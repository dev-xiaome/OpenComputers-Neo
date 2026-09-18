package li.cil.oc.server.component

import org.lwjgl.openal.AL10

import java.io.ByteArrayOutputStream

private[component] object AudioCardSession {
  def fromString(mode: String): (Int, Int, Int) = mode match {
    case "mono8"    => (AL10.AL_FORMAT_MONO8,    1, 1)
    case "mono16"   => (AL10.AL_FORMAT_MONO16,   1, 2)
    case "stereo8"  => (AL10.AL_FORMAT_STEREO8,  2, 1)
    case "stereo16" => (AL10.AL_FORMAT_STEREO16, 2, 2)
    case other      => throw new IllegalArgumentException(s"unknown audio mode: '$other' (valid: mono8, mono16, stereo8, stereo16)")
  }
}

private[component] final class AudioCardSession(
                                                 val handle: Int,
                                                 val channel: Int,
                                                 val sampleRate: Int,
                                                 val mode: String
                                               ) {
  val (format, channels, bytesPerSample) = AudioCardSession.fromString(mode)

  private val buffer = new ByteArrayOutputStream()

  var loop: Boolean = false
  var closed: Boolean = false

  private var playing: Boolean = false
  private var paused: Boolean = false
  private var playStartTime: Long = 0L
  private var remainingDurationMs: Long = 0L

  def size: Int = buffer.size()

  def append(data: Array[Byte]): Unit = buffer.write(data)

  def pcm: Array[Byte] = buffer.toByteArray

  def startPlayback(): Unit = {
    playing = true
    paused = false
    val totalDurationMs = (size * 1000L) / (sampleRate * channels * bytesPerSample)
    remainingDurationMs = totalDurationMs
    playStartTime = System.currentTimeMillis()
  }

  def pausePlayback(): Unit = {
    if (playing && !paused) {
      paused = true
      val elapsed = System.currentTimeMillis() - playStartTime
      remainingDurationMs = math.max(0L, remainingDurationMs - elapsed)
    }
  }

  def resumePlayback(): Unit = {
    if (playing && paused) {
      paused = false
      playStartTime = System.currentTimeMillis()
    }
  }

  def stopPlayback(): Unit = {
    playing = false
    paused = false
    remainingDurationMs = 0L
  }

  def isPlayingNow: Boolean = {
    if (!playing) false
    else if (loop) true
    else if (paused) true
    else {
      val elapsed = System.currentTimeMillis() - playStartTime
      if (elapsed >= remainingDurationMs) {
        playing = false
        false
      } else {
        true
      }
    }
  }
}