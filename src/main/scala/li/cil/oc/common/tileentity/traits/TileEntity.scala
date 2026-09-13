package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SideTracker
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

/**
 * 方块实体基 trait（对应 1.7.10 的 `li.cil.oc.common.tileentity.traits.TileEntity`）。
 *
 * ==1.21.1 结构说明==
 * 1.7.10 里 `trait TileEntity extends net.minecraft.tileentity.TileEntity`——trait 直接继承
 * 一个无参构造的类。1.21.1 的 `BlockEntity` **必须**通过
 * `BlockEntity(BlockEntityType, BlockPos, BlockState)` 构造，Scala trait 无法带构造参数
 * 传给父类，因此这里拆成两层：
 *
 *  - 本 trait 只提供「原 TileEntity 的 API 表面 + 可覆写钩子」，并声明自身类型
 *    `self: BlockEntity`（`this` 因此可以直接当作 `BlockEntity` 传给 Java API）；
 *  - [[li.cil.oc.common.tileentity.BlockEntityBase]] 是真正的 `BlockEntity` 子类，
 *    负责把 1.21.1 的生命周期回调转发到本 trait 的钩子。
 *
 * 具体方块实体的写法（构造函数只接收位置与状态，类型名从注册层查）：
 * {{{
 *   class Adapter(pos: BlockPos, state: BlockState)
 *     extends BlockEntityBase(Registry.getBlockEntityType(Constants.BlockName.Adapter), pos, state)
 *       with traits.Environment with traits.ComponentInventory ...
 * }}}
 *
 * ==生命周期 / API 映射==
 * {{{
 *  1.7.10                     1.21.1
 *  validate()                 IBlockEntityExtension#onLoad()   → initialize()
 *  invalidate()               BlockEntity#setRemoved()        → dispose()
 *  onChunkUnload()            IBlockEntityExtension#onChunkUnloaded() → dispose()
 *  updateEntity()             BlockEntityTicker（调用 tick()）
 *  worldObj / xCoord / ...    level / worldPosition           → world / x / y / z
 *  markDirty()                setChanged()
 *  getBlockMetadata()         已移除，改用 BlockState 属性（blockMetadata 恒返回 0）
 * }}}
 */
trait TileEntity { self: BlockEntity =>

  // ----------------------------------------------------------------------- //
  // 位置与世界
  // ----------------------------------------------------------------------- //

  /** 所在世界；方块实体尚未加入世界时为 `null`。 */
  def world: Level = getLevel

  def x: Int = worldPosition.getX

  def y: Int = worldPosition.getY

  def z: Int = worldPosition.getZ

  /** 当前位置（带世界引用），等价于原 `BlockPosition(x, y, z, world)`。 */
  def position: BlockPosition = BlockPosition(x, y, z, world)

  /** 当前 BlockPos。 */
  def blockPos: BlockPos = worldPosition

  /** 自身方块（原 `getBlockType`）。 */
  def block: Block = getBlockState.getBlock

  /** 当前方块状态（原 metadata 的替代）。 */
  def blockState: BlockState = getBlockState

  /**
   * TODO(标签): 1.7.10 的 `getBlockMetadata()` 在 1.21.1 已被 `BlockState` 属性取代。
   * 这里固定返回 `0`，新代码请直接读取 [[blockState]] 的属性。
   */
  def blockMetadata: Int = 0

  def isClient: Boolean = world != null && world.isClientSide

  def isServer: Boolean = if (world != null) !world.isClientSide else SideTracker.isServer

  /**
   * 是否参与 tick（原 `TileEntity#canUpdate`）。
   *
   * 1.21.1 里 tick 由 `EntityBlock#getTicker` 决定，本标志只用于
   * [[li.cil.oc.common.tileentity.traits.Environment#markChanged]] 的降级分支。
   */
  def canUpdate: Boolean = true

  /** 与世界中心的距离平方（原 `getDistanceFrom`）。 */
  def distanceSq(other: BlockEntity): Double =
    if (other == null) Double.MaxValue else worldPosition.distSqr(other.getBlockPos)

  // ----------------------------------------------------------------------- //
  // 生命周期钩子
  // ----------------------------------------------------------------------- //

  /**
   * 追加 tick 逻辑（原 `updateEntity()`）。
   *
   * 由方块侧的 `EntityBlock#getTicker` 每个游戏刻调用一次；子类覆写时**必须**调用
   * `super.tick()`。
   */
  def tick(): Unit = {
    // 原逻辑：定期强制光照更新，避免大机器阵列里客户端光照不刷新。
    if (Settings.get.periodicallyForceLightUpdate && world != null && world.getGameTime % 40 == 0 &&
      getBlockState.getLightEmission(world, worldPosition) > 0) {
      world.sendBlockUpdated(worldPosition, getBlockState, getBlockState, Block.UPDATE_CLIENTS)
    }
  }

  /** 客户端 tick（服务端不调用）。默认什么都不做。 */
  def tickClient(): Unit = {}

  /** 等价于原 `validate()`：方块实体加入世界时调用一次。 */
  protected def initialize(): Unit = {}

  /** 等价于原 `invalidate()` / `onChunkUnload()`：方块实体离开世界时调用（可能被调用两次，实现需幂等）。 */
  def dispose(): Unit = {}

  // ----------------------------------------------------------------------- //
  // NBT
  // ----------------------------------------------------------------------- //

  /** 服务端侧读档（原 `readFromNBT` 的服务端分支）。 */
  protected def readFromNBTForServer(nbt: CompoundTag): Unit = {}

  /** 服务端侧存档。 */
  protected def writeToNBTForServer(nbt: CompoundTag): Unit = {}

  /** 客户端侧读取同步数据（原 `readFromNBTForClient`，服务端不会调用）。 */
  protected def readFromNBTForClient(nbt: CompoundTag): Unit = {}

  /** 客户端侧写出同步数据（用于 `getUpdateTag`）。 */
  protected def writeToNBTForClient(nbt: CompoundTag): Unit = {}

  // ----------------------------------------------------------------------- //
  // 便捷方法
  // ----------------------------------------------------------------------- //

  /** 标记需要存盘（原 `markDirty()`）。 */
  def markDirty(): Unit = setChanged()

  /** 标记需要存盘并同步给客户端。 */
  def markDirtyAndUpdate(): Unit = {
    setChanged()
    if (world != null && !world.isClientSide) {
      world.sendBlockUpdated(worldPosition, getBlockState, getBlockState, Block.UPDATE_CLIENTS)
    }
  }

  /** 通知周围方块（原 `world.notifyBlocksOfNeighborChange(x, y, z, block)`）。 */
  protected def notifyNeighbors(): Unit =
    if (world != null) world.updateNeighborsAt(worldPosition, block)

  /** 通知客户端方块更新（原 `world.markBlockForUpdate(x, y, z)`）。 */
  protected def markBlockForUpdate(): Unit =
    if (world != null) world.sendBlockUpdated(worldPosition, getBlockState, getBlockState, Block.UPDATE_CLIENTS)

  /** 类型别名，方便子类覆写时引用（原代码里 `BlockEntityType` 由注册层保管）。 */
  protected def blockEntityType: BlockEntityType[_] = getType
}
