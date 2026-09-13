package li.cil.oc.common.tileentity.traits.power

import net.neoforged.fml.common.Optional
import net.neoforged.bus.api.Event
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.NeoForge
import net.minecraft.core.Direction

@Injectable.Interface(value = "ic2.api.energy.tile.IEnergySink", modid = Mods.IDs.IndustrialCraft2)
trait IndustrialCraft2Experimental extends Common with IndustrialCraft2Common {
  private var conversionBuffer = 0.0

  private def useIndustrialCraft2Power() = isServer && Mods.IndustrialCraft2.isAvailable

  // ----------------------------------------------------------------------- //

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (useIndustrialCraft2Power() && world.getTotalWorldTime % Settings.get.tickFrequency == 0) {
      updateEnergy()
    }
  }

  @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
  private def updateEnergy(): Unit = {
    tryAllSides((demand, _) => {
      val result = math.min(demand, conversionBuffer)
      conversionBuffer -= result
      result
    }, Power.fromEU, Power.toEU)
  }

  override def validate(): Unit = {
    super.validate()
    if (useIndustrialCraft2Power() && !addedToIC2PowerGrid) EventHandler.scheduleIC2Add(this)
  }

  override def invalidate(): Unit = {
    super.invalidate()
    if (useIndustrialCraft2Power() && addedToIC2PowerGrid) removeFromIC2Grid()
  }

  override def onChunkUnload(): Unit = {
    super.onChunkUnload()
    if (useIndustrialCraft2Power() && addedToIC2PowerGrid) removeFromIC2Grid()
  }

  private def removeFromIC2Grid(): Unit = {
    try MinecraftForge.EVENT_BUS.post(Class.forName("ic2.api.energy.event.EnergyTileUnloadEvent").getConstructor(Class.forName("ic2.api.energy.tile.IEnergyTile")).newInstance(this).asInstanceOf[Event]) catch {
      case t: Throwable => OpenComputers.log.warn("Error removing node from IC2 grid.", t)
    }
    addedToIC2PowerGrid = false
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    conversionBuffer = nbt.getDouble(Settings.namespace + "ic2power")
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putDouble(Settings.namespace + "ic2power", conversionBuffer)
  }

  // ----------------------------------------------------------------------- //

  @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
  def getSinkTier: Int = Int.MaxValue

  @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
  def acceptsEnergyFrom(emitter: net.minecraft.tileentity.BlockEntity, direction: Direction): Boolean = useIndustrialCraft2Power && canConnectPower(direction)

  @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
  def injectEnergy(directionFrom: Direction, amount: Double, voltage: Double): Double = {
    conversionBuffer += amount
    0.0
  }

  @Optional.Method(modid = Mods.IDs.IndustrialCraft2)
  def getDemandedEnergy: Double = {
    if (!useIndustrialCraft2Power()) 0.0
    else if (conversionBuffer < energyThroughput * Settings.get.tickFrequency)
      math.min(Direction.VALID_DIRECTIONS.map(globalDemand).max, Power.toEU(energyThroughput))
    else 0
  }
}
