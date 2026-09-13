package li.cil.oc.common.tileentity.traits.power

import net.neoforged.fml.common.Optional
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.core.Direction

@Injectable.Interface(value = "mekanism.api.energy.IStrictEnergyAcceptor", modid = Mods.IDs.Mekanism)
trait Mekanism extends Common {
  @Optional.Method(modid = Mods.IDs.Mekanism)
  def canReceiveEnergy(side: Direction) = Mods.Mekanism.isAvailable && canConnectPower(side)

  @Optional.Method(modid = Mods.IDs.Mekanism)
  def transferEnergyToAcceptor(side: Direction, amount: Double) =
    if (!Mods.Mekanism.isAvailable) 0
    else Power.toJoules(tryChangeBuffer(side, Power.fromJoules(amount)))

  @Optional.Method(modid = Mods.IDs.Mekanism)
  def getMaxEnergy = Power.toJoules(Direction.VALID_DIRECTIONS.map(globalBufferSize).max)

  @Optional.Method(modid = Mods.IDs.Mekanism)
  def getEnergy = Power.toJoules(Direction.VALID_DIRECTIONS.map(globalBuffer).max)

  @Optional.Method(modid = Mods.IDs.Mekanism)
  def setEnergy(energy: Double) {}
}
