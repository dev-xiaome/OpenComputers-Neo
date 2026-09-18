package li.cil.oc.server.component

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{EnvironmentHost, Message, Node, Visibility}
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.server.PacketSender
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentHolder

import scala.collection.mutable
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.IntArrayTag
import net.neoforged.neoforge.common.MutableDataComponentHolder

import scala.jdk.CollectionConverters._

object AudioCard {
  // Audio packets are keyed only by handle on the client. Keep this process
  // wide: per-card counters cause two nearby cards to overwrite each other.
  private val nextNetworkHandle = new AtomicInteger(1)
  private def allocateHandle(): Int = nextNetworkHandle.getAndIncrement()

  /** Sends a completed mono8 stream for a block device such as a tape drive. */
  def playPcm(host: EnvironmentHost, pcm: Array[Byte], sampleRate: Int = 12000, packetSize: Int = -1): Option[Int] = {
    if (!Settings.get.audioCardEnablePcm || pcm.isEmpty) return None
    val handle = allocateHandle()
    // Computronics 1.12 sent 1024 DFPWM bytes per packet.  The client decoded
    // each packet to 8192 MONO8 PCM bytes and queued that complete decoded
    // packet as one OpenAL buffer.  TapeDrive passes 1024 here as the encoded
    // packet size, so preserve that historical decoded-buffer boundary.
    val chunkSize = math.max(1, if (packetSize > 0) packetSize * 8 else Settings.get.audioCardChunkSize)
    PacketSender.sendAudioStart(host, handle, 0, sampleRate.max(1).min(96000), 1, org.lwjgl.openal.AL10.AL_FORMAT_MONO8, false)
    for (offset <- pcm.indices by chunkSize) PacketSender.sendAudioChunk(host, handle, pcm.slice(offset, math.min(pcm.length, offset + chunkSize)))
    PacketSender.sendAudioPlay(host, handle)
    Some(handle)
  }

  /** Send the original encoded packets and let the client follow AsieLib's decode path. */
  def playDfpwm(host: EnvironmentHost, encoded: Array[Byte], sampleRate: Int, volume: Float): Option[Int] = {
    if (!Settings.get.audioCardEnablePcm || encoded.isEmpty) return None
    val handle = allocateHandle()
    PacketSender.sendTapeAudioStart(host, handle, sampleRate.max(1).min(96000), volume.max(0f).min(1f))
    for (offset <- encoded.indices by 1024)
      PacketSender.sendAudioChunk(host, handle, encoded.slice(offset, math.min(encoded.length, offset + 1024)))
    PacketSender.sendAudioPlay(host, handle)
    Some(handle)
  }
}

class AudioCard(private val host: EnvironmentHost) extends AbstractManagedEnvironment with DeviceInfo {
  override val node: Node = Network.newNode(this, Visibility.Neighbors)
    .withComponent("audio")
    .withConnector()
    .create()

  private val owners = mutable.Map.empty[String, mutable.Set[Int]]
  private val sessions = mutable.Map.empty[Int, AudioCardSession]
  private val synthChannels = Array.fill(8)(new SynthChannel)
  private val synthModes = Array("square", "sine", "triangle", "sawtooth", "noise")
  private var totalSynthVolume = 1.0

  private final class SynthChannel {
    var mode = 0
    var frequency = 440
    var volume = 1.0
    var fmChannel = -1
    var fmIntensity = 0.0
    var amChannel = -1
    var attack = 0
    var decay = 0
    var sustain = 1.0
    var release = 0
  }

  private def synthChannel(index: Int): SynthChannel = {
    if (index < 1 || index > synthChannels.length) throw new IllegalArgumentException("channel must be in [1, 8]")
    synthChannels(index - 1)
  }

  private def playSynth(channel: Int, duration: Int, delay: Int = 0): Unit = {
    val c = synthChannel(channel)
    val fm = if (c.fmChannel >= 0) synthChannels(c.fmChannel).frequency else 0
    val am = if (c.amChannel >= 0) synthChannels(c.amChannel).frequency else 0
    val level = host.getEnvironmentLevel
    if (level != null && !level.isClientSide)
      PacketSender.sendComputronicsTone(level, host.xPosition, host.yPosition, host.zPosition, c.mode,
        c.frequency.max(20).min(2000), duration.max(50).min(5000), delay.max(0).min(16000), c.volume * totalSynthVolume,
        fm, c.fmIntensity, am, c.attack, c.decay, c.sustain, c.release)
  }

