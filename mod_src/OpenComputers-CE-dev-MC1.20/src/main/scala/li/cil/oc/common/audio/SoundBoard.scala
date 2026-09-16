package li.cil.oc.common.audio

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.audio.{AudioHost, AudioManager, AudioProcess, AudioReceiver, AudioType}
import li.cil.oc.client.audio.AudioPacketHandler
import li.cil.oc.common.audio.Instruction._
import li.cil.oc.server.PacketSender
import net.minecraft.nbt.{CompoundTag, ListTag, Tag}
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.level.{ChunkEvent, LevelEvent}
import net.minecraftforge.eventbus.api.SubscribeEvent

import java.util
import scala.jdk.CollectionConverters._

class SoundBoard(private val host: AudioHost) {
  val process = new AudioProcess(Settings.get.audioChannelCount)
  
  private var buildBuffer: util.ArrayDeque[Instruction] = null
  private var nextBuffer: util.ArrayDeque[Instruction] = null
 
  private var buildDelay = 0
  private var nextDelay = 0
  private var timeout = System.currentTimeMillis()
  private var soundVolume = 127
  var codecId: Integer = null
  private var clientAddress: String = null

  private val soundTimeoutMillis = 250
  
  private var bufferInit = false

  initBuffers()
  
  private def initBuffers(): Unit = {
    if (bufferInit) return
    val level = host.level()
    if (level == null) return
    if (level.isClientSide) {
      SoundBoard.envs.add(this)
      buildBuffer = null
      nextBuffer = null
      if (clientAddress != null) {
        AudioPacketHandler.setProcess(clientAddress, process)
      }
    } else {
      buildBuffer = new util.ArrayDeque[Instruction]()
      nextBuffer = new util.ArrayDeque[Instruction]()
    }
    bufferInit = true
  }
  
  private var dirty = false
  
  def update(): Unit = {
    initBuffers()
    if (!host.level().isClientSide) {
      if (nextBuffer != null && !nextBuffer.isEmpty && System.currentTimeMillis() >= timeout - 100) {
        var clone: util.ArrayDeque[Instruction] = null
        nextBuffer.synchronized {
          clone = nextBuffer.clone()
          timeout = timeout + nextDelay
          nextBuffer.clear()
        }
        sendSound(clone)
        dirty = true
      } else if (codecId != null && System.currentTimeMillis() >= timeout + soundTimeoutMillis) {
        AudioManager.removePlayer(codecId)
        codecId = null
      }
      if (dirty) {
        host.setChanged()
      }
    }
  }
  
  def load(nbt: CompoundTag): Unit = {
    if (nbt.contains("process")) {
      process.load(nbt.getCompound("process"))
    }
    if (nbt.contains("node")) {
      val nodeTag = nbt.getCompound("node")
      if (nodeTag.contains("address")) {
        clientAddress = nodeTag.getString("address")
      }
    }
    if (nbt.contains("bbuffer")) {
      if (buildBuffer != null) {
        buildDelay.synchronized {
          buildBuffer.clear()
          buildBuffer.addAll(Instruction.fromTag(nbt.getList("bbuffer", Tag.TAG_COMPOUND)))
          buildDelay = 0
          buildBuffer.forEach {
            case d: Delay => buildDelay += d.delay
            case _ =>
          }
        }
      }
    }
    if (nbt.contains("nbuffer")) {
      if (nextBuffer != null) {
        nextBuffer.synchronized {
          nextBuffer.clear()
          nextBuffer.addAll(Instruction.fromTag(nbt.getList("nbuffer", Tag.TAG_COMPOUND)))
          nextDelay = 0
          nextBuffer.forEach {
            case d: Delay => nextDelay += d.delay
            case _ =>
          }
        }
      }
    }
    if (nbt.contains("volume")) {
      soundVolume = nbt.getByte("volume")
    }
  }
  
  def save(nbt: CompoundTag): Unit = {
    val processTag = new CompoundTag()
    nbt.put("process", processTag)
    process.save(processTag)
    if (buildBuffer != null && !buildBuffer.isEmpty) {
      val buildTag = new ListTag()
      buildBuffer.synchronized {
        Instruction.toTag(buildTag, buildBuffer)
      }
      nbt.put("bbuffer", buildTag)
    }
    if (nextBuffer != null && !nextBuffer.isEmpty) {
      val nextTag = new ListTag()
      nextBuffer.synchronized {
        Instruction.toTag(nextTag, nextBuffer)
      }
      nbt.put("nbuffer", nextTag)
    }
    nbt.putByte("volume", soundVolume.toByte)
  }
  
  def clearAndStop(): Unit = {
    if (buildBuffer != null && !buildBuffer.isEmpty) {
      buildBuffer.synchronized {
        buildBuffer.clear()
      }
    }
    if (nextBuffer != null && !nextBuffer.isEmpty) {
      nextBuffer.synchronized {
        nextBuffer.clear()
      }
    }
    buildDelay = 0
    if (codecId != null) {
      AudioManager.removePlayer(codecId)
      codecId = null
    }
    dirty = true
  }
  
  def tryAdd(inst: Instruction): Array[AnyRef] = {
    buildBuffer.synchronized {
      if (buildBuffer.size() >= Settings.get.audioQueueSize) {
        return Array[AnyRef](Boolean.box(false), "too many instructions")
      }
      buildBuffer.add(inst)
    }
    dirty = true
    Array[AnyRef](Boolean.box(true))
  }
  
  def checkChannel(ch: Int): Int = {
    val channel = ch - 1
    if (channel >= 0 && channel < process.states.size()) {
      return channel
    }
    throw new IllegalArgumentException(s"invalid channel: ${channel + 1}")
  }
  
  def clear(): Unit = {
    buildBuffer.synchronized {
      buildBuffer.clear()
    }
    buildDelay = 0
    dirty = true
  }
  
  def delay(duration: Int): Array[AnyRef] = {
    if (duration < 0 || duration > Settings.get.audioMaxDelay) {
      throw new IllegalArgumentException(s"invalid duration. must be between 0 and ${Settings.get.audioMaxDelay}")
    }
    if (buildDelay + duration > Settings.get.audioMaxDelay) {
      return Array[AnyRef](Boolean.box(false), "too many delays in queue")
    }
    buildDelay += duration
    tryAdd(Delay(duration))
  }
  
  def setTotalVolume(volume: Double): Unit = {
    soundVolume = Mth.floor(Mth.clamp(volume, 0.0, 1.0) * 127.0F)
  }
  
  def setWave(ch: Int, m: Int): Array[AnyRef] = {
    val channel = checkChannel(ch)
    val mode = m - 1
    mode match {
      case -2 => return tryAdd(SetWhiteNoise(channel))
      case _ => if (mode >= 0 && mode < AudioType.values().length) {
        return tryAdd(SetWave(channel, AudioType.fromIndex(mode)))
      }
    }
    throw new IllegalArgumentException(s"invalid mode: ${mode + 1}")
  }
  
  def doProcess(): Array[AnyRef] = {
    buildBuffer.synchronized {
      if (nextBuffer != null && nextBuffer.isEmpty) {
        if (buildBuffer.size() == 0) {
          return Array[AnyRef](Boolean.box(true))
        }
        if (!host.tryChangeBuffer(-Settings.get.audioEnergyCost * (buildDelay / 1000D))) {
          return Array[AnyRef](Boolean.box(false), "not enough energy")
        }
        nextBuffer.synchronized {
          nextBuffer.addAll(new util.ArrayDeque[Instruction](buildBuffer))
        }
        nextDelay = buildDelay
        buildBuffer.clear()
        buildDelay = 0
        if (System.currentTimeMillis() > timeout) {
          timeout = System.currentTimeMillis()
        }
        dirty = true
        return Array[AnyRef](Boolean.box(true))
      } else {
        return Array[AnyRef](Boolean.box(false), Long.box(System.currentTimeMillis() - timeout))
      }
    }
  }
  
  def sendMusicPacket(instructions: util.Queue[Instruction]): Unit = {
    if (codecId == null) {
      codecId = AudioManager.newPlayer()
      AudioManager.getPlayer(codecId)
    }
    PacketSender.sendSoundCardData(host, host.address(), soundVolume.toByte, host.getReceivers, instructions)
  }
  
  def sendSound(buffer: util.Queue[Instruction]): Unit = {
    val sendBuffer = new util.ArrayDeque[Instruction]()
    while (!buffer.isEmpty || process.delay > 0) {
      if (process.delay > 0) {
        process.delay = 0
      } else {
        val inst = buffer.poll()
        inst.encounter(process)
        sendBuffer.add(inst)
      }
    }
    if (sendBuffer.size() > 0) {
      sendMusicPacket(sendBuffer)
    }
  }
}

object SoundBoard {
  private var modes: util.Map[AnyRef, AnyRef] = null
  val envs: util.Set[SoundBoard] = util.Collections.newSetFromMap(new util.WeakHashMap[SoundBoard, java.lang.Boolean]())

  def compileModes: util.Map[AnyRef, AnyRef] = {
    if (modes == null) {
      val m = new util.HashMap[AnyRef, AnyRef](AudioType.values().length * 2 + 2)
      for (value <- AudioType.values()) {
        val name = value.name().toLowerCase(util.Locale.ENGLISH)
        m.put(Int.box(value.ordinal() + 1), name)
        m.put(name, Int.box(value.ordinal() + 1))
      }
      // White noise doesn't have an AudioType entry; it's addressed with index -1, like the original.
      m.put("noise", Int.box(-1))
      m.put(Int.box(-1), "noise")
      modes = m
    }
    modes
  }

  @SubscribeEvent
  def onChunkUnload(event: ChunkEvent.Unload): Unit = {
    envs.forEach(env => {
      val pos = env.host.position()
      val chunkPos = event.getChunk.getPos
      if (env.host.level() == event.getLevel && chunkPos.x == Mth.floor(pos.x) >> 4 && chunkPos.z == Mth.floor(pos.z) >> 4) {
        AudioPacketHandler.setProcess(env.clientAddress, null)
      }
    })
  }
  
  @SubscribeEvent
  def onLevelUnload(event: LevelEvent.Unload): Unit = {
    envs.forEach(env => {
      if (env.host.level() == event.getLevel) {
        AudioPacketHandler.setProcess(env.clientAddress, null)
      }
    })
  }
}