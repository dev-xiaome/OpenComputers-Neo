package li.cil.oc.common.tileentity

import java.util

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network._
import net.minecraft.core.Direction

import scala.jdk.CollectionConverters._

class PowerConverter extends traits.PowerAcceptor with traits.Environment with traits.NotAnalyzable with DeviceInfo {
  val node = api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferConverter).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Power converter",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Transgizer-PX5",
    DeviceAttribute.Capacity -> energyThroughput.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  @SideOnly(Dist.CLIENT)
  override protected def hasConnector(side: Direction) = true

  override protected def connector(side: Direction) = Option(node)

  override def energyThroughput = Settings.get.powerConverterRate

  override def canUpdate = isServer
}
