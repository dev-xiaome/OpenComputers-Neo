package li.cil.oc.common.tileentity.traits.power

import net.neoforged.fml.common.Optional
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.core.Direction

@Injectable.Interface(value = "cofh.api.energy.IEnergyHandler", modid = Mods.IDs.CoFHEnergy)
trait RedstoneFlux extends Common {
  @Optional.Method(modid = Mods.IDs.CoFHEnergy)
  def canConnectEnergy(from: Direction) = Mods.CoFHEnergy.isAvailable && canConnectPower(from)

  @Optional.Method(modid = Mods.IDs.CoFHEnergy)
  def receiveEnergy(from: Direction, maxReceive: Int, simulate: Boolean) =
    if (!Mods.CoFHEnergy.isAvailable) 0
    else Power.toRF(tryChangeBuffer(from, Power.fromRF(maxReceive), !simulate))

  @Optional.Method(modid = Mods.IDs.CoFHEnergy)
  def getEnergyStored(from: Direction) = Power.toRF(globalBuffer(from))

  @Optional.Method(modid = Mods.IDs.CoFHEnergy)
  def getMaxEnergyStored(from: Direction) = Power.toRF(globalBufferSize(from))

  @Optional.Method(modid = Mods.IDs.CoFHEnergy)
  def extractEnergy(from: Direction, maxExtract: Int, simulate: Boolean) = 0
}
