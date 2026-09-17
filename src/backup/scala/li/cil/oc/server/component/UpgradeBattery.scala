package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab

import scala.jdk.CollectionConverters._

class UpgradeBattery(val tier: Int) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withConnector(Settings.get.bufferCapacitorUpgrades(tier)).
    create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Battery",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Unlimited Power (Almost Ed.)",
    DeviceAttribute.Capacity -> Settings.get.bufferCapacitorUpgrades(tier).toString
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava
}
