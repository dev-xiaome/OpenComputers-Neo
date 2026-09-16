package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.energy.IEnergyStorage

trait Chargeable extends li.cil.oc.api.driver.item.Chargeable {
  def maxCharge(stack: ItemStack): Double
  def getCharge(stack: ItemStack): Double
  def setCharge(stack: ItemStack, amount: Double): Unit
  def canExtract(stack: ItemStack): Boolean = false
}

object Chargeable {
  // NeoForge 1.21.1: KEY (ResourceLocation) is no longer needed.
  // Capability registration is done via RegisterCapabilitiesEvent in EventHandler.

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

  class Provider(val stack: ItemStack, val item: Chargeable) extends IEnergyStorage {

    override def receiveEnergy(maxReceive: Int, simulate: Boolean): Int =
      maxReceive - convertOpenComputersToForgeEnergy(item.charge(stack, convertForgeEnergyToOpenComputers(maxReceive), simulate))

    override def extractEnergy(maxExtract: Int, simulate: Boolean): Int = {
      if (canExtract) -receiveEnergy(-maxExtract, simulate) else 0
    }

    override def getEnergyStored: Int = convertOpenComputersToForgeEnergy(item.getCharge(stack))

    override def getMaxEnergyStored: Int = convertOpenComputersToForgeEnergy(item.maxCharge(stack))

    override def canExtract: Boolean = item.canExtract(stack)

    override def canReceive: Boolean = item.canCharge(stack)
  }
}