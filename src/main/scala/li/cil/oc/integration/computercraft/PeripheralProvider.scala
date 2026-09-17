package li.cil.oc.integration.computercraft

import dan200.computercraft.api.peripheral.IPeripheral
import li.cil.oc.common.blockentity.Relay
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.capabilities.{Capability, CapabilityManager, CapabilityToken, ICapabilityProvider}
import net.neoforged.neoforge.common.util.LazyOptional
import net.neoforged.neoforge.event.AttachCapabilitiesEvent
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.bus.api.SubscribeEvent
import li.cil.oc.OpenComputers
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.fml.ModList

object PeripheralProvider {
  val CAPABILITY_PERIPHERAL: Capability[IPeripheral] = CapabilityManager.get(new CapabilityToken[IPeripheral]() {})

  private val PERIPHERAL_KEY = ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, "peripheral")

  def register(): Unit = {
    if (ModList.get().isLoaded("computercraft")) return
    NeoForge.EVENT_BUS.register(this)
  }

  @SubscribeEvent
  def attachCapabilities(event: AttachCapabilitiesEvent[BlockEntity]): Unit = {
    event.getObject match {
      case relay: Relay =>
        val peripheral = new RelayPeripheral(relay)
        val lazyOptional = LazyOptional.of(() => peripheral)

        event.addCapability(PERIPHERAL_KEY, new ICapabilityProvider {
          override def getCapability[T](cap: Capability[T], side: Direction): LazyOptional[T] =
            if (cap == CAPABILITY_PERIPHERAL) lazyOptional.cast()
            else LazyOptional.empty()
        })

        event.addListener(() => lazyOptional.invalidate())
      case _ =>
    }
  }
}