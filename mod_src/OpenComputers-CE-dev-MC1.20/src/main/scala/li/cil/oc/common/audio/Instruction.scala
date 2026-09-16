package li.cil.oc.common.audio

import li.cil.oc.api.audio.synth._
import li.cil.oc.api.audio.{AudioProcess, AudioState, AudioType}
import net.minecraft.nbt.{CompoundTag, ListTag}

import java.util

sealed trait Instruction {
  def encounter(process: AudioProcess): Unit
}

object Instruction {
  trait Ticking

  sealed trait ChannelSpecific extends Instruction {
    def channelIndex: Int

    final override def encounter(process: AudioProcess): Unit = encounter(process, process.states.get(channelIndex))

    def encounter(process: AudioProcess, state: AudioState): Unit
  }

  final case class Open(channelIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.gate = Gate.Open
      if (state.envelope != null)
        state.envelope.reset()
    }
  }

  final case class Close(channelIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.gate = Gate.Closed
    }
  }

  final case class SetWave(channelIndex: Int, wave: AudioType) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.generator = new Wave(wave)
    }
  }

  final case class Delay(delay: Int) extends Instruction with Ticking {
    override def encounter(process: AudioProcess): Unit = {
      process.delay = delay
    }
  }

  final case class SetFM(channelIndex: Int, modulatorIndex: Int, index: Float) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      if (state.isAmpMod || state.isFreqMod)
        return

      if (state.freqMod != null) {
        val mstate = process.states.get(state.freqMod.modulatorIndex)
        if (mstate != null)
          mstate.isFreqMod = false
      }

      val mstate = process.states.get(modulatorIndex)
      if (mstate != null) {
        mstate.isFreqMod = true
        state.freqMod = new FrequencyModulation(modulatorIndex, index)
      }
    }
  }

  final case class ResetFM(channelIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      if (state.freqMod == null)
        return

      val mstate = process.states.get(state.freqMod.modulatorIndex)
      if (mstate != null)
        mstate.isFreqMod = false

      state.freqMod = null
    }
  }

  final case class SetAM(channelIndex: Int, modulatorIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      if (state.isAmpMod || state.isFreqMod)
        return

      if (state.ampMod != null) {
        val mstate = process.states.get(state.ampMod.modulatorIndex)
        if (mstate != null)
          mstate.isAmpMod = false
      }

      val mstate = process.states.get(modulatorIndex)
      if (mstate != null) {
        mstate.isAmpMod = true
        state.ampMod = new AmplitudeModulation(modulatorIndex)
      }
    }
  }

  final case class ResetAM(channelIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      if (state.ampMod == null)
        return

      val mstate = process.states.get(state.ampMod.modulatorIndex)
      if (mstate != null)
        mstate.isAmpMod = false

      state.ampMod = null
    }
  }

  final case class SetADSR(channelIndex: Int, attackDuration: Int, decayDuration: Int, attenuation: Float, releaseDuration: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      val envelope = new ADSR(attackDuration, decayDuration, attenuation, releaseDuration)
      if (state.envelope != null) {
        envelope.progress = state.envelope.progress
        envelope.phase = state.envelope.phase
      }
      state.envelope = envelope
    }
  }

  final case class ResetEnvelope(channelIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.envelope = null
    }
  }

  final case class SetVolume(channelIndex: Int, volume: Float) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.volume = Math.max(0f, Math.min(volume, 1f))
    }
  }

  final case class SetFrequency(channelIndex: Int, frequency: Float) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.frequencyInHz = frequency
    }
  }

  final case class SetWhiteNoise(channelIndex: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.generator = new WhiteNoise()
    }
  }

  final case class SetLFSR(channelIndex: Int, initial: Int, mask: Int) extends ChannelSpecific {
    override def encounter(process: AudioProcess, state: AudioState): Unit = {
      state.generator = new LFSR(initial, mask)
    }
  }

  def load(tag: CompoundTag): Option[Instruction] = {
    tag.getByte("t") match {
      case 0 =>
        Some(Open(tag.getByte("c")))
      case 1 =>
        Some(Close(tag.getByte("c")))
      case 2 =>
        Some(
          SetWave(
            tag.getByte("c"),
            AudioType.fromIndex(tag.getByte("w"))
          )
        )
      case 3 =>
        Some(Delay(tag.getInt("d")))
      case 4 =>
        Some(
          SetFM(
            tag.getByte("c"),
            tag.getInt("m"),
            tag.getFloat("i")
          )
        )
      case 5 =>
        Some(ResetFM(tag.getByte("c")))
      case 6 =>
        Some(
          SetAM(
            tag.getByte("c"),
            tag.getInt("m")
          )
        )
      case 7 =>
        Some(ResetAM(tag.getByte("c")))
      case 8 =>
        Some(
          SetADSR(
            tag.getByte("c"),
            tag.getInt("a"),
            tag.getInt("d"),
            tag.getFloat("s"),
            tag.getInt("r")
          )
        )
      case 9 =>
        Some(ResetEnvelope(tag.getByte("c")))
      case 10 =>
        Some(
          SetVolume(
            tag.getByte("c"),
            tag.getFloat("v")
          )
        )
      case 11 =>
        Some(
          SetFrequency(
            tag.getByte("c"),
            tag.getFloat("f")
          )
        )
      case 12 =>
        Some(SetWhiteNoise(tag.getByte("c")))
      case 13 =>
        Some(
          SetLFSR(
            tag.getByte("c"),
            tag.getInt("i"),
            tag.getInt("m")
          )
        )
      case _ =>
        None
    }
  }

  def fromTag(list: ListTag): util.Queue[Instruction] = {
    val queue = new util.ArrayDeque[Instruction]()

    var i = 0
    while (i < list.size()) {
      val tag = list.getCompound(i)

      if (!tag.isEmpty) {
        load(tag).foreach(queue.add)
      }

      i += 1
    }

    queue
  }

  def toTag(list: ListTag, instructions: util.Queue[Instruction]): Unit = {
    val iterator = instructions.iterator()

    while (iterator.hasNext) {
      val tag = new CompoundTag()
      save(tag, iterator.next())
      list.add(tag)
    }
  }

  def save(tag: CompoundTag, inst: Instruction): Unit = {
    inst match {
      case Open(channel) =>
        tag.putByte("t", 0)
        tag.putByte("c", channel.toByte)
      case Close(channel) =>
        tag.putByte("t", 1)
        tag.putByte("c", channel.toByte)
      case SetWave(channel, wave) =>
        tag.putByte("t", 2)
        tag.putByte("c", channel.toByte)
        tag.putInt("w", wave.ordinal())
      case Delay(delay) =>
        tag.putByte("t", 3)
        tag.putInt("d", delay)
      case SetFM(channel, modulatorIndex, index) =>
        tag.putByte("t", 4)
        tag.putByte("c", channel.toByte)
        tag.putInt("m", modulatorIndex)
        tag.putFloat("i", index)
      case ResetFM(channel) =>
        tag.putByte("t", 5)
        tag.putByte("c", channel.toByte)
      case SetAM(channel, modulatorIndex) =>
        tag.putByte("t", 6)
        tag.putByte("c", channel.toByte)
        tag.putInt("m", modulatorIndex)
      case ResetAM(channel) =>
        tag.putByte("t", 7)
        tag.putByte("c", channel.toByte)
      case SetADSR(channel, attack, decay, attenuation, release) =>
        tag.putByte("t", 8)
        tag.putByte("c", channel.toByte)
        tag.putInt("a", attack)
        tag.putInt("d", decay)
        tag.putFloat("s", attenuation)
        tag.putInt("r", release)
      case ResetEnvelope(channel) =>
        tag.putByte("t", 9)
        tag.putByte("c", channel.toByte)
      case SetVolume(channel, volume) =>
        tag.putByte("t", 10)
        tag.putByte("c", channel.toByte)
        tag.putFloat("v", volume)
      case SetFrequency(channel, frequency) =>
        tag.putByte("t", 11)
        tag.putByte("c", channel.toByte)
        tag.putFloat("f", frequency)
      case SetWhiteNoise(channel) =>
        tag.putByte("t", 12)
        tag.putByte("c", channel.toByte)
      case SetLFSR(channel, initial, mask) =>
        tag.putByte("t", 13)
        tag.putByte("c", channel.toByte)
        tag.putInt("i", initial)
        tag.putInt("m", mask)
    }
  }
}