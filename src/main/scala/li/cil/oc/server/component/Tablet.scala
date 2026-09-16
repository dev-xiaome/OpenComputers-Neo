package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import net.neoforged.neoforge.items.IItemHandler

import scala.jdk.CollectionConverters._

/**
 * 「平板电脑」组件（1.7.10 的 `server.component.Tablet`，由 `TabletWrapper` 安装）。
 *
 * 1.21.1 迁移要点：
 *  - `tablet.getSizeInventory` → `tablet.getSlots`（`IInventory` → `IItemHandler`）。
 *  - `player.rotationPitch/rotationYaw` → `player.getXRot/getYRot`。
 *
 * 参数类型说明：1.7.10 的构造参数是 `common.item.TabletWrapper`。该类的宿主包装层
 * （`TabletWrapper`）属于 `common/item/Tablet.scala`（本批次范围外，该文件当前已降级，
 * 其 TODO 说明 `TabletWrapper` 尚未移植），所以这里改为按**它实际要用的两个能力**声明参数：
 * `internal.Tablet`（提供 `player`）与 `IItemHandler`（提供 `getSlots`）。
 * 将来 `TabletWrapper` 在 `common/item` 中移植完成后（它同时实现这两个接口），
 * 本类无需任何修改即可直接接收它。
 */
class Tablet(val tablet: internal.Tablet with IItemHandler) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withComponent("tablet").
    withConnector(Settings.get.bufferTablet).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.System,
    DeviceAttribute.Description -> "Tablet",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Jogger",
    DeviceAttribute.Capacity -> tablet.getSlots.toString
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function():number -- Gets the pitch of the player holding the tablet.""")
  def getPitch(context: Context, args: Arguments): Array[AnyRef] = result(tablet.player.getXRot)

  @Callback(doc = """function():number -- Gets the yaw of the player holding the tablet.""")
  def getYaw(context: Context, args: Arguments): Array[AnyRef] = result(tablet.player.getYRot)
}
