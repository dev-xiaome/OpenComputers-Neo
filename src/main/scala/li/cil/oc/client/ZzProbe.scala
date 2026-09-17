package li.cil.oc.client

import li.cil.oc.api.event.FileSystemAccessEvent
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.common.NeoForge

object ZzProbe {
  def a(cond: Boolean, t: Option[BlockEntity], sound: String, data: CompoundTag): Unit = {
    if (cond) t match {
      case Some(x) =>
        NeoForge.EVENT_BUS.post(new FileSystemAccessEvent.Client(sound, x, data))
      case _ => // Invalid packet.
    }
  }

  def b(t: Option[BlockEntity], sound: String, data: CompoundTag): Unit = {
    t match {
      case Some(x) =>
        NeoForge.EVENT_BUS.post(new FileSystemAccessEvent.Client(sound, x, data))
      case _ => // Invalid packet.
    }
  }

  def c(t: BlockEntity, sound: String, data: CompoundTag): Unit = {
    NeoForge.EVENT_BUS.post(new FileSystemAccessEvent.Client(sound, t, data))
  }

  def d(t: BlockEntity, sound: String, data: CompoundTag): Unit = {
    val ev = new FileSystemAccessEvent.Client(sound, t, data)
    NeoForge.EVENT_BUS.post(ev)
  }

  def e(cond: Boolean, t: Option[BlockEntity], sound: String, data: CompoundTag): Unit = {
    if (cond) t match {
      case Some(x) =>
        val ev = new FileSystemAccessEvent.Client(sound, x, data)
        NeoForge.EVENT_BUS.post(ev)
      case _ => // Invalid packet.
    }
    else None match {
      case Some(x) =>
        NeoForge.EVENT_BUS.post(new FileSystemAccessEvent.Client(sound, x, data))
      case _ => // Invalid packet.
    }
  }
}
