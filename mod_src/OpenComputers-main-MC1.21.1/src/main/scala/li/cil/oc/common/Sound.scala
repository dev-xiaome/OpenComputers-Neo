package li.cil.oc.common

import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.server.PacketSender
import net.minecraft.resources.ResourceLocation

import scala.collection.mutable
import net.minecraft.sounds.SoundSource

object Sound {
  val globalTimeouts = mutable.WeakHashMap.empty[EnvironmentHost, mutable.Map[String, Long]]

  def play(host: EnvironmentHost, name: String) = this.synchronized {
    globalTimeouts.get(host) match {
      case Some(hostTimeouts) if hostTimeouts.getOrElse(name, 0L) > System.currentTimeMillis() => // Cooldown.
      case _ =>
        PacketSender.sendSound(host.getEnvironmentLevel, host.xPosition, host.yPosition, host.zPosition, ResourceLocation.fromNamespaceAndPath(Settings.resourceDomain, name), SoundSource.BLOCKS, 15 * Settings.get.soundVolume)
        globalTimeouts.getOrElseUpdate(host, mutable.Map.empty) += name -> (System.currentTimeMillis() + 500)
    }
  }

  def playDiskInsert(host: EnvironmentHost): Unit = {
    play(host, "floppy_insert")
  }

  def playDiskEject(host: EnvironmentHost): Unit = {
    play(host, "floppy_eject")
  }
}
