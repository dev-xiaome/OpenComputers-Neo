package li.cil.oc.util

import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

/**
 * 流体（`IFluidHandler`）相关工具。
 *
 * 1.21.1 迁移要点：
 *  - `net.minecraftforge.fluids.*` → `net.neoforged.neoforge.fluids.*`
 *  - `FluidRegistry` 已移除，改用 `FluidState` / `FluidType` / 注册表
 *  - `FluidContainerRegistry.BUCKET_VOLUME` → `FluidType.BUCKET_VOLUME`
 *  - `IFluidBlock` 已移除，改为 `LiquidBlock` + `BucketPickup`
 *  - `IFluidHandler#getTankInfo` / `FluidTankInfo` 已移除，
 *    改为 `getTanks()` / `getFluidInTank(i)` / `getTankCapacity(i)`；
 *    这里用 [[TankInfo]] 承载等价信息
 *  - 所有 `side` 参数仅用于兼容旧签名：新 API 由能力的 `Direction` 上下文决定面
 */
object FluidUtils {
  /** 桶容量（1000 mB）。 */
  final val BucketVolume: Int = FluidType.BUCKET_VOLUME

  /** 单槽流体信息（等价于已移除的 `FluidTankInfo`）。 */
  final case class TankInfo(fluid: FluidStack, capacity: Int)

  /**
   * Retrieves an actual fluid handler implementation for a specified world coordinate.
   * <br>
   * This performs special handling for in-world liquids.
   */
  def fluidHandlerAt(position: BlockPosition): Option[IFluidHandler] = position.world match {
    case Some(world) if world.isLoaded(position.toChunkCoordinates) =>
      val pos = position.toChunkCoordinates
      val blockHandler = Option(blockFluidHandler(world, pos))
      blockHandler match {
        case Some(handler) => Option(handler)
        case _ => Option(new GenericBlockWrapper(position))
      }
    case _ => None
  }

  /**
   * 通过 NeoForge 方块能力查询流体处理器。
   * <br>
   * 1.21.1 的 `BlockEntity` 已没有 `getCapability`，必须使用
   * `Capabilities.FluidHandler.BLOCK.getCapability(level, pos, state, blockEntity, side)`；
   * `side` 为 `null` 表示不限定面。无处理器时返回 `null`。
   */
  private def blockFluidHandler(world: Level, pos: BlockPos): IFluidHandler = {
    val state = world.getBlockState(pos)
    val blockEntity = world.getBlockEntity(pos)
    Capabilities.FluidHandler.BLOCK.getCapability(world, pos, state, blockEntity, null)
  }

  /** 查询某个槽位的流体信息。 */
  def tankInfo(handler: IFluidHandler, tank: Int): Option[TankInfo] = {
    if (handler == null || tank < 0 || tank >= handler.getTanks) return None
    Some(TankInfo(handler.getFluidInTank(tank), handler.getTankCapacity(tank)))
  }

  /**
   * Transfers some fluid between two fluid handlers.
   * <br>
   * This will try to extract up the specified amount of fluid from any handler,
   * then insert it into the specified sink handler. If the insertion fails, the
   * fluid will remain in the source handler.
   * <br>
   * This returns the amount of fluid transferred.
   */
  def transferBetweenFluidHandlers(source: IFluidHandler, sourceSide: Direction, sink: IFluidHandler, sinkSide: Direction, limit: Int = BucketVolume, sourceTank: Int = -1): Int = {
    if (source == null || sink == null || limit <= 0) return 0

    val tank = if (sourceTank < 0) 0 else sourceTank
    val template = tankInfo(source, tank) match {
      case Some(info) if !info.fluid.isEmpty => info.fluid.copy()
      case _ => null
    }

    val drained = if (template == null) source.drain(limit, FluidAction.SIMULATE)
    else source.drain(new FluidStack(template.getFluid, limit), FluidAction.SIMULATE)
    if (drained.isEmpty) return 0

    val accepted = sink.fill(drained, FluidAction.SIMULATE)
    if (accepted <= 0) return 0

    if (template == null) source.drain(accepted, FluidAction.EXECUTE)
    else source.drain(new FluidStack(template.getFluid, accepted), FluidAction.EXECUTE)
    sink.fill(new FluidStack(drained.getFluid, accepted), FluidAction.EXECUTE)
    accepted
  }

