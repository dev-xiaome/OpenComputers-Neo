package li.cil.oc.integration.computercraft

import dan200.computercraft.api.peripheral.{IPeripheral, PeripheralCapability}
import li.cil.oc.OpenComputers
import li.cil.oc.common.blockentity.{Relay, BlockEntityTypes}
import net.minecraft.core.Direction
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent

object PeripheralProvider {
  def register(): Unit = {
    if (!isComputerCraftPresent()) return
    OpenComputers.proxy.modBus.addListener(onRegisterCapabilities)
  }

  private def isComputerCraftPresent(): Boolean = {
    try {
      Class.forName("dan200.computercraft.api.peripheral.IDynamicPeripheral",
        false, getClass.getClassLoader)
      true
    } catch {
      case _: ClassNotFoundException => false
    }
  }

  def onRegisterCapabilities(event: RegisterCapabilitiesEvent): Unit = {
    event.registerBlockEntity(
      PeripheralCapability.get(),
      BlockEntityTypes.RELAY.get(),
      (relay: Relay, _: Direction) => new RelayPeripheral(relay): IPeripheral
    )
  }
}