  private def chunkSize: Int = math.max(1, Settings.get.audioCardChunkSize)
  private def bufferLimit: Int = math.max(chunkSize, Settings.get.audioCardBufferLimit)
  private def defaultSampleRate: Int = Settings.get.audioCardSampleRate

  private def nextId(): Int = AudioCard.allocateHandle()

  private def session(handle: Int): AudioCardSession =
    sessions.getOrElse(handle, throw new IllegalArgumentException("invalid handle"))

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Multimedia,
    DeviceAttribute.Description -> "Audio Streaming Interface",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.ViridiaComputronics,
    DeviceAttribute.Product -> "WaveBlaster Zero"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @Callback(direct = true, doc = """function([channel:number, sampleRate:number, mode:string]):userdata -- open an audio buffer handle.
  - channel: output channel index (default: 0)
  - sampleRate: samples per second (default: server config value)
  - mode: PCM format string (default: "mono8")
      "mono8"    -- mono,   8-bit unsigned  (DFPWM output)
      "mono16"   -- mono,   16-bit signed little-endian
      "stereo8"  -- stereo, 8-bit unsigned
      "stereo16" -- stereo, 16-bit signed little-endian (WAV stereo)
  """)
  def open(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    if (!Settings.get.audioCardEnablePcm) return result(null, "PCM streaming is disabled by the server")
    if (owners.get(context.node.address).fold(false)(_.size >= Settings.get.maxHandles)) {
      throw new IOException("too many open handles")
    }
    val channel = args.optInteger(0, 0)
    val sampleRate = args.optInteger(1, defaultSampleRate)
    val mode = args.optString(2, "mono8")
    val handle = nextId()

    sessions(handle) = new AudioCardSession(handle, channel, sampleRate, mode)
    owners.getOrElseUpdate(context.node.address, mutable.Set.empty[Int]) += handle

    result(new AudioHandleValue(node.address, handle))
  }

  // The synthesis API deliberately lives on the existing `audio` component.
  // OpenOS ships a `sound` compatibility module that maps the legacy names.
  @Callback(direct = true, doc = "function():table -- available synthesizer wave modes.")
  def modes(context: Context, args: Arguments): Array[AnyRef] = {
    val values = mutable.Map.empty[Any, Any]
    synthModes.zipWithIndex.foreach { case (name, index) => values(index + 1) = name; values(name) = index + 1 }
    result(values.toMap)
  }

  @Callback(direct = true, doc = "function():number -- number of synthesizer channels.")
  def channel_count(context: Context, args: Arguments): Array[AnyRef] = result(synthChannels.length)

  @Callback(direct = true, doc = "function(volume:number):boolean -- set overall synthesizer volume.")
  def setTotalVolume(context: Context, args: Arguments): Array[AnyRef] = { totalSynthVolume = args.checkDouble(0).max(0).min(1); result(true) }

  @Callback(doc = "function(channel:number, wave:number):boolean -- set a synthesizer wave.")
  def setWave(context: Context, args: Arguments): Array[AnyRef] = {
    val c = synthChannel(args.checkInteger(0)); val wave = args.checkInteger(1) - 1
    if (wave < 0 || wave >= synthModes.length) throw new IllegalArgumentException("unknown wave mode")
    c.mode = wave; result(true)
  }

  @Callback(doc = "function(channel:number, initial:number, mask:number):boolean -- configure legacy LFSR noise.")
  def setLFSR(context: Context, args: Arguments): Array[AnyRef] = { synthChannel(args.checkInteger(0)).mode = 4; result(true) }

  @Callback(doc = "function(channel:number, frequency:number):boolean -- set a synthesizer frequency.")
  def setFrequency(context: Context, args: Arguments): Array[AnyRef] = { synthChannel(args.checkInteger(0)).frequency = args.checkInteger(1).max(20).min(2000); result(true) }

  @Callback(doc = "function(channel:number, volume:number):boolean -- set a synthesizer channel volume.")
  def setVolume(context: Context, args: Arguments): Array[AnyRef] = { synthChannel(args.checkInteger(0)).volume = args.checkDouble(1).max(0).min(1); result(true) }

  @Callback(doc = "function(channel:number, attack:number, decay:number, sustain:number, release:number):boolean -- set ADSR in milliseconds.")
  def setADSR(context: Context, args: Arguments): Array[AnyRef] = { val c = synthChannel(args.checkInteger(0)); c.attack = args.checkInteger(1).max(0).min(5000); c.decay = args.checkInteger(2).max(0).min(5000); c.sustain = args.checkDouble(3).max(0).min(1); c.release = args.checkInteger(4).max(0).min(5000); result(true) }

  @Callback(doc = "function(channel:number):boolean -- reset ADSR envelope.")
  def resetEnvelope(context: Context, args: Arguments): Array[AnyRef] = { val c = synthChannel(args.checkInteger(0)); c.attack = 0; c.decay = 0; c.sustain = 1; c.release = 0; result(true) }

  @Callback(doc = "function(channel:number, modulator:number, intensity:number):boolean -- set frequency modulation.")
  def setFM(context: Context, args: Arguments): Array[AnyRef] = { val c = synthChannel(args.checkInteger(0)); c.fmChannel = args.checkInteger(1) - 1; synthChannel(c.fmChannel + 1); c.fmIntensity = args.checkDouble(2); result(true) }

  @Callback(doc = "function(channel:number):boolean -- disable frequency modulation.")
  def resetFM(context: Context, args: Arguments): Array[AnyRef] = { synthChannel(args.checkInteger(0)).fmChannel = -1; result(true) }

  @Callback(doc = "function(channel:number, modulator:number):boolean -- set amplitude modulation.")
  def setAM(context: Context, args: Arguments): Array[AnyRef] = { val c = synthChannel(args.checkInteger(0)); c.amChannel = args.checkInteger(1) - 1; synthChannel(c.amChannel + 1); result(true) }

  @Callback(doc = "function(channel:number):boolean -- disable amplitude modulation.")
  def resetAM(context: Context, args: Arguments): Array[AnyRef] = { synthChannel(args.checkInteger(0)).amChannel = -1; result(true) }

  @Callback(doc = "function(channel:number, duration:number[, delay:number]):boolean -- play one synthesized channel; durations are milliseconds.")
  def playSynthesized(context: Context, args: Arguments): Array[AnyRef] = { playSynth(args.checkInteger(0), args.checkInteger(1), args.optInteger(2, 0)); result(true) }

  @Callback(doc = "function(table):boolean -- play up to eight frequency-duration beep pairs.")
  def beep(context: Context, args: Arguments): Array[AnyRef] = {
    val entries = args.checkTable(0).asScala.toSeq
    if (entries.size > 8) return result(false, "table must not contain more than 8 frequencies")
    entries.zipWithIndex.foreach { case ((frequency: Number, duration: Number), index) =>
      val c = synthChannels(index); c.frequency = frequency.intValue().max(20).min(2000); c.mode = 0; c.volume = 1
      playSynth(index + 1, (duration.doubleValue() * 1000).toInt)
    }
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata, pcm:string):boolean -- append raw PCM bytes to the handle buffer.")
  def send(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    val data = args.checkByteArray(1)
    checkOwner(context.node.address, handle)

    val s = session(handle)
    if (s.isPlayingNow) return result(null, "already playing")
    if (s.closed) return result(null, "handle closed")
    if (s.size + data.length > bufferLimit) return result(null, "buffer full")

    s.append(data)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata):boolean -- flush buffer to clients and start playback.")
  def play(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    checkOwner(context.node.address, handle)
    val s = session(handle)

    if (s.closed) return result(null, "handle closed")
    if (s.size == 0) return result(null, "buffer empty")

    s.startPlayback()

    PacketSender.sendAudioStart(host, handle, s.channel, s.sampleRate, s.channels, s.format, s.loop)

    val pcm = s.pcm
    var off = 0
    while (off < pcm.length) {
      val len = math.min(chunkSize, pcm.length - off)
      PacketSender.sendAudioChunk(host, handle, pcm.slice(off, off + len))
      off += len
    }

    PacketSender.sendAudioPlay(host, handle)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata):boolean -- pause playback if active.")
  def pause(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    checkOwner(context.node.address, handle)
    val s = session(handle)
    if (s.closed) return result(null, "handle closed")

    s.pausePlayback()
    PacketSender.sendAudioPause(host, handle)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata):boolean -- resume playback if paused.")
  def resume(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    checkOwner(context.node.address, handle)
    val s = session(handle)
    if (s.closed) return result(null, "handle closed")

    s.resumePlayback()
    PacketSender.sendAudioResume(host, handle)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata):boolean -- stop playback.")
  def stop(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    checkOwner(context.node.address, handle)
    val s = session(handle)
    if (s.closed) return result(null, "handle closed")

    s.stopPlayback()
    PacketSender.sendAudioStop(host, handle)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata, loop:boolean):boolean -- set loop mode.")
  def setLoop(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    val loop = args.checkBoolean(1)
    checkOwner(context.node.address, handle)
    val s = session(handle)
    if (s.closed) return result(null, "handle closed")

    s.loop = loop
    PacketSender.sendAudioSetLoop(host, handle, loop)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata):boolean -- close and dispose handle.")
  def close(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    closeHandle(context.node.address, handle)
    result(true)
  }

