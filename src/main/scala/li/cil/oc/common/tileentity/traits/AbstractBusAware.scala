package li.cil.oc.common.tileentity.traits

import net.neoforged.fml.common.Optional
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.api.network
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.stargatetech2.AbstractBusCard
import li.cil.oc.integration.util.StargateTech2
import li.cil.oc.server.component
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import lordfokas.stargatetech2.api.StargateTechAPI
import lordfokas.stargatetech2.api.bus.IBusDevice
import lordfokas.stargatetech2.api.bus.IBusInterface
import net.minecraft.nbt.CompoundTag

@Injectable.Interface(value = "lordfokas.stargatetech2.api.bus.IBusDevice", modid = Mods.IDs.StargateTech2)
trait AbstractBusAware extends BlockEntity with network.Environment {
  protected var _isAbstractBusAvailable: Boolean = _

  protected lazy val fakeInterface = Array[AnyRef](StargateTechAPI.api.getFactory.getIBusInterface(this.asInstanceOf[IBusDevice], null))

  def installedComponents: Iterable[ManagedEnvironment]

  @Optional.Method(modid = Mods.IDs.StargateTech2)
  def getInterfaces(side: Int): Array[IBusInterface] =
    if (isAbstractBusAvailable) {
      if (isServer) {
        installedComponents.collect {
          case abstractBus: AbstractBusCard => abstractBus.busInterface
        }.toArray
      }
      else fakeInterface.map(_.asInstanceOf[IBusInterface])
    }
    else null

  def getWorld = world

  def getXCoord = x

  def getYCoord = y

  def getZCoord = z

  def isAbstractBusAvailable = _isAbstractBusAvailable

  def isAbstractBusAvailable_=(value: Boolean) = {
    if (value != isAbstractBusAvailable) {
      _isAbstractBusAvailable = value
      if (isServer && Mods.StargateTech2.isAvailable) {
        if (isAbstractBusAvailable) StargateTech2.addDevice(world, x, y, z)
        else StargateTech2.removeDevice(world, x, y, z)
      }
      world.notifyBlocksOfNeighborChange(x, y, z, block)
      if (isServer) ServerPacketSender.sendAbstractBusState(this)
      else world.markBlockForUpdate(x, y, z)
    }
    this
  }

  @SideOnly(Dist.CLIENT)
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    isAbstractBusAvailable = nbt.getBoolean("isAbstractBusAvailable")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean("isAbstractBusAvailable", isAbstractBusAvailable)
  }

  abstract override def onDisconnect(node: network.Node) {
    super.onDisconnect(node)
    if (node == this.node) {
      isAbstractBusAvailable = false
    }
  }
}
