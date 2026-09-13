package li.cil.oc.common

import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.sounds.SoundSource

import scala.collection.mutable

/**
 * 世界内音效播放（带 500ms 冷却，避免同一主机连续播放刷屏）。
 *
 * 1.21.1 迁移要点：`World#playSoundEffect` → `Level#playSound`，
 * 音效名 → 注册过的 [[SoundEvents]] 事件。
 */
object Sound {
  val globalTimeouts = mutable.WeakHashMap.empty[EnvironmentHost, mutable.Map[String, Long]]

  def play(host: EnvironmentHost, name: String): Unit = this.synchronized {
    globalTimeouts.get(host) match {
      case Some(hostTimeouts) if hostTimeouts.getOrElse(name, 0L) > System.currentTimeMillis() => // Cooldown.
      case _ =>
        val level = host.world()
        val event = SoundEvents.byName(name)
        if (level != null && event != null) {
          level.playSound(
            null,
            host.xPosition(), host.yPosition(), host.zPosition(),
            event, SoundSource.BLOCKS,
            Settings.get.soundVolume, 1f
          )
        }
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
