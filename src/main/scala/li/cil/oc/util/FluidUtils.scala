package li.cil.oc.util

import li.cil.oc.util.ExtendedBlock._
import li.cil.oc.util.ExtendedLevel._
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.neoforged.neoforge.fluids.{FluidStack, FluidType}
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.block.LiquidBlock
import net.neoforged.neoforge.capabilities.Capabilities

object FluidUtils {
  /**
   * Retrieves an actual fluid handler implementation for a specified world coordinate.
   * <br>
   * This performs special handling for in-world liquids.
   */
  def fluidHandlerAt(position: BlockPosition, side: Direction): Option[IFluidHandler] = position.world match {
    case Some(world) if world.blockExists(position) => world.getBlockEntity(position) match {
      case handler: IFluidHandler => Option(handler)
      case _: BlockEntity =>
        Option(world.getCapability(Capabilities.FluidHandler.BLOCK, position.toBlockPos, side)) match {
          case Some(handler) => Option(handler)
          case _ => Option(new GenericBlockWrapper(position))
        }
      case _ => Option(new GenericBlockWrapper(position))
    }
    case _ => None
  }

  def fluidHandlerOf(stack: ItemStack): IFluidHandlerItem = Option(stack) match {
    case Some(itemStack) => itemStack.getCapability(Capabilities.FluidHandler.ITEM)
    case _ => null
  }

  /**
   * Transfers some fluid between two fluid handlers.
   * <br>
   * This will try to extract up the specified amount of fluid from any handler,
   * then insert it into the specified sink handler. If the insertion fails, the
   * fluid will remain in the source handler.
   * <br>
   * This returns {@code true} if some fluid was transferred.
   */
  def transferBetweenFluidHandlers(source: IFluidHandler, sink: IFluidHandler, limit: Int = FluidType.BUCKET_VOLUME, sourceTank: Int = -1): Int = {
    var stackToDrain: FluidStack = null
    if (sourceTank >= 0 && sourceTank < source.getTanks) {
      stackToDrain = source.getFluidInTank(sourceTank)
      if (stackToDrain != null && !stackToDrain.isEmpty) {
        stackToDrain = stackToDrain.copy()
        stackToDrain.setAmount(math.min(stackToDrain.getAmount, limit))
      }
    }

    val drained = Option(stackToDrain) match {
      case Some(fluidStack) => source.drain(fluidStack, FluidAction.SIMULATE)
      case _ => source.drain(limit, FluidAction.SIMULATE)
    }
    if (drained == null || drained.isEmpty) {
      return 0
    }
    val filledAmount = sink.fill(drained, FluidAction.SIMULATE)
    if (stackToDrain != null) {
      val filledStack = drained.copy()
      filledStack.setAmount(filledAmount)
      sink.fill(source.drain(filledStack, FluidAction.EXECUTE), FluidAction.EXECUTE)
    } else {
      sink.fill(source.drain(filledAmount, FluidAction.EXECUTE), FluidAction.EXECUTE)
    }
  }

  /**
   * Utility method for calling {@link #transferBetweenFluidHandlers} on handlers
   * in the world.
   * <br>
   * This uses the {@link #fluidHandlerAt} method, and therefore handles special
   * cases such as fluid blocks.
   */
  def transferBetweenFluidHandlersAt(sourcePos: BlockPosition, sourceSide: Direction, sinkPos: BlockPosition, sinkSide: Direction, limit: Int = FluidType.BUCKET_VOLUME, sourceTank: Int = -1): Int =
    fluidHandlerAt(sourcePos, sourceSide).fold(0)(source =>
      fluidHandlerAt(sinkPos, sinkSide).fold(0)(sink =>
        transferBetweenFluidHandlers(source, sink, limit, sourceTank)))

  /**
   * Lookup fluid taking into account flowing liquid blocks...
   * For legacy reasons, returns null when the block is not a fluid, not Fluids.EMPTY.
   */
  @Deprecated
  def lookupFluidForBlock(block: Block): Fluid = block match {
    case fluid: LiquidBlock => fluid.fluid
    case _ => null
  }

  // ----------------------------------------------------------------------- //

  private class GenericBlockWrapper(position: BlockPosition) extends IFluidHandler {
    override def getTanks = currentWrapper.fold(0)(_.getTanks)

    override def getFluidInTank(tank: Int) = currentWrapper.fold(FluidStack.EMPTY)(_.getFluidInTank(tank))

    override def getTankCapacity(tank: Int) = currentWrapper.fold(0)(_.getTankCapacity(tank))

