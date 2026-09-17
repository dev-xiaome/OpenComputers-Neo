package li.cil.oc.common.item.traits

import li.cil.oc.integration.Mods
import li.cil.oc.integration.opencomputers.ModOpenComputers
import li.cil.oc.{Settings, api}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.energy.IEnergyStorage

// TODO Forge power capabilities.
trait Chargeable extends api.driver.item.Chargeable {

  def maxCharge(stack: ItemStack): Double

  def getCharge(stack: ItemStack): Double

  def setCharge(stack: ItemStack, amount: Double): Unit

  def canExtract(stack: ItemStack): Boolean = false
}

object Chargeable {
  val KEY = ResourceLocation.fromNamespaceAndPath(ModOpenComputers.getMod.id, "chargeable")

  def convertForgeEnergyToOpenComputers(fe: Int): Double = fe / Settings.get.ratioForgeEnergy

  def convertOpenComputersToForgeEnergy(oc: Double): Int = (oc * Settings.get.ratioForgeEnergy).toInt

  def applyCharge(amount: Double, current: Double, maximum: Double, save: Double => Unit): Double = {
    val target = current + amount
    val result = (target max 0) min maximum
    val used = result - current
    val unused = amount - used
    if (used > Double.MinPositiveValue || used < -Double.MinPositiveValue) {
      save(used)
    }
    unused
  }

  /**
   * 可充电物品的 Forge 能量视图。
   *
   * 1.21.1 里 provider 由能力系统按查询即时创建（不再有 `LazyOptional` 需要失效，
   * 也不需要自己实现 `ICapabilityProvider`），注册见
   * [[li.cil.oc.common.capabilities.Capabilities#onRegisterCapabilities]]。
   */
  class Provider(val stack: ItemStack, val item: li.cil.oc.common.item.traits.Chargeable) extends IEnergyStorage {

    def receiveEnergy(maxReceive: Int, simulate: Boolean): Int =
      // Chargeable.charge() returns the amount UNUSED
      // IEnergyStorage wants the amount USED
      maxReceive - convertOpenComputersToForgeEnergy(item.charge(stack, convertForgeEnergyToOpenComputers(maxReceive), simulate))

    def extractEnergy(maxExtract: Int, simulate: Boolean): Int = {
      if (canExtract) {
        -receiveEnergy(-maxExtract, simulate)
      } else {
        0
      }
    }

    def getEnergyStored: Int = convertOpenComputersToForgeEnergy(item.getCharge(stack))

    def getMaxEnergyStored: Int = convertOpenComputersToForgeEnergy(item.maxCharge(stack))

    def canExtract: Boolean = item.canExtract(stack)

    def canReceive: Boolean = item.canCharge(stack)
  }
}