  @Callback(direct = true, doc = "function(handle:userdata):number -- get current buffer size.")
  def size(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    checkOwner(context.node.address, handle)
    result(session(handle).size)
  }

  @Callback(direct = true, doc = "function(handle:userdata):boolean -- check if audio is currently playing.")
  def isPlaying(context: Context, args: Arguments): Array[AnyRef] = synchronized {
    val handle = checkHandle(args, 0)
    checkOwner(context.node.address, handle)
    result(session(handle).isPlayingNow)
  }

  // ----------------------------------------------------------------------- //

  def checkHandle(args: Arguments, index: Int): Int = {
    if (args.isInteger(index)) {
      args.checkInteger(index)
    } else if (args.isTable(index)) {
      args.checkTable(index).get("handle") match {
        case handle: Number => handle.intValue()
        case _ => throw new IOException("bad file descriptor")
      }
    } else args.checkAny(index) match {
      case handle: AudioHandleValue => handle.handle
      case _ => throw new IOException("bad file descriptor")
    }
  }

  def closeHandle(owner: String, handle: Int): Unit = {
    sessions.get(handle) match {
      case Some(s) =>
        owners.get(owner) match {
          case Some(set) if set.remove(handle) =>
            PacketSender.sendAudioClose(host, handle)
            s.closed = true
            sessions.remove(handle)
          case _ => throw new IOException("bad file descriptor")
        }
      case None => throw new IOException("bad file descriptor")
    }
  }

  private def checkOwner(owner: String, handle: Int) =
    if (!owners.contains(owner) || !owners(owner).contains(handle))
      throw new IOException("bad file descriptor")

  // ----------------------------------------------------------------------- //

  override def onMessage(message: Message): Unit = synchronized {
    super.onMessage(message)
    if (message.name == "computer.stopped" || message.name == "computer.started") {
      owners.get(message.source.address) match {
        case Some(set) =>
          set.foreach { handle =>
            PacketSender.sendAudioClose(host, handle)
            sessions.get(handle).foreach(_.closed = true)
            sessions.remove(handle)
          }
          set.clear()
        case _ =>
      }
    }
  }

  override def onDisconnect(node: Node): Unit = synchronized {
    super.onDisconnect(node)
    if (node == this.node) {
      sessions.keys.foreach(handle => PacketSender.sendAudioClose(host, handle))
      sessions.clear()
      owners.clear()
    }
    else if (owners.contains(node.address)) {
      for (handle <- owners(node.address)) {
        PacketSender.sendAudioClose(host, handle)
        sessions.get(handle).foreach(_.closed = true)
        sessions.remove(handle)
      }
      owners.remove(node.address)
    }
  }

  // ----------------------------------------------------------------------- //

  override def loadData(holder: DataComponentHolder): Unit = {
    super.loadData(holder)

    for(owners <- holder.getComponent(OCComponents.HANDLES)) {
      this.owners ++= owners.map { case k -> v => k -> v.to(mutable.Set) }
    }
  }

  override def saveData(holder: MutableDataComponentHolder): Unit = {
    super.saveData(holder)

    holder.setComponent(OCComponents.HANDLES, Map.from(owners.map { case k -> v => k -> v.toSet }))
  }
}
