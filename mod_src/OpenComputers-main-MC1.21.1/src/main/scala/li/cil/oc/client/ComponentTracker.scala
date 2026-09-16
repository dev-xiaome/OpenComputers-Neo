package li.cil.oc.client

import li.cil.oc.common
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.LevelEvent

object ComponentTracker extends common.ComponentTracker {
  override protected def clear(world: Level) = if (world.isClientSide) super.clear(world)

  @SubscribeEvent
  def onWorldUnload(e: LevelEvent.Unload): Unit = worldUnloaded(e)
}
