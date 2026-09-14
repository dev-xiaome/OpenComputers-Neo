package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.Visibility
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable

/**
 * 航点（对应 1.7.10 的 `common.tileentity.Waypoint`）。
 *
 * 带标签的锚点：导航升级用它定位目标。朝向由 [[traits.Rotatable]] 提供，
 * 红石输入由 [[traits.RedstoneAware]] 提供（导航升级据此判断航点是否「通电」）。
 *
 * 纹理：下/上 = WaypointTop，其它四面 = WaypointSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `ForgeDirection` → `Direction`（`ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`，
 *    `side.offsetX/Y/Z` → `side.getStepX/Y/Z`）。
 *  - `world.rand` → `world.random`；`world.spawnParticle("portal", ...)` →
 *    `world.addParticle(ParticleTypes.PORTAL, ...)`。
 *  - `position.toVec3.addVector(x, y, z)` → `position.toVec3.add(x, y, z)`（`Vec3` 的 `xCoord` 等改为 `x` 等）。
 *  - `updateEntity()` → [[traits.TileEntity.tick]]（覆写时先调 `super.tick()`）。
 *  - 删除 `@SideOnly(Side.CLIENT)`，改为 `isClient` 判断。
 *
 * ==降级说明==
 * TODO(common.EventHandler / server.network.Waypoints): 原实现用
 * `EventHandler.scheduleServer(() => Waypoints.add(this))` 延迟一 tick 把航点登记到
 * `li.cil.oc.server.network.Waypoints`。两者都未移植，这里改为在 [[Waypoint.initialize]] 里
 * 直接登记到本文件内的本地登记表（见 [[Waypoint.localWaypoints]]）。
 */
class Waypoint(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.Rotatable with traits.RedstoneAware {

  val node = api.Network.newNode(this, Visibility.Network).
    withComponent("waypoint").
    create()

  var label = ""

  override def validFacings: Array[Direction] = Direction.values()

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function(): string -- Get the current label of this waypoint.""")
  def getLabel(context: Context, args: Arguments): Array[Object] = result(label)

  @Callback(doc = """function(value:string) -- Set the label for this waypoint.""")
  def setLabel(context: Context, args: Arguments): Array[Object] = {
    label = args.checkString(0).take(32)
    context.pause(0.5)
    null
  }

  // ----------------------------------------------------------------------- //

  override def tick(): Unit = {
    super.tick()
    if (isClient) {
      val origin = position.toVec3.add(facing.getStepX * 0.5, facing.getStepY * 0.5, facing.getStepZ * 0.5)
      val dx = (world.random.nextFloat() - 0.5f) * 0.8f
      val dy = (world.random.nextFloat() - 0.5f) * 0.8f
      val dz = (world.random.nextFloat() - 0.5f) * 0.8f
      val vx = (world.random.nextFloat() - 0.5f) * 0.2f + facing.getStepX * 0.3f
      val vy = (world.random.nextFloat() - 0.5f) * 0.2f + facing.getStepY * 0.3f - 0.5f
      val vz = (world.random.nextFloat() - 0.5f) * 0.2f + facing.getStepZ * 0.3f
      world.addParticle(ParticleTypes.PORTAL, origin.x + dx, origin.y + dy, origin.z + dz, vx, vy, vz)
    }
  }

  override protected def initialize(): Unit = {
    super.initialize()
    if (isServer) {
      Waypoint.add(this)
    }
  }

  override def dispose(): Unit = {
    super.dispose()
    // `dispose()` 可能被调用两次（区块卸载 + 移除），Set 的移除是幂等的。
    Waypoint.remove(this)
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    label = nbt.getString(Settings.namespace + "label")
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putString(Settings.namespace + "label", label)
  }

  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    label = nbt.getString(Settings.namespace + "label")
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putString(Settings.namespace + "label", label)
  }
}

object Waypoint {

  /**
   * 本地方块实体登记表（占位）。
   *
   * TODO(server.network.Waypoints): 原实现由 `li.cil.oc.server.network.Waypoints` 维护
   * 「标签 → 航点」的映射，供导航升级查询最近 / 指定标签的航点。服务端网络层尚未移植，
   * 这里先在方块实体侧维护一个进程内集合：进入世界时登记、离开世界时注销，
   * 按标签查找的对外入口留待 `server.network.Waypoints` 移植后补上。
   */
  private val localWaypoints = mutable.Set.empty[Waypoint]

  private[tileentity] def add(waypoint: Waypoint): Unit = localWaypoints += waypoint

  private[tileentity] def remove(waypoint: Waypoint): Unit = localWaypoints -= waypoint
}
