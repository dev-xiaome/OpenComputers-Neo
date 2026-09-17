package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import net.minecraft.nbt.CompoundTag

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 无线红石控制器组件（原 `server.component.RedstoneWireless`）。
 *
 * ==1.21.1 降级说明（WR-CBE 集成）==
 * 原实现直接实现 WR-CBE（Wireless Redstone ChickenBones Edition）的两个接口
 * `codechicken.wirelessredstone.core.WirelessReceivingDevice` /
 * `WirelessTransmittingDevice`，并用 `@Optional.InterfaceList` / `@Optional.Method`
 * 做「模组存在才生效」的软依赖。
 *
 * TODO(integration): WR-CBE 在 1.21.1 **没有对应实现**（`codechicken.*` 不存在，
 * 该项目自身也未移植），NeoForge 也没有 1.7.10 的 `@Optional.Interface` 注入机制
 * （`net.neoforged.fml.common.Optional` 已不存在），因此：
 *  - 两个 `codechicken` 接口不再实现，随之删除只服务于它们的四个接口方法
 *    `getPosition` / `getDimension` / `getFreq` / `getAttachedEntity`
 *    （其中 `getDimension` 还依赖已移除的 `level.provider.dimensionId`）。
 *    [[li.cil.oc.integration.util.WirelessRedstone]] 目前没有任何
 *    `WirelessRedstoneSystem` 实现（`systems` 恒为空），恢复 WR-CBE
 *    （或等价的无线红石模组）时请在 `integration` 层新增驱动，并在这里重新实现上述方法。
 *  - [[updateDevice]] 保留为普通公开方法（原接口回调入口），签名不变，
 *    方便将来集成层直接调用；`wirelessInput` 因此目前只会被
 *    [[getWirelessInput]] 与存档读写修改。
 *  - 无线红石系统注册表原为 `li.cil.oc.integration.util.WirelessRedstone`（未纳入编译范围），
 *    这里内联为本文件末尾的 [[WirelessRedstoneRegistry]]，逐行对应原实现。
 *    它不是 `integration` 的那份对象，恢复集成层时请改回引用；详见该对象的 TODO。
 *  - `onConnect` 原为 `common.EventHandler.scheduleWirelessRedstone(this)`（延迟一 tick 注册），
 *    `common.EventHandler` 未纳入编译范围，且它自身也依赖同一份未编译的集成层。
 *    参照 `server.component.UpgradeLeash` 的同类处理（原 `EventHandler.scheduleServer`
 *    在那里也是直接执行），这里立即注册：此时节点已经在网络上，语义等价，只少了
 *    一 tick 的延迟。
 *  - 其余行为（频率、输入 / 输出缓存、节点断开时注销、存档）与 1.7.10 完全一致。
 *
 * 影响面：Lua 侧 `redstone` 组件的 `getWirelessInput` / `setWirelessOutput` /
 * `getWirelessFrequency` / `setWirelessFrequency` 回调仍然存在且可调用，
 * 只是在没有无线红石系统注册的情况下不会真正收发信号（`getWirelessInput` 恒为 false）。
 */
trait RedstoneWireless extends RedstoneSignaller with DeviceInfo {
  def redstone: EnvironmentHost

  var wirelessFrequency = 0

  var wirelessInput = false

  var wirelessOutput = false