    override def isFluidValid(tank: Int, fluid: FluidStack): Boolean = currentWrapper.fold(false)(_.isFluidValid(tank, fluid))

    override def drain(resource: FluidStack, action: FluidAction): FluidStack = currentWrapper.fold(null: FluidStack)(_.drain(resource, action))

    override def drain(maxDrain: Int, action: FluidAction): FluidStack = currentWrapper.fold(null: FluidStack)(_.drain(maxDrain, action))

    override def fill(resource: FluidStack, action: FluidAction): Int = currentWrapper.fold(0)(_.fill(resource, action))

    def currentWrapper: Option[IFluidHandler] = if (position.world.get.blockExists(position)) position.world.get.getBlock(position) match {
      case block: LiquidBlock if lookupFluidForBlock(block) != null && isFullLiquidBlock => Option(new LiquidBlockWrapper(position, block))
      case block: Block if block.isAir(position) || block.isReplaceable(position) => Option(new AirBlockWrapper(position, block))
      case _ => None
    }
    else None

    def isFullLiquidBlock: Boolean = {
      val state = position.world.get.getBlockState(position.toBlockPos)
      state.getValue(LiquidBlock.LEVEL) == 0
    }
  }

  private trait BlockWrapperBase extends IFluidHandler {
    override def getTanks = 1

    override def getTankCapacity(tank: Int) = FluidType.BUCKET_VOLUME

    protected def uncheckedDrain(action: FluidAction): FluidStack

    override def drain(resource: FluidStack, action: FluidAction): FluidStack = {
      val drained = uncheckedDrain(FluidAction.SIMULATE)
      if (drained != null && (resource == null || (drained.getFluid == resource.getFluid && drained.getAmount <= resource.getAmount))) {
        uncheckedDrain(action)
      }
      else null
    }

    override def drain(maxDrain: Int, action: FluidAction): FluidStack = {
      val drained = uncheckedDrain(FluidAction.SIMULATE)
      if (drained != null && drained.getAmount <= maxDrain) {
        uncheckedDrain(action)
      }
      else null
    }

    override def fill(resource: FluidStack, action: FluidAction): Int = 0
  }

  private class LiquidBlockWrapper(val position: BlockPosition, val block: LiquidBlock) extends BlockWrapperBase {
    val fluid: Fluid = lookupFluidForBlock(block)

    override def getFluidInTank(tank: Int) = if (isFullLiquidBlock) new FluidStack(fluid, FluidType.BUCKET_VOLUME) else FluidStack.EMPTY

    override def isFluidValid(tank: Int, fluid: FluidStack): Boolean = block.fluid.isSame(fluid.getFluid)

    override protected def uncheckedDrain(action: FluidAction): FluidStack = {
      if (action.execute) {
        position.world.get.setBlockToAir(position)
      }
      if (isFullLiquidBlock) new FluidStack(fluid, FluidType.BUCKET_VOLUME) else FluidStack.EMPTY
    }

    def isFullLiquidBlock: Boolean = {
      val state = position.world.get.getBlockState(position.toBlockPos)
      state.getValue(LiquidBlock.LEVEL) == 0
    }
  }

  private class AirBlockWrapper(val position: BlockPosition, val block: Block) extends IFluidHandler {
    override def getTanks = 1

    override def getTankCapacity(tank: Int) = FluidType.BUCKET_VOLUME

    override def getFluidInTank(tank: Int) = FluidStack.EMPTY

    override def drain(resource: FluidStack, action: FluidAction): FluidStack = FluidStack.EMPTY

    override def drain(maxDrain: Int, action: FluidAction): FluidStack = FluidStack.EMPTY

    override def isFluidValid(tank: Int, fluid: FluidStack): Boolean = fluid.getFluid.defaultFluidState.createLegacyBlock != null

    override def fill(resource: FluidStack, action: FluidAction): Int = {
      if (resource != null && resource.getFluid.defaultFluidState.createLegacyBlock != null && resource.getAmount >= FluidType.BUCKET_VOLUME) {
        if (action.execute) {
          val world = position.world.get
          if (!world.isAirBlock(position) && !world.containsAnyLiquid(position.bounds))
            world.breakBlock(position)
          world.setBlockAndUpdate(position.toBlockPos, resource.getFluid.defaultFluidState.createLegacyBlock)
          // This fake neighbor update is required to get stills to start flowing.
          world.notifyBlockOfNeighborChange(position, world.getBlock(position))
        }
        FluidType.BUCKET_VOLUME
      }
      else 0
    }
  }

}