  /**
   * Utility method for calling <tt>transferBetweenFluidHandlers</tt> on handlers
   * in the world.
   * <br>
   * This uses the <tt>fluidHandlerAt</tt> method, and therefore handles special
   * cases such as fluid blocks.
   */
  def transferBetweenFluidHandlersAt(sourcePos: BlockPosition, sourceSide: Direction, sinkPos: BlockPosition, sinkSide: Direction, limit: Int = BucketVolume, sourceTank: Int = -1): Int =
    fluidHandlerAt(sourcePos).fold(0)(source =>
      fluidHandlerAt(sinkPos).fold(0)(sink =>
        transferBetweenFluidHandlers(source, sourceSide, sink, sinkSide, limit, sourceTank)))

  /**
   * Lookup fluid taking into account flowing liquid blocks...
   * <br>
   * TODO(标签): 1.21.1 没有 `FluidRegistry.lookupFluidForBlock`，且流体信息只能通过
   * `FluidState` 获得（需要世界与坐标）。此处保留旧签名，固定返回 `null`；
   * 请改用 [[lookupFluidStateForBlock]]。
   */
  def lookupFluidForBlock(block: Block): Fluid = null

  /** 方块对应流体的 `FluidState`，无流体时返回 `null`。 */
  def lookupFluidStateForBlock(world: Level, position: BlockPosition): FluidState = {
    val state = world.getBlockState(position.toChunkCoordinates)
    val fluidState = state.getFluidState
    if (fluidState == null || fluidState.isEmpty) null else fluidState
  }

  // ----------------------------------------------------------------------- //

  private class GenericBlockWrapper(position: BlockPosition) extends IFluidHandler {
    override def getTanks: Int = 1

    override def getFluidInTank(tank: Int): FluidStack = currentWrapper.fold(FluidStack.EMPTY)(_.getFluidInTank(tank))

    override def getTankCapacity(tank: Int): Int = currentWrapper.fold(0)(_.getTankCapacity(tank))

    override def isFluidValid(tank: Int, stack: FluidStack): Boolean =
      currentWrapper.fold(false)(_.isFluidValid(tank, stack))

    override def drain(resource: FluidStack, action: FluidAction): FluidStack =
      currentWrapper.fold(FluidStack.EMPTY)(_.drain(resource, action))

    override def drain(maxDrain: Int, action: FluidAction): FluidStack =
      currentWrapper.fold(FluidStack.EMPTY)(_.drain(maxDrain, action))

    override def fill(resource: FluidStack, action: FluidAction): Int =
      currentWrapper.fold(0)(_.fill(resource, action))

    def currentWrapper: Option[IFluidHandler] = position.world match {
      case Some(world) if world.isLoaded(position.toChunkCoordinates) =>
        val pos = position.toChunkCoordinates
        val state = world.getBlockState(pos)
        // 先看方块实体能力，再回退到内建液体 / 空气方块包装。
        Option(blockFluidHandler(world, pos)) match {
          case Some(handler) => Option(handler)
          case _ => blockWrapper(state)
        }
      case _ => None
    }

    private def blockWrapper(state: BlockState): Option[IFluidHandler] = state.getBlock match {
      case _: BucketPickup if isFullLiquidBlock(state) =>
        // 1.21.1 的可抽取液体方块统一实现了 `BucketPickup`（`LiquidBlock` 即是）。
        Option(new LiquidBlockWrapper(position, state.getBlock.asInstanceOf[BucketPickup]))
      case block if state.isAir || state.canBeReplaced =>
        Option(new AirBlockWrapper(position, block))
      case _ => None
    }

    /** 旧版 `getBlockMetadata == 0` 的等价判断：是否为液体源方块。 */
    private def isFullLiquidBlock(state: BlockState): Boolean = {
      val fluidState = state.getFluidState
      fluidState != null && !fluidState.isEmpty && fluidState.isSource
    }
  }

  private trait BlockWrapperBase extends IFluidHandler {
    protected def uncheckedDrain(doDrain: Boolean): FluidStack

    override def getTanks: Int = 1

    override def isFluidValid(tank: Int, stack: FluidStack): Boolean = false

    override def fill(resource: FluidStack, action: FluidAction): Int = 0

    override def drain(resource: FluidStack, action: FluidAction): FluidStack = {
      // 旧签名允许 `resource` 为 null，这里归一化为具体数量。
      val requested = if (resource == null || resource.isEmpty) BucketVolume else resource.getAmount
      drainAmount(requested, action, resource)
    }

    override def drain(maxDrain: Int, action: FluidAction): FluidStack =
      drainAmount(maxDrain, action, null)

    private def drainAmount(maxDrain: Int, action: FluidAction, filter: FluidStack): FluidStack = {
      val drained = uncheckedDrain(false)
      if (drained == null || drained.isEmpty || drained.getAmount > maxDrain) FluidStack.EMPTY
      else if (filter != null && !filter.isEmpty && (drained.getFluid != filter.getFluid)) FluidStack.EMPTY
      else if (action.execute()) {
        val result = uncheckedDrain(true)
        if (result == null) FluidStack.EMPTY else result
      }
      else drained
    }
  }

  /** 液体方块（`LiquidBlock` / `BucketPickup`）的流体处理器包装。 */
  private class LiquidBlockWrapper(val position: BlockPosition, val block: BucketPickup) extends BlockWrapperBase {
    private val AssumedCapacity = BucketVolume

    override def getFluidInTank(tank: Int): FluidStack = {
      val fluid = fluidState
      if (fluid == null) FluidStack.EMPTY else new FluidStack(fluid, AssumedCapacity)
    }

    override def getTankCapacity(tank: Int): Int = AssumedCapacity

    private def fluidState: Fluid = position.world match {
      case Some(world) =>
        val state = world.getBlockState(position.toChunkCoordinates)
        val fluidState = state.getFluidState
        if (fluidState == null || fluidState.isEmpty) null else fluidState.getType
      case _ => null
    }

    override protected def uncheckedDrain(doDrain: Boolean): FluidStack = {
      val fluid = fluidState
      if (fluid == null) return FluidStack.EMPTY
      position.world match {
        case Some(world) =>
          val pos = position.toChunkCoordinates
          if (doDrain) {
            // 1.21.1 用 `BucketPickup#pickupBlock` 取出液体（等价于旧版的 setBlockToAir + getItem）。
            block.pickupBlock(null, world, pos, world.getBlockState(pos))
          }
          new FluidStack(fluid, AssumedCapacity)
        case _ => FluidStack.EMPTY
      }
    }
  }

  /** 空气 / 可替换方块的流体处理器包装：只支持注入。 */
  private class AirBlockWrapper(val position: BlockPosition, val block: Block) extends IFluidHandler {
    override def getTanks: Int = 1

    override def getFluidInTank(tank: Int): FluidStack = FluidStack.EMPTY

    override def getTankCapacity(tank: Int): Int = BucketVolume

    override def isFluidValid(tank: Int, stack: FluidStack): Boolean = canPlace(stack)

    override def drain(resource: FluidStack, action: FluidAction): FluidStack = FluidStack.EMPTY

    override def drain(maxDrain: Int, action: FluidAction): FluidStack = FluidStack.EMPTY

    override def fill(resource: FluidStack, action: FluidAction): Int = {
      if (!canPlace(resource)) return 0
      position.world match {
        case Some(world) =>
          if (action.execute()) {
            val pos = position.toChunkCoordinates
            val fluidType = resource.getFluid.getFluidType
            val state = fluidType.getBlockForFluidState(world, pos, resource.getFluid.defaultFluidState())
            if (state != null && !state.isAir) {
              world.setBlock(pos, state, Block.UPDATE_ALL)
              // 这个“假”的邻居更新是让静止液体开始流动所必需的。
              world.updateNeighborsAt(pos, state.getBlock)
            }
          }
          BucketVolume
        case _ => 0
      }
    }

    private def canPlace(resource: FluidStack): Boolean =
      resource != null && !resource.isEmpty && resource.getAmount >= BucketVolume
  }
}
