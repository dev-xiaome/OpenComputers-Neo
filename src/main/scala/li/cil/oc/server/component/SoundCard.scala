package li.cil.oc.server.component

import li.cil.oc.{Constants, Settings}
import li.cil.oc.api.Network
import li.cil.oc.api.audio.{AudioHost, AudioReceiver}
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{ComponentConnector, EnvironmentHost, Message, Node, Visibility}
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.audio.{Instruction, SoundBoard}
import li.cil.oc.common.audio.{Audio => AudioRegistry}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

import java.util
import scala.jdk.CollectionConverters._

class SoundCard(private val host: EnvironmentHost) extends AbstractManagedEnvironment with DeviceInfo with AudioHost with AudioReceiver {
  override val node: ComponentConnector = Network.newNode(this, Visibility.Neighbors)
    .withComponent("sound")
    .withConnector()
    .create()

  protected val board: SoundBoard = new SoundBoard(this)

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Multimedia,
    DeviceAttribute.Description -> "Audio Interface",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.ViridiaComputronics,
    DeviceAttribute.Product -> "WaveBlaster Zero"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  override def onConnect(node: Node): Unit = {
    super.onConnect(node)
  }

  override def onDisconnect(node: Node): Unit = {
    super.onDisconnect(node)
    board.clearAndStop()
  }

  override def onMessage(message: Message): Unit = {
    super.onMessage(message)
    if (node.isNeighborOf(message.source) && (message.name == "computer.stopped" || message.name == "computer.started")) {
      board.clearAndStop()
    }
  }

  // ----------------------------------------------------------------------- //
  // Server tick handling: flushes the queued ("next") instruction buffer to
  // nearby clients once its timeout has elapsed. See SoundBoard#update().

  override def canUpdate: Boolean = !isClient

  override def update(): Unit = {
    super.update()
    board.update()
  }

  private def isClient: Boolean = {
    val level = host.getEnvironmentLevel
    if (level != null) level.isClientSide else false
  }

  // ----------------------------------------------------------------------- //
  // Lua API. Channel numbers here are 1-based, matching Computronics 1.12;
  // SoundBoard#checkChannel converts them to 0-based indices internally.

  @Callback(doc = "This is a bidirectional table of all valid modes.", direct = true, getter = true)
  def modes(context: Context, args: Arguments): Array[AnyRef] = Array[AnyRef](SoundBoard.compileModes)

  @Callback(doc = "This is the number of channels this card provides.", direct = true, getter = true)
  def channel_count(context: Context, args: Arguments): Array[AnyRef] = Array[AnyRef](Int.box(board.process.states.size))

  @Callback(doc = "function(volume:number); Sets the general volume of the entire sound card to a value between 0 and 1. Not an instruction, this affects all channels directly.", direct = true)
  def setTotalVolume(context: Context, args: Arguments): Array[AnyRef] = {
    board.setTotalVolume(args.checkDouble(0))
    Array.empty[AnyRef]
  }

  @Callback(doc = "function(); Clears the instruction queue.", direct = true)
  def clear(context: Context, args: Arguments): Array[AnyRef] = {
    board.clear()
    Array.empty[AnyRef]
  }

  @Callback(doc = "function(channel:number); Instruction; Opens the specified channel, allowing sound to be generated.", direct = true)
  def open(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.Open(checkChannel(args)))

  @Callback(doc = "function(channel:number); Instruction; Closes the specified channel, stopping sound from being generated.", direct = true)
  def close(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.Close(checkChannel(args)))

  @Callback(doc = "function(channel:number, type:number); Instruction; Sets the wave type on the specified channel.", direct = true)
  def setWave(context: Context, args: Arguments): Array[AnyRef] = board.setWave(args.checkInteger(0), args.checkInteger(1))

  @Callback(doc = "function(channel:number, frequency:number); Instruction; Sets the frequency on the specified channel.", direct = true)
  def setFrequency(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.SetFrequency(checkChannel(args), args.checkDouble(1).asInstanceOf[Float]))

