package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.network
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.nbt.CompoundTag

/**
 * 抽象总线（StargateTech2 的 Abstract Bus）方块实体 trait
 * （对应 1.7.10 的 `traits.AbstractBusAware`）。
 *
 * ==降级说明==
 * 1.7.10 通过 `@Injectable.Interface`（ASM 注入）让本 trait 实现
 * `lordfokas.stargatetech2.api.bus.IBusDevice`，并把 `installedComponents` 里的
 * `AbstractBusCard` 暴露为 `IBusInterface`；开关总线状态时还要调用
 * `StargateTech2.addDevice/removeDevice` 与 `ServerPacketSender.sendAbstractBusState`。
 *
 * 1.21.1 下仍缺失的依赖：
 *  - `li.cil.oc.common.asm.**`（ASM 注入层）已整体删除；
 *  - StargateTech2 自身及其集成类（`lordfokas.stargatetech2.*`、`Mods.StargateTech2`、
 *    `AbstractBusCard`）不存在，因此 `getInterfaces` 只保留占位实现。
 *
 * 注意：`li.cil.oc.integration.**` 与 `li.cil.oc.server.PacketSender` **都已纳入编译范围**，
 * 因此总线状态同步已按原实现接回（见 [[isAbstractBusAvailable_=]]）。
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
        // 服务端发专用 AbstractBusState 包，客户端走方块更新（对齐 1.7.10 原实现）。
        if (isServer) ServerPacketSender.sendAbstractBusState(this)
        else markBlockForUpdate()
      }
    }
  }

  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    isAbstractBusAvailable = nbt.getBoolean("isAbstractBusAvailable")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
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
