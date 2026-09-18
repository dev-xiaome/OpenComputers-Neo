package li.cil.oc.server

import li.cil.oc.common
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.LevelEvent

object ComponentTracker extends common.ComponentTracker {
  override protected def clear(level: Level) = if (!level.isClientSide) super.clear(level)

  @SubscribeEvent
  def onWorldUnload(e: LevelEvent.Unload): Unit = worldUnloaded(e)
}