  @Callback(doc = "function(channel:number, initial:number, mask:number); Instruction; Makes the specified channel generate LFSR noise. Functions like a wave type.", direct = true)
  def setLFSR(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.SetLFSR(checkChannel(args), args.checkInteger(1), args.checkInteger(2)))

  @Callback(doc = "function(duration:number); Instruction; Adds a delay of the specified duration in milliseconds, allowing sound to generate.", direct = true)
  def delay(context: Context, args: Arguments): Array[AnyRef] = board.delay(args.checkInteger(0))

  @Callback(doc = "function(channel:number, modIndex:number, intensity:number); Instruction; Assigns a frequency modulator channel to the specified channel with the specified intensity.", direct = true)
  def setFM(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.SetFM(checkChannel(args), checkChannel(args, 1), args.checkDouble(2).asInstanceOf[Float]))

  @Callback(doc = "function(channel:number); Instruction; Removes the specified channel's frequency modulator.", direct = true)
  def resetFM(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.ResetFM(checkChannel(args)))

  @Callback(doc = "function(channel:number, modIndex:number); Instruction; Assigns an amplitude modulator channel to the specified channel.", direct = true)
  def setAM(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.SetAM(checkChannel(args), checkChannel(args, 1)))

  @Callback(doc = "function(channel:number); Instruction; Removes the specified channel's amplitude modulator.", direct = true)
  def resetAM(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.ResetAM(checkChannel(args)))

  @Callback(doc = "function(channel:number, attack:number, decay:number, attenuation:number, release:number); Instruction; Assigns ADSR to the specified channel with the specified phase durations in milliseconds and attenuation between 0 and 1.", direct = true)
  def setADSR(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.SetADSR(checkChannel(args), args.checkInteger(1), args.checkInteger(2), args.checkDouble(3).asInstanceOf[Float], args.checkInteger(4)))

  @Callback(doc = "function(channel:number); Instruction; Removes ADSR from the specified channel.", direct = true)
  def resetEnvelope(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.ResetEnvelope(checkChannel(args)))

  @Callback(doc = "function(channel:number, volume:number); Instruction; Sets the volume of the channel between 0 and 1.", direct = true)
  def setVolume(context: Context, args: Arguments): Array[AnyRef] = board.tryAdd(Instruction.SetVolume(checkChannel(args), args.checkDouble(1).asInstanceOf[Float]))

  @Callback(doc = "function(); Starts processing the queue; Returns true is processing began, false if there is still a queue being processed.", direct = true)
  def process(context: Context, args: Arguments): Array[AnyRef] = board.doProcess()

  protected def checkChannel(args: Arguments, index: Int): Int = board.checkChannel(args.checkInteger(index))

  protected def checkChannel(args: Arguments): Int = checkChannel(args, 0)

  // ----------------------------------------------------------------------- //

  override def loadData(nbt: CompoundTag): Unit = {
    super.loadData(nbt)
    board.load(nbt)
  }

  override def saveData(nbt: CompoundTag): Unit = synchronized {
    super.saveData(nbt)
    board.save(nbt)
  }

  override def level(): Level = host.getEnvironmentLevel

  override def tryChangeBuffer(delta: Double): Boolean = node.tryChangeBuffer(delta)

  override def address(): String = node.address()

  override def position(): Vec3 = new Vec3(host.xPosition, host.yPosition, host.zPosition)

  override def setChanged(): Unit = {}

  override def getId: Int = board.codecId

  override def distance(): Int = Settings.get.audioRadius.toInt

  override def getReceivers: util.Set[AudioReceiver] = {
    val neighbors = node.neighbors()
      .asScala
      .collect { case receiver: AudioReceiver => receiver }
      .toSet[AudioReceiver]
    if (neighbors.isEmpty) util.Collections.singleton[AudioReceiver](this)
    else neighbors.asJava
  }
}