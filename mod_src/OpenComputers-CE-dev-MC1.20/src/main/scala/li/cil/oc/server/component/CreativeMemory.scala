package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment

import java.util
import scala.jdk.CollectionConverters._

class CreativeMemory extends AbstractManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Neighbors).create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Memory,
    DeviceAttribute.Description -> "Memory bank",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.ViridiaComputronics,
    DeviceAttribute.Product -> "MagicalMemory 3000",
    DeviceAttribute.Clock -> Int.MaxValue.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava
}
