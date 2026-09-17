package li.cil.oc.common.tileentity.traits.power

import li.cil.oc.Settings
import li.cil.oc.api.network.Connector
import li.cil.oc.common.tileentity.traits.TileEntity
import net.minecraft.core.Direction

/**
 * 外部能量系统互通的公共部分（对应 1.7.10 的 `traits.power.Common`）。
 *
 * 本 trait 只依赖 `li.cil.oc.api.network.Connector` 与 [[li.cil.oc.Settings]]，
 * **不依赖任何外部模组**，因此可以正常移植：
 *  - 每个面通过 [[connector]] 暴露一个 OC 网络连接器（由具体方块实现，
 *    例如 `Case#connector` 直接取机器节点）；
 *  - [[tryChangeBuffer]] / [[globalBuffer]] / [[globalBufferSize]] / [[globalDemand]]
 *    把外部能量单位换算后的能量注入 OC 网络缓冲；
 *  - [[tryAllSides]] 是各模组集成 trait 的公共驱动循环。
 *
 * ==1.21.1 迁移要点==
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`；
 *    `ForgeDirection.UNKNOWN` 不存在，`canConnectPower` 只做空值判断。
 *  - 删除 `@SideOnly(Side.CLIENT)`（NeoForge 会因此抛异常）；
 *    [[hasConnector]] 仍然只应在客户端调用（客户端没有 [[Connector]] 实例）。
 */
trait Common extends TileEntity {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  /**
   * 客户端侧判断某一面是否"看起来"接了能量线缆，用于渲染。
   *
   * 1.7.10 的 `@SideOnly(Side.CLIENT)` 已删除；实现方只需保证该方法只在客户端被调用。
   */
  protected def hasConnector(side: Direction) = false

  protected def connector(side: Direction): Option[Connector] = None

  // ----------------------------------------------------------------------- //

  /** 每秒可注入的能量上限（原 `energyThroughput`）。 */
  def energyThroughput: Double

  /**
   * 逐面尝试把外部能量搬进 OC 网络缓冲。
   *
   * 注意：本方法只在每 `Settings.get.tickFrequency` 刻被调用一次，
   * 但 `energyThroughput` 是"每 tick"的值，所以预算要乘上去。
   */
  protected def tryAllSides(provider: (Double, Direction) => Double, fromOther: Double => Double, toOther: Double => Double): Unit = {
    var budget = energyThroughput * Settings.get.tickFrequency
    for (side <- Direction.values()) {
      val demand = toOther(math.min(budget, globalDemand(side)))
      if (demand > 1) {
        val energy = fromOther(provider(demand, side))
        if (energy > 0) {
          budget -= tryChangeBuffer(side, energy)
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //

  def canConnectPower(side: Direction) =
    !Settings.get.ignorePower && side != null &&
      (if (isClient) hasConnector(side) else connector(side).isDefined)

  /**
   * Tries to inject the specified amount of energy into the buffer via the specified side.
   *
   * @param side      the side to change the buffer through.
   * @param amount    the amount to change the buffer by.
   * @param doReceive whether to actually inject energy or only simulate it.
   * @return the amount of energy that was actually injected.
   */
  def tryChangeBuffer(side: Direction, amount: Double, doReceive: Boolean = true): Double =
    if (isClient || Settings.get.ignorePower) 0
    else connector(side) match {
      case Some(node) =>
        val cappedAmount = math.max(0, math.min(math.min(energyThroughput, amount), globalDemand(side)))
        if (doReceive) cappedAmount - node.changeBuffer(cappedAmount)
        else cappedAmount
      case _ => 0
    }

  def globalBuffer(side: Direction): Double =
    if (isClient) 0
    else connector(side) match {
      case Some(node) => node.globalBuffer
      case _ => 0
    }

  def globalBufferSize(side: Direction): Double =
    if (isClient) 0
    else connector(side) match {
      case Some(node) => node.globalBufferSize
      case _ => 0
    }

  def globalDemand(side: Direction) = math.max(0, math.min(energyThroughput, globalBufferSize(side) - globalBuffer(side)))
}
