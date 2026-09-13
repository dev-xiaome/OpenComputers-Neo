package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.network
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.nbt.CompoundTag

/**
 * 抽象总线（StargateTech2 的 Abstract Bus）方块实体 trait
 * （对应 1.7.10 的 `traits.AbstractBusAware`）。
 *
 * ==降级说明（本文件为占位实现）==
 * 1.7.10 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现
 * `lordfokas.stargatetech2.api.bus.IBusDevice`，并把 `installedComponents` 里的
 * `AbstractBusCard` 暴露为 `IBusInterface`；开关总线状态时还要调用
 * `StargateTech2.addDevice/removeDevice` 与 `ServerPacketSender.sendAbstractBusState`。
 *
 * 1.21.1 下这些依赖**全部不可用**：
 *  - `li.cil.oc.common.asm.**`（ASM 注入层）已整体删除；
 *  - `li.cil.oc.integration.**`（`Mods` / `StargateTech2` / `AbstractBusCard`）未纳入编译范围；
 *  - `li.cil.oc.server.PacketSender` 未移植；
 *  - StargateTech2 自身也未移植（`lordfokas.stargatetech2.*`）。
 *
 * 因此这里只保留对外 API 表面与状态位，全部用最小占位实现，保证类型检查通过；
 * 恢复集成时请按下面的 TODO 逐项补回。
 */
trait AbstractBusAware extends TileEntity with network.Environment {
  // 注意：Scala 的自类型不会被继承，TileEntity 的每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  protected var _isAbstractBusAvailable: Boolean = false

  /** 宿主安装的组件（由 [[Computer]] 等 trait 实现）。 */
  def installedComponents: Iterable[ManagedEnvironment]

  /**
   * 返回该侧可用的抽象总线接口。
   *
   * TODO(integration.stargatetech2): 原返回 `Array[IBusInterface]`：服务端从
   * `installedComponents` 里收集 `AbstractBusCard#busInterface`，客户端返回一个假的接口实例。
   * 由于 StargateTech2 的 `IBusInterface` 不可用，这里退化为返回 `Array[AnyRef]` 且恒为空。
   */
  def getInterfaces(side: Int): Array[AnyRef] = Array.empty[AnyRef]

  def getWorld = world

  def getXCoord = x

  def getYCoord = y

  def getZCoord = z

  def isAbstractBusAvailable: Boolean = _isAbstractBusAvailable

  def isAbstractBusAvailable_=(value: Boolean): Unit = {
    if (value != isAbstractBusAvailable) {
      _isAbstractBusAvailable = value
      // TODO(integration.stargatetech2): 原实现在服务端且 `Mods.StargateTech2.isAvailable` 时
      // 调用 `StargateTech2.addDevice/removeDevice(world, x, y, z)` 把本方块注册进抽象总线。
      if (world != null) {
        notifyNeighbors()
        // TODO(server.PacketSender): 原服务端为 ServerPacketSender.sendAbstractBusState(this)，
        // 客户端为 world.markBlockForUpdate(x, y, z)。
        markBlockForUpdate()
      }
    }
  }

  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    isAbstractBusAvailable = nbt.getBoolean("isAbstractBusAvailable")
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean("isAbstractBusAvailable", isAbstractBusAvailable)
  }

  abstract override def onDisconnect(node: network.Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      isAbstractBusAvailable = false
    }
  }
}
