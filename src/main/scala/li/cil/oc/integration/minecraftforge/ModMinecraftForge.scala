package li.cil.oc.integration.minecraftforge

import li.cil.oc.api
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import net.neoforged.neoforge.common.NeoForge

object ModNeoForge extends ModProxy {
  override def getMod: Mod = Mods.Forge

  override def initialize(): Unit = {
    NeoForge.EVENT_BUS.register(EventHandlerNeoForge)
    api.IMC.registerItemCharge("NeoForge",
      "li.cil.oc.integration.minecraftforge.EventHandlerNeoForge.canCharge",
      "li.cil.oc.integration.minecraftforge.EventHandlerNeoForge.charge")
    api.Driver.add(DriverEnergyStorage)
  }
}
