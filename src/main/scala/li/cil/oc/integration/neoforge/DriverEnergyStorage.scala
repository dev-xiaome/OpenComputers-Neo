package li.cil.oc.integration.neoforge

import li.cil.oc.api.Network
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage

/**
  * @author Vexatos
  */
object DriverEnergyStorage extends DriverBlock {

  override def worksWith(world: Level, pos: BlockPos, side: Direction): Boolean =
    world.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null

  override def createEnvironment(world: Level, pos: BlockPos, side: Direction): ManagedEnvironment =
    Option(world.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side)) match {
      case Some(storage) => new Environment(storage)
      case _ => null
    }

  final class Environment(val storage: IEnergyStorage) extends AbstractManagedEnvironment with NamedBlock {

    setNode(Network.newNode(this, Visibility.Network).withComponent("energy_device").create)

    @Callback(doc = "function():number -- Returns the amount of stored energy on the connected side.")
    def getEnergyStored(context: Context, args: Arguments): Array[AnyRef] = result(storage.getEnergyStored)

    @Callback(doc = "function():number -- Returns the maximum amount of stored energy on the connected side.")
    def getMaxEnergyStored(context: Context, args: Arguments): Array[AnyRef] = result(storage.getMaxEnergyStored)

    @Callback(doc = "function():number -- Returns whether this component can have energy extracted from the connected side.")
    def canExtract(context: Context, args: Arguments): Array[AnyRef] = result(storage.canExtract)

    @Callback(doc = "function():number -- Returns whether this component can receive energy on the connected side.")
    def canReceive(context: Context, args: Arguments): Array[AnyRef] = result(storage.canReceive)

    override def preferredName(): String = "energy_device"

    override def priority(): Int = 0
  }
}
