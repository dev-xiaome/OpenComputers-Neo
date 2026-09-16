package li.cil.oc.client

import java.net.MalformedURLException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import com.google.common.base.Charsets
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent

import scala.collection.mutable
import scala.ref.WeakReference
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.client.resources.sounds.{AbstractSoundInstance, SoundInstance, TickableSoundInstance}
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.event.level.LevelEvent

object Sound {
  private val sources = mutable.WeakHashMap.empty[BlockEntity, PseudoLoopingStream]
  // SoundEngine owns the actual sound instance. Keep a strong reference to
  // every instance while it is playing so cleanup cannot lose it when the
  // block entity key disappears from the weak map during a world transition.
  private val activeSounds = mutable.Set.empty[PseudoLoopingStream]

  private val commandQueue = mutable.PriorityQueue.empty[Command]

  private val updateTimer = new Timer("OpenComputers-SoundUpdater", true)
  if (Settings.get.soundVolume > 0) {
    updateTimer.scheduleAtFixedRate(new TimerTask {
      override def run(): Unit = {
        sources.synchronized(Sound.updateCallable = Some(() => processQueue()))
      }
    }, 500, 50)
  }

  private var updateCallable = None: Option[() => Unit]

  private def processQueue(): Unit = {
    if (commandQueue.nonEmpty) {
      commandQueue.synchronized {
        while (commandQueue.nonEmpty && commandQueue.head.when < System.currentTimeMillis()) {
          try commandQueue.dequeue()() catch {
            case t: Throwable => OpenComputers.log.warn("Error processing sound command.", t)
          }
        }
      }
    }
  }

  def startLoop(BlockEntity: BlockEntity, name: String, volume: Float = 1f, delay: Long = 0): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StartCommand(System.currentTimeMillis() + delay, WeakReference(BlockEntity), name, volume)
      }
    }
  }

  def stopLoop(BlockEntity: BlockEntity): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StopCommand(WeakReference(BlockEntity))
      }
    }
  }

  def updatePosition(BlockEntity: BlockEntity): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new UpdatePositionCommand(WeakReference(BlockEntity))
      }
    }
  }

  @SubscribeEvent
  def onTick(e: ClientTickEvent.Pre): Unit = {
    sources.synchronized {
      updateCallable.foreach(_ ())
      updateCallable = None
    }
  }

  @SubscribeEvent
  def onWorldUnload(event: LevelEvent.Unload): Unit = {
    stopAll()
  }

  def stopAll(): Unit = {
    commandQueue.synchronized(commandQueue.clear())
    sources.synchronized {
      try activeSounds.toSeq.foreach(stopSound) catch {
        case _: Throwable => // Ignore.
      }
      activeSounds.clear()
      sources.clear()
    }
  }

  private def stopSound(sound: PseudoLoopingStream): Unit = {
    sound.stop()
    val minecraft = Minecraft.getInstance
    if (minecraft != null && minecraft.getSoundManager != null) {
      // Marking a TickableSoundInstance stopped only makes SoundEngine notice
      // it on its next tick. Stop the channel too, so a world transition or
      // resource reload cannot leave the fan playing in the next world.
      minecraft.getSoundManager.stop(sound)
    }
    activeSounds -= sound
  }

  private abstract class Command(val when: Long, val blockEntity: WeakReference[BlockEntity]) extends Ordered[Command] {
    def apply(): Unit

    override def compare(that: Command) = (that.when - when).toInt
  }

  private class StartCommand(when: Long, blockEntity: WeakReference[BlockEntity], val name: String, val volume: Float) extends Command(when, blockEntity) {
    override def apply(): Unit = {
      blockEntity.get.foreach { entity =>
        sources.synchronized {
          val current = sources.getOrElse(entity, null)
          if (current == null || !current.getLocation.getPath.equals(name)) {
            if (current != null) stopSound(current)
            val sound = new PseudoLoopingStream(blockEntity, volume, name)
            sources(entity) = sound
            activeSounds += sound
            Minecraft.getInstance.getSoundManager.play(sound)
          }
        }
      }
    }
  }

  private class StopCommand(blockEntity: WeakReference[BlockEntity]) extends Command(System.currentTimeMillis() + 1, blockEntity) {
    override def apply(): Unit = {
      blockEntity.get.foreach { entity =>
        sources.synchronized {
          sources.remove(entity) match {
            case Some(sound) => stopSound(sound)
            case _ =>
          }
        }
        commandQueue.synchronized {
          // Remove all other commands for this block entity from the queue.
          commandQueue ++= commandQueue.dequeueAll.filter(_.blockEntity.get.exists(_ ne entity))
        }
      }
    }
  }

  private class UpdatePositionCommand(blockEntity: WeakReference[BlockEntity]) extends Command(System.currentTimeMillis(), blockEntity) {
    override def apply(): Unit = {
      blockEntity.get.foreach { entity =>
        sources.synchronized {
          sources.get(entity) match {
            case Some(sound) => sound.updatePosition()
            case _ =>
          }
        }
      }
    }
  }

  private class PseudoLoopingStream(val blockEntity: WeakReference[BlockEntity], val subVolume: Float, name: String)
    extends AbstractSoundInstance(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, name), SoundSource.BLOCKS, SoundInstance.createUnseededRandom()) with TickableSoundInstance {

    var stopped = false
    volume = subVolume * Settings.get.soundVolume
    // Computer running sounds are world-positioned and must attenuate with
    // distance. Relative sounds are listener-relative and bypass world range.
    relative = false
    attenuation = SoundInstance.Attenuation.LINEAR
    looping = true
    updatePosition()

    def updatePosition(): Unit = {
      blockEntity.get.foreach { entity =>
        val pos = entity.getBlockPos
        x = pos.getX + 0.5
        y = pos.getY + 0.5
        z = pos.getZ + 0.5
      }
    }

    override def canStartSilent() = true

    override def isStopped() = stopped

    // Required by ITickableSound, which is required to update position while playing
    override def tick(): Unit = if (blockEntity.get.isDefined) updatePosition() else stopSound(this)

    // Keep a hard positional cutoff even if a sound-engine implementation or
    // resource definition ignores the normal attenuation distance.
    override def getVolume(): Float = {
      val listener = Minecraft.getInstance.player
      val maxDistance = 16.0
      if (listener == null) 0f
      else {
        val distance = math.sqrt(listener.distanceToSqr(x, y, z))
        if (distance >= maxDistance) 0f
        else {
          // SoundEngine already applies one linear falloff. Apply a second,
          // gentle linear factor here so the fan fades more noticeably with
          // distance instead of staying prominent until the cutoff.
          val distanceFade = (1.0 - distance / maxDistance).toFloat
          super.getVolume() * distanceFade
        }
      }
    }

    def stop(): Unit = {
      stopped = true
      looping = false
    }
  }
}
