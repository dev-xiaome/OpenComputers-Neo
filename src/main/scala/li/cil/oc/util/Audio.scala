package li.cil.oc.util

import java.nio.ByteBuffer

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.{SoundEvents, SoundSource}
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.common.NeoForge
import org.lwjgl.BufferUtils
import org.lwjgl.openal.{AL10, ALC10}

import scala.collection.mutable

/**
 * This class contains the logic used by computers' internal "speakers".
 * It can generate square waves with a specific frequency and duration
 * and will play them through OpenAL, acquiring sources as necessary.
 * Tones that have finished playing are disposed automatically in the
 * tick handler.
 *
 * 1.21.1 迁移要点：
 *  - `Minecraft.getMinecraft` → `Minecraft.getInstance()`，`thePlayer` → `player`
 *  - `gameSettings.getSoundLevel(SoundCategory.BLOCKS)` → `options.getSoundSourceVolume(SoundSource.BLOCKS)`
 *  - `PositionedSoundRecord` → `SimpleSoundInstance.forUI(...)`；`getSoundHandler` → `getSoundManager`，
 *    `playDelayedSound` → `playDelayed`
 *  - 事件注册：`FMLCommonHandler.instance.bus.register(this)` + `TickEvent.ClientTickEvent`
 *    → `NeoForge.EVENT_BUS.register(this)` + `ClientTickEvent.Post`
 *  - 距离衰减用 `player.distanceTo(x, y, z)` 保持原有的线性衰减语义
 *
 * TODO(音频): OpenAL 上下文在 1.21.1 由 Minecraft 音响系统托管；若运行期出现
 * 上下文冲突，应改为接入 `net.minecraft.client.sounds.SoundEngine` 而非直接操作 AL。
 */
object Audio {
  private def sampleRate = Settings.get.beepSampleRate

  private def amplitude = Settings.get.beepAmplitude

  private def maxDistance = Settings.get.beepRadius

  private val sources = mutable.Set.empty[Source]

  private def volume: Float = {
    val mc = Minecraft.getInstance
    if (mc == null) 0f else mc.options.getSoundSourceVolume(SoundSource.BLOCKS)
  }

  private var disableAudio = false

  /**
   * 当前线程是否已绑定可用的 OpenAL 上下文。
   * <br>
   * LWJGL 3 已移除 `AL.isCreated`（`AL` 现在只是无状态的绑定层，是否“已创建”要看
   * 上下文）。1.21.1 里上下文由 Minecraft 的音响系统创建，这里用
   * `ALC10.alcGetCurrentContext()` 判定；OpenAL 未加载时（`UnsatisfiedLinkError`）
   * 视作不可用。
   */
  private def isALCreated: Boolean =
    try ALC10.alcGetCurrentContext() != 0L
    catch { case _: Throwable => false }

  def play(x: Float, y: Float, z: Float, frequencyInHz: Int, durationInMilliseconds: Int): Unit = {
    play(x, y, z, ".", frequencyInHz, durationInMilliseconds)
  }