  // ----------------------------------------------------------------------- //

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Communication,
    DeviceAttribute.Description -> "Wireless redstone controller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Rw400-M",
    DeviceAttribute.Capacity -> "1",
    DeviceAttribute.Width -> "1"
  )

  // 1.21.1：`DeviceInfo#getDeviceInfo` 返回 `java.util.Map`，Scala 的 `Map` 需要显式转换。
  override def getDeviceInfo: java.util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function():number -- Get the wireless redstone input.""")
  def getWirelessInput(context: Context, args: Arguments): Array[AnyRef] = {
    wirelessInput = WirelessRedstoneRegistry.getInput(this)
    result(wirelessInput)
  }

  @Callback(direct = true, doc = """function():boolean -- Get the wireless redstone output.""")
  def getWirelessOutput(context: Context, args: Arguments): Array[AnyRef] = result(wirelessOutput)

  @Callback(doc = """function(value:boolean):boolean -- Set the wireless redstone output.""")
  def setWirelessOutput(context: Context, args: Arguments): Array[AnyRef] = {
    val oldValue = wirelessOutput
    val newValue = args.checkBoolean(0)

    if (oldValue != newValue) {
      wirelessOutput = newValue

      WirelessRedstoneRegistry.updateOutput(this)

      if (Settings.get.redstoneDelay > 0)
        context.pause(Settings.get.redstoneDelay)
    }

    result(oldValue)
  }

  @Callback(direct = true, doc = """function():number -- Get the currently set wireless redstone frequency.""")
  def getWirelessFrequency(context: Context, args: Arguments): Array[AnyRef] = result(wirelessFrequency)

  @Callback(doc = """function(frequency:number):number -- Set the wireless redstone frequency to use.""")
  def setWirelessFrequency(context: Context, args: Arguments): Array[AnyRef] = {
    val oldValue = wirelessFrequency
    val newValue = args.checkInteger(0)

    if (oldValue != newValue) {
      WirelessRedstoneRegistry.removeReceiver(this)
      WirelessRedstoneRegistry.removeTransmitter(this)

      wirelessFrequency = newValue
      wirelessInput = false
      wirelessOutput = false

      WirelessRedstoneRegistry.addReceiver(this)

      context.pause(0.5)
    }

    result(oldValue)
  }

  // ----------------------------------------------------------------------- //

  /**
   * 无线红石接收回调（原 `WirelessReceivingDevice#updateDevice`）。
   *
   * TODO(integration): 原实现带 `@Optional.Method(modid = Mods.IDs.WirelessRedstoneCBE)`，
   * 由 WR-CBE 在有信号变化时调用。1.21.1 没有 WR-CBE，这里保留方法与签名，
   * 供将来的集成层驱动调用。
   *
   * 原实现在「无线」场景下用 `ForgeDirection.UNKNOWN` 构造 [[RedstoneChangedEventArgs]]；
   * 1.21.1 的 `Direction` 没有 `UNKNOWN`，这里传 `null` 表示「无线来源」，
   * 与 [[RedstoneSignaller.onRedstoneChanged]] 的约定一致（Lua 侧仍收到 "wireless"）。
   */
  def updateDevice(frequency: Int, on: Boolean): Unit = {
    if (frequency == wirelessFrequency && on != wirelessInput) {
      wirelessInput = on
      onRedstoneChanged(RedstoneChangedEventArgs(null, if (on) 0 else 1, if (on) 1 else 0))
    }
  }

  // ----------------------------------------------------------------------- //

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
    if (node == this.node) {
      // 原实现：`EventHandler.scheduleWirelessRedstone(this)`（延迟一 tick 注册 + 刷新输出）。
      // TODO(common.EventHandler): `common/EventHandler.scala` 未纳入编译范围（它自身依赖未编译的
      // `integration` 层），且原调度器只是把这两步推到下一个服务端 tick；此时节点已在网络上，
      // 直接执行语义等价（与 `UpgradeLeash` 处理 `EventHandler.scheduleServer` 的方式一致）。
      // 恢复调度器后改回 `EventHandler.scheduleWirelessRedstone(this)`。
      WirelessRedstoneRegistry.addReceiver(this)
      WirelessRedstoneRegistry.updateOutput(this)
    }
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    if (node == this.node) {
      WirelessRedstoneRegistry.removeReceiver(this)
      WirelessRedstoneRegistry.removeTransmitter(this)
      wirelessOutput = false
      wirelessFrequency = 0
    }
  }

  // ----------------------------------------------------------------------- //

  override def load(nbt: CompoundTag): Unit = {
    super.load(nbt)
    // 1.21.1：`CompoundTag#getInteger` → `getInt`。
    wirelessFrequency = nbt.getInt("wirelessFrequency")
    wirelessInput = nbt.getBoolean("wirelessInput")
    wirelessOutput = nbt.getBoolean("wirelessOutput")
  }

  override def save(nbt: CompoundTag): Unit = {
    super.save(nbt)
    nbt.putInt("wirelessFrequency", wirelessFrequency)
    nbt.putBoolean("wirelessInput", wirelessInput)
    nbt.putBoolean("wirelessOutput", wirelessOutput)
  }
}

object WirelessRedstoneRegistry {

  /**
   * 无线红石系统注册表（原 `li.cil.oc.integration.util.WirelessRedstone` 的内联副本）。
   *
   * TODO(integration): 原实现位于
   * `src/main/scala/li/cil/oc/integration/util/WirelessRedstone.scala`，
   * 该包未纳入编译范围，`server.component` 层无法 import，因此这里按原实现
   * **逐行内联**一份（成员名、语义完全一致）。
   *
   * 影响：这是**独立**的注册表。当前没有任何 `WirelessRedstoneSystem` 实现
   * （WR-CBE 与等价模组在 1.21.1 都不存在），`systems` 恒为空，所以两份注册表
   * 行为完全一致——所有操作空转，`getInput` 恒为 `false`。
   * 将来补齐集成层后，请把本文件里对 [[WirelessRedstoneRegistry]] 的引用改回
   * `li.cil.oc.integration.util.WirelessRedstone` 并删除本对象，否则会出现两份注册表、
   * 「接收器注册到哪一份取决于调用方」的隐患。
   */
  val systems = mutable.Set.empty[WirelessRedstoneSystem]

  def isAvailable = systems.nonEmpty

  def addReceiver(rs: RedstoneWireless): Unit = {
    systems.foreach(system => try system.addReceiver(rs) catch {
      case _: Throwable => // Ignore
    })
  }

  def removeReceiver(rs: RedstoneWireless): Unit = {
    systems.foreach(system => try system.removeReceiver(rs) catch {
      case _: Throwable => // Ignore
    })
  }

  def updateOutput(rs: RedstoneWireless): Unit = {
    systems.foreach(system => try system.updateOutput(rs) catch {
      case _: Throwable => // Ignore
    })
  }

  def removeTransmitter(rs: RedstoneWireless): Unit = {
    systems.foreach(system => try system.removeTransmitter(rs) catch {
      case _: Throwable => // Ignore
    })
  }

  def getInput(rs: RedstoneWireless): Boolean = systems.exists(_.getInput(rs))

  trait WirelessRedstoneSystem {
    def addReceiver(rs: RedstoneWireless)

    def removeReceiver(rs: RedstoneWireless)

    def updateOutput(rs: RedstoneWireless)

    def removeTransmitter(rs: RedstoneWireless)

    def getInput(rs: RedstoneWireless): Boolean
  }
}
