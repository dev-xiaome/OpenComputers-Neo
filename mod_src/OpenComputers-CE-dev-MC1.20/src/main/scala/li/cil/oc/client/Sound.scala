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
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.event.TickEvent.ClientTickEvent

import scala.collection.mutable
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.client.resources.sounds.{AbstractSoundInstance, SoundInstance, TickableSoundInstance}
import net.minecraft.sounds.SoundSource
import net.minecraftforge.event.level.LevelEvent

object Sound {
  private val sources = mutable.Map.empty[BlockEntity, PseudoLoopingStream]

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
        commandQueue += new StartCommand(System.currentTimeMillis() + delay, BlockEntity, name, volume)
      }
    }
  }

  def stopLoop(BlockEntity: BlockEntity): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new StopCommand(BlockEntity)
      }
    }
  }

  def updatePosition(BlockEntity: BlockEntity): Unit = {
    if (Settings.get.soundVolume > 0) {
      commandQueue.synchronized {
        commandQueue += new UpdatePositionCommand(BlockEntity)
      }
    }
  }

  @SubscribeEvent
  def onTick(e: ClientTickEvent): Unit = {
    sources.synchronized {
      updateCallable.foreach(_ ())
      updateCallable = None
    }
  }

  @SubscribeEvent
  def onWorldUnload(event: LevelEvent.Unload): Unit = {
    commandQueue.synchronized(commandQueue.clear())
    sources.synchronized(try sources.foreach(_._2.stop()) catch {
      case _: Throwable => // Ignore.
    })
    sources.clear()
  }

  private abstract class Command(val when: Long, val BlockEntity: BlockEntity) extends Ordered[Command] {
    def apply(): Unit

    override def compare(that: Command) = (that.when - when).toInt
  }

  private class StartCommand(when: Long, BlockEntity: BlockEntity, val name: String, val volume: Float) extends Command(when, BlockEntity) {
    override def apply(): Unit = {
      sources.synchronized {
        val current = sources.getOrElse(BlockEntity, null)
        if (current == null || !current.getLocation.getPath.equals(name)) {
          if (current != null) current.stop()
          val sound = new PseudoLoopingStream(BlockEntity, volume, name)
          sources(BlockEntity) = sound
          Minecraft.getInstance.getSoundManager.play(sound)
        }
      }
    }
  }

  private class StopCommand(BlockEntity: BlockEntity) extends Command(System.currentTimeMillis() + 1, BlockEntity) {
    override def apply(): Unit = {
      sources.synchronized {
        sources.remove(BlockEntity) match {
          case Some(sound) => sound.stop()
          case _ =>
        }
      }
      commandQueue.synchronized {
        // Remove all other commands for this tile entity from the queue. This
        // is inefficient, but we generally don't expect the command queue to
        // be very long, so this should be OK.
        commandQueue ++= commandQueue.dequeueAll.filter(_.BlockEntity != BlockEntity)
      }
    }
  }

  private class UpdatePositionCommand(BlockEntity: BlockEntity) extends Command(System.currentTimeMillis(), BlockEntity) {
    override def apply(): Unit = {
      sources.synchronized {
        sources.get(BlockEntity) match {
          case Some(sound) => sound.updatePosition()
          case _ =>
        }
      }
    }
  }

  private class PseudoLoopingStream(val BlockEntity: BlockEntity, val subVolume: Float, name: String)
    extends AbstractSoundInstance(ResourceLocation.fromNamespaceAndPath(OpenComputers.ID, name), SoundSource.BLOCKS, SoundInstance.createUnseededRandom()) with TickableSoundInstance {

    var stopped = false
    volume = subVolume * Settings.get.soundVolume
    //relative = BlockEntity != null
    looping = true
    attenuation = Attenuation.LINEAR
    updatePosition()

    def updatePosition(): Unit = {
      if (BlockEntity != null) {
        val pos = BlockEntity.getBlockPos
        x = pos.getX + 0.5
        y = pos.getY + 0.5
        z = pos.getZ + 0.5
      }
    }

    override def canStartSilent() = true

    override def isStopped() = stopped

    // Required by ITickableSound, which is required to update position while playing
    override def tick() = ()

    def stop(): Unit = {
      stopped = true
      looping = false
    }
  }
}