  def play(x: Float, y: Float, z: Float, pattern: String, frequencyInHz: Int = 1000, durationInMilliseconds: Int = 200): Unit = {
    val mc = Minecraft.getInstance
    if (mc == null || mc.player == null) return
    // 1.21.1 的 Entity 只提供平方距离，这里开方以保持原有的线性衰减。
    val distance = math.sqrt(mc.player.distanceToSqr(x, y, z))
    val distanceBasedGain = math.max(0, 1 - distance / maxDistance).toFloat
    val gain = distanceBasedGain * volume
    if (gain <= 0 || amplitude <= 0) return

    if (disableAudio) {
      // Fallback audio generation, using built-in Minecraft sound. This can be
      // necessary on certain systems with audio cards that do not have enough
      // memory. May still fail, but at least we can say we tried!
      // Valid range is 20-2000Hz, clamp it to that and get a relative value.
      // MC's pitch system supports a minimum pitch of 0.5, however, so up it
      // by that.
      val clampedFrequency = ((frequencyInHz - 20) max 0 min 1980) / 1980f + 0.5f
      var delay = 0
      for (ch <- pattern) {
        val record = SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), gain, clampedFrequency)
        if (delay == 0) mc.getSoundManager.play(record)
        else mc.getSoundManager.playDelayed(record, delay)
        delay += ((if (ch == '.') durationInMilliseconds else 2 * durationInMilliseconds) * 20 / 1000) max 1
      }
    }
    else {
      if (isALCreated) {
        val sampleCounts = pattern.toCharArray.
          map(ch => if (ch == '.') durationInMilliseconds else 2 * durationInMilliseconds).
          map(_ * sampleRate / 1000)
        // 50ms pause between pattern parts.
        val pauseSampleCount = 50 * sampleRate / 1000
        val data = BufferUtils.createByteBuffer(sampleCounts.sum + (sampleCounts.length - 1) * pauseSampleCount)
        val step = frequencyInHz / sampleRate.toFloat
        var offset = 0f
        for (sampleCount <- sampleCounts) {
          for (sample <- 0 until sampleCount) {
            val angle = 2 * math.Pi * offset
            val value = (math.signum(math.sin(angle)) * amplitude).toByte ^ 0x80
            offset += step
            if (offset > 1) offset -= 1
            data.put(value.toByte)
          }
          if (data.hasRemaining) {
            for (sample <- 0 until pauseSampleCount) {
              data.put(127: Byte)
            }
          }
        }
        data.rewind()

        // Watch out for sound cards running out of memory... this apparently
        // really does happen. I'm assuming this is due to too many sounds being
        // kept loaded, since from what I can see OC's releasing its audio
        // memory as it should.
        try sources.synchronized(sources += new Source(x, y, z, data, gain)) catch {
          case e: LessUselessOpenALException =>
            if (e.errorCode == AL10.AL_OUT_OF_MEMORY) {
              // Well... let's just stop here.
              OpenComputers.log.info("Couldn't play computer speaker sound because your sound card ran out of memory. Either your sound card is just really low-end, or there are just too many sounds in use already by other mods. Disabling computer speakers to avoid spamming your log file now.")
              disableAudio = true
            }
            else {
              OpenComputers.log.warn("Error playing computer speaker sound.", e)
            }
        }
      }
    }
  }

  def update(): Unit = {
    if (!disableAudio) {
      sources.synchronized(sources --= sources.filter(_.checkFinished))

      // Clear error stack.
      if (isALCreated) {
        try AL10.alGetError() catch {
          case _: UnsatisfiedLinkError =>
            OpenComputers.log.warn("Negotiations with OpenAL broke down, disabling sounds.")
            disableAudio = true
        }
      }
    }
  }

  private class Source(val x: Float, y: Float, z: Float, val data: ByteBuffer, val gain: Float) {
    // Clear error stack.
    AL10.alGetError()

    val (source, buffer) = {
      val buffer = AL10.alGenBuffers()
      checkALError()

      try {
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO8, data, sampleRate)
        checkALError()

        val source = AL10.alGenSources()
        checkALError()

        try {
          AL10.alSourceQueueBuffers(source, buffer)
          checkALError()

          AL10.alSource3f(source, AL10.AL_POSITION, x, y, z)
          AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, maxDistance)
          AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, maxDistance)
          AL10.alSourcef(source, AL10.AL_GAIN, gain * 0.3f)
          checkALError()

          AL10.alSourcePlay(source)
          checkALError()

          (source, buffer)
        }
        catch {
          case t: Throwable =>
            AL10.alDeleteSources(source)
            throw t
        }
      }
      catch {
        case t: Throwable =>
          AL10.alDeleteBuffers(buffer)
          throw t
      }
    }

    def checkFinished: Boolean = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && {
      AL10.alDeleteSources(source)
      AL10.alDeleteBuffers(buffer)
      true
    }
  }

  // Having the error code in an accessible way is really cool, you know.
  // 1.21.1 的 LWJGL 3 已移除 `org.lwjgl.openal.OpenALException`，改为继承 `RuntimeException`，
  // 这样既能 `throw`，又能通过 `errorCode` 读取具体的 AL 错误码。
  class LessUselessOpenALException(val errorCode: Int)
    extends RuntimeException(s"OpenAL error code: $errorCode")

  // Custom implementation of Util.checkALError() that uses our custom exception.
  def checkALError(): Unit = {
    val errorCode = AL10.alGetError()
    if (errorCode != AL10.AL_NO_ERROR) {
      throw new LessUselessOpenALException(errorCode)
    }
  }

  NeoForge.EVENT_BUS.register(this)

  @SubscribeEvent
  def onTick(e: ClientTickEvent.Post): Unit = {
    update()
  }
}
