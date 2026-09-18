package li.cil.oc.integration.neoforge

import li.cil.oc.api
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

object ModNeoForge extends ModProxy {
  override def getMod: Mod = Mods.NeoForge

  override def initialize(): Unit = {
    api.IMC.registerItemCharge("MinecraftForge",
      "li.cil.oc.integration.neoforge.EventHandlerNeoForge.canCharge",
      "li.cil.oc.integration.neoforge.EventHandlerNeoForge.charge")
    api.Driver.add(DriverEnergyStorage)
  }
}
