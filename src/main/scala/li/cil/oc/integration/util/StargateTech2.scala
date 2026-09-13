package li.cil.oc.integration.util

import lordfokas.stargatetech2.api.bus.BusEvent.AddToNetwork
import lordfokas.stargatetech2.api.bus.BusEvent.RemoveFromNetwork
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge

object StargateTech2 {
  def addDevice(world: Level, x: Int, y: Int, z: Int) = MinecraftForge.EVENT_BUS.post(new AddToNetwork(world, x, y, z))

  def removeDevice(world: Level, x: Int, y: Int, z: Int) = MinecraftForge.EVENT_BUS.post(new RemoveFromNetwork(world, x, y, z))
}