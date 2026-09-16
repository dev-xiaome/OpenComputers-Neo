package li.cil.oc.client.audio

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.audio._
import li.cil.oc.client.PacketHandler.PacketParser
import li.cil.oc.common.audio.Instruction
import li.cil.oc.common.audio.Instruction._
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.lwjgl.BufferUtils

import java.io.ByteArrayOutputStream
import java.util
import scala.jdk.CollectionConverters._

object AudioPacketHandler {
  private val processes = new util.HashMap[String, AudioProcess]()

  private def sampleRate: Int = {
    val rate = Settings.get.audioSampleRate
    if (rate > 0) rate else 44100
  }
  
  def setProcess(addr: String, process: AudioProcess): Unit = {
    if (process != null) processes.put(addr, process)
    else processes.remove(addr)
  }

  def readInstruction(p: PacketParser): Instruction = {
    p.readByte() match {
      case 0 => Open(p.readByte())
      case 1 => Close(p.readByte())
      case 2 => SetWave(p.readByte(), AudioType.fromIndex(p.readInt()))
      case 3 => Delay(p.readInt())
      case 4 => SetFM(p.readByte(), p.readInt(), p.readFloat())
      case 5 => ResetFM(p.readByte())
      case 6 => SetAM(p.readByte(), p.readInt())
      case 7 => ResetAM(p.readByte())
      case 8 => SetADSR(p.readByte(), p.readInt(), p.readInt(), p.readFloat(), p.readInt())
      case 9 => ResetEnvelope(p.readByte())
      case 10 => SetVolume(p.readByte(), p.readFloat())
      case 11 => SetFrequency(p.readByte(), p.readFloat())
      case 12 => SetWhiteNoise(p.readByte())
      case 13 => SetLFSR(p.readByte(), p.readInt(), p.readInt())
      case id => throw new IllegalArgumentException(s"Unknown instruction type: $id")
    }
  }

  def readData(p: PacketParser, codecId: Int): Unit = {
    val address = p.readUTF()
    val size = p.readInt()
    val buffer = new util.ArrayDeque[Instruction]()
    for (i <- 0 until size) {
      buffer.add(readInstruction(p))
    }
    if (!processes.containsKey(address)) {
      setProcess(address, new AudioProcess(Settings.get.audioChannelCount))
    }
    val process = processes.get(address)
    val data = new ByteArrayOutputStream()
    while (!buffer.isEmpty || process.delay > 0) {
      if (process.delay > 0) {
        val sampleCount = process.delay * sampleRate / 1000
        for (i <- 0 until sampleCount) {
          var sample = 0D
          for (state <- process.states.asScala) {
            sample += state.gate.getValue(process, state)
          }
          sample = Math.max(Math.min(sample, 1), -1)
          val value = (sample * 127D + process.error)
          process.error = value - Math.floor(value)
          val bvalue = Math.floor(value).toByte ^ 0x80
          data.write(bvalue.toByte)
        }
        process.delay = 0
      } else {
        val inst = buffer.poll()
        inst.encounter(process)
      }
    }
    
    if (data.size() > 0) {
      val codec = AudioManager.getPlayer(codecId)
      codec.setSampleRate(sampleRate)
      codec.push(data.toByteArray)
    }
  }
  
  def playData(p: PacketParser, codecId: Int, x: Float, y: Float, z: Float, distance: Int, volume: Byte, address: String): Unit = {
    val codec = AudioManager.getPlayer(codecId)

    codec.setHearing(distance.toFloat, ((volume & 0xFF) / 127F) * Settings.get.soundVolume)

    try {
      codec.play(s"oc:soundcard${codecId}_${address}", x, y, z, 1F)
    } catch {
      case e: NullPointerException => 
      // This exception occurs when there is no data to play, and is harmless.
    }
  }
  
  def isDimensionSame(dimId: String): Boolean = {
    Minecraft.getInstance().level.dimension().toString.equals(dimId)
  }
  
  def onSoundCardData(p: PacketParser): Unit = {
    val codecId = p.readInt()
    
    readData(p, codecId)

    val volume = p.readByte()
    
    val receiverSize = p.readInt()
    
    for (j <- 0 until receiverSize) {
      val dimId = p.readUTF()
      val x = p.readFloat()
      val y = p.readFloat()
      val z = p.readFloat()
      val distance = p.readUnsignedShort()
      val addr = p.readUTF()
      
      if (isDimensionSame(dimId)) {
        playData(p, codecId, x, y, z, distance, volume, addr)
      }
    }
  }
}