package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.RegistryAccessHelper
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.IFluidTank
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.fluids.capability.templates.FluidTank

import scala.collection.convert.ImplicitConversionsToJava._

class UpgradeTank(val owner: EnvironmentHost, val capacity: Int) extends AbstractManagedEnvironment with IFluidTank with DeviceInfo {
  override val node = Network.newNode(this, Visibility.None).create()

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Tank upgrade",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Superblubb V10",
    DeviceAttribute.Capacity -> capacity.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // ----------------------------------------------------------------------- //

  val tank = new FluidTank(capacity)

  override def loadData(nbt: CompoundTag): Unit = {
    super.loadData(nbt)
    // 1.21.1 的 FluidTank NBT 读写需要 HolderLookup.Provider 参数。
    tank.readFromNBT(RegistryAccessHelper.getOrEmpty(), nbt)
  }

  override def saveData(nbt: CompoundTag): Unit = {
    super.saveData(nbt)
    // writeToNBT 是返回编码结果而不是就地写入，必须用返回值。
    nbt.merge(tank.writeToNBT(RegistryAccessHelper.getOrEmpty(), new CompoundTag))
  }

  // ----------------------------------------------------------------------- //

  override def getFluid = tank.getFluid

  override def getFluidAmount = tank.getFluidAmount

  override def getCapacity = tank.getCapacity

  override def isFluidValid(stack: FluidStack) = tank.isFluidValid(stack)

  override def fill(stack: FluidStack, action: FluidAction) = {
    val amount = tank.fill(stack, action)
    if (action.execute && amount > 0) {
      node.sendToVisible("computer.signal", "tank_changed", Int.box(tankIndex), Int.box(amount))
    }
    amount
  }

  override def drain(stack: FluidStack, action: FluidAction) = {
    val amount = tank.drain(stack, action)
    if (action.execute && amount != null && amount.getAmount > 0) {
      node.sendToVisible("computer.signal", "tank_changed", Int.box(tankIndex), Int.box(-amount.getAmount))
    }
    amount
  }

  override def drain(maxDrain: Int, action: FluidAction) = {
    val amount = tank.drain(maxDrain, action)
    if (action.execute && amount != null && amount.getAmount > 0) {
      node.sendToVisible("computer.signal", "tank_changed", Int.box(tankIndex), Int.box(-amount.getAmount))
    }
    amount
  }

  private def tankIndex = {
    owner match {
      case agent: li.cil.oc.api.internal.Agent if agent.tank != null =>
        val tanks = (0 until agent.tank.tankCount).map(agent.tank.getFluidTank)
        val index = tanks.indexOf(this)
        (index max 0) + 1
      case _ => 1
    }
  }
}
