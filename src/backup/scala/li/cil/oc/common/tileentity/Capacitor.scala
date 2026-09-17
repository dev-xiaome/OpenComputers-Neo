package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 电容器（三级电容组，对应 1.7.10 的 `common.tileentity.Capacitor`）。
 *
 * 本体只是一个带连接器节点的能量缓冲：缓冲大小 = 基础容量 + 相邻电容的毗邻奖励 +
 * 「隔一格」的二级邻居奖励的一半。相邻电容增减时会互相触发 [[recomputeCapacity]]。
 *
 * 纹理：下/上 = CapacitorTop，其它四面 = CapacitorSide（分级方块，1.21.1 每个等级一个方块）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`；
 *    `side.offsetX/Y/Z` → `worldPosition.relative(side)` / `relative(side, 2)`。
 *  - `world.blockExists(x, y, z)` + `world.getTileEntity(x, y, z)` → `world.getBlockEntity(pos)`
 *    （`Level#getBlockEntity` 内部已做「区块未加载返回 null」的判断）。
 *  - `WrapAsJava` → Scala 2.13 的 `scala.jdk.CollectionConverters._`（这里用显式 `.asJava`）。
 *  - 原实现混入的 IC2 等模组能量接口已删除（见 `traits.PowerAcceptor`）。
 */
class Capacitor(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with DeviceInfo {

  // 先按最大理论容量创建节点，校验（入网）后再收缩，避免读档时丢失能量。
  val node = api.Network.newNode(this, Visibility.Network).
    withConnector(maxCapacity).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Battery",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "CapBank3x",
    DeviceAttribute.Capacity -> maxCapacity.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = false

  override def dispose(): Unit = {
    super.dispose()
    if (isServer && world != null) {
      // 自己消失后二级邻居的奖励要重算（一级邻居会在自己的 onDisconnect 里重算）。
      indirectNeighbors.map(neighborPos => world.getBlockEntity(neighborPos)).collect {
        case capacitor: Capacitor => capacitor.recomputeCapacity()
      }
    }
  }

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      recomputeCapacity(updateSecondGradeNeighbors = true)
    }
  }

  // ----------------------------------------------------------------------- //

  def recomputeCapacity(updateSecondGradeNeighbors: Boolean = false): Unit = {
    node.setLocalBufferSize(
      Settings.get.bufferCapacitor +
        Settings.get.bufferCapacitorAdjacencyBonus * Direction.values().count(side => {
          // `getBlockEntity` 对未加载的区块返回 null，`case _` 一并覆盖 null。
          world.getBlockEntity(worldPosition.relative(side)) match {
            case _: Capacitor => true
            case _ => false
          }
        }) +
        Settings.get.bufferCapacitorAdjacencyBonus / 2 * indirectNeighbors.count(neighborPos => {
          world.getBlockEntity(neighborPos) match {
            case capacitor: Capacitor =>
              if (updateSecondGradeNeighbors) {
                capacitor.recomputeCapacity()
              }
              true
            case _ => false
          }
        }))
  }

  /** 隔一格的六个位置（原 `ForgeDirection.VALID_DIRECTIONS.map(side => (x + side.offsetX * 2, ...))`）。 */
  private def indirectNeighbors: Array[BlockPos] =
    Direction.values().map(side => worldPosition.relative(side, 2))

  protected def maxCapacity: Double = Settings.get.bufferCapacitor + Settings.get.bufferCapacitorAdjacencyBonus * 9
}
