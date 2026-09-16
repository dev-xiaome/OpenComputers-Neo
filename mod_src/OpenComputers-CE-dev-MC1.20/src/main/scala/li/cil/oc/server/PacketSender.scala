package li.cil.oc.server

import com.google.common.cache.{Cache, CacheBuilder}
import li.cil.oc.api.audio.{AudioHost, AudioReceiver}
import li.cil.oc.{Settings, api}
import li.cil.oc.api.event.{FileSystemAccessEvent, NetworkActivityEvent}
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.common._
import li.cil.oc.common.audio.Instruction
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.common.blockentity.Waypoint
import li.cil.oc.common.blockentity.traits._
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.PackedColor
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.BlockPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.registries.ForgeRegistries

import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import scala.collection.mutable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.level.Level
import net.minecraft.sounds.SoundSource

import scala.jdk.CollectionConverters._

object PacketSender {
  def sendSoundCardData(host: AudioHost, address: String, volume: Byte, receivers: java.util.Set[AudioReceiver], instructions: java.util.Queue[Instruction]): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.SoundCardData)
    val pos = host.position()
    pb.writeInt(host.getId)
    pb.writeUTF(address)
    pb.writeInt(instructions.size)
    for (inst <- instructions.asScala) {
      inst match {
        case Instruction.Open(channel) =>
          pb.writeByte(0)
          pb.writeByte(channel)
        case Instruction.Close(channel) =>
          pb.writeByte(1)
          pb.writeByte(channel)
        case Instruction.SetWave(channel, wave) =>
          pb.writeByte(2)
          pb.writeByte(channel)
          pb.writeInt(wave.ordinal())
        case Instruction.Delay(delay) =>
          pb.writeByte(3)
          pb.writeInt(delay)
        case Instruction.SetFM(channel, modulatorIndex, index) =>
          pb.writeByte(4)
          pb.writeByte(channel)
          pb.writeInt(modulatorIndex)
          pb.writeFloat(index)
        case Instruction.ResetFM(channel) =>
          pb.writeByte(5)
          pb.writeByte(channel)
        case Instruction.SetAM(channel, modulatorIndex) =>
          pb.writeByte(6)
          pb.writeByte(channel)
          pb.writeInt(modulatorIndex)
        case Instruction.ResetAM(channel) =>
          pb.writeByte(7)
          pb.writeByte(channel)
        case Instruction.SetADSR(channel, attack, decay, attenuation, release) =>
          pb.writeByte(8)
          pb.writeByte(channel)
          pb.writeInt(attack)
          pb.writeInt(decay)
          pb.writeFloat(attenuation)
          pb.writeInt(release)
        case Instruction.ResetEnvelope(channel) =>
          pb.writeByte(9)
          pb.writeByte(channel)
        case Instruction.SetVolume(channel, volume) =>
          pb.writeByte(10)
          pb.writeByte(channel)
          pb.writeFloat(volume)
        case Instruction.SetFrequency(channel, frequency) =>
          pb.writeByte(11)
          pb.writeByte(channel)
          pb.writeFloat(frequency)
        case Instruction.SetWhiteNoise(channel) =>
          pb.writeByte(12)
          pb.writeByte(channel)
        case Instruction.SetLFSR(channel, initial, mask) =>
          pb.writeByte(13)
          pb.writeByte(channel)
          pb.writeInt(initial)
          pb.writeInt(mask)
      }
    }
    pb.writeByte(volume)
    pb.writeInt(receivers.size())
    for (receiver <- receivers.asScala) {
      pb.writeUTF(if (receiver.level() != null) receiver.level().dimension().toString else "")
      val pos = receiver.position()
      pb.writeFloat(pos.x.toFloat)
      pb.writeFloat(pos.y.toFloat)
      pb.writeFloat(pos.z.toFloat)
      pb.writeShort(receiver.distance().toShort)
      pb.writeUTF(receiver.address())
    }
    pb.sendToAllPlayers()
  }

  def sendAdapterState(t: blockentity.Adapter): Unit = {
    val pb = new SimplePacketBuilder(PacketType.AdapterState)

    pb.writeTileEntity(t)
    pb.writeByte(t.compressSides)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendAnalyze(address: String, player: ServerPlayer): Unit = {
    val pb = new SimplePacketBuilder(PacketType.Analyze)

    pb.writeUTF(address)

    pb.sendToPlayer(player)
  }

  def sendChargerState(t: blockentity.Charger): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ChargerState)

    pb.writeTileEntity(t)
    pb.writeDouble(t.chargeSpeed)
    pb.writeBoolean(t.hasPower)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendClientLog(line: String, player: ServerPlayer): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.ClientLog)

    pb.writeUTF(line)

    pb.sendToPlayer(player)
  }

  def sendClipboard(player: ServerPlayer, text: String): Unit = {
    val pb = new SimplePacketBuilder(PacketType.Clipboard)

    pb.writeUTF(text)

    pb.sendToPlayer(player)
  }

  def sendColorChange(t: Colored): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ColorChange)

    pb.writeTileEntity(t)
    pb.writeInt(t.getColor)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendComputerState(t: Computer): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ComputerState)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.isRunning)
    pb.writeBoolean(t.hasErrored)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendMachineItemState(player: ServerPlayer, stack: ItemStack, isRunning: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.MachineItemStateResponse)

    pb.writeItemStack(stack)
    pb.writeBoolean(isRunning)

    pb.sendToPlayer(player)
  }

  def sendComputerUserList(t: Computer, list: Array[String]): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ComputerUserList)

    pb.writeTileEntity(t)
    pb.writeInt(list.length)
    list.foreach(pb.writeUTF)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendContainerUpdate(c: AbstractContainerMenu, nbt: CompoundTag, player: ServerPlayer): Unit = {
    if (!nbt.isEmpty) {
      val pb = new SimplePacketBuilder(PacketType.ContainerUpdate)

      pb.writeInt(c.containerId)
      pb.writeNBT(nbt)

      pb.sendToPlayer(player)
    }
  }

  def sendDisassemblerActive(t: blockentity.Disassembler, active: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.DisassemblerActiveChange)

    pb.writeTileEntity(t)
    pb.writeBoolean(active)

    pb.sendToPlayersNearTileEntity(t)
  }

  // Avoid spamming the network with disk activity notices.
  private val fileSystemAccessTimeouts = mutable.WeakHashMap.empty[Node, Cache[String, java.lang.Long]]

  def sendFileSystemActivity(node: Node, host: EnvironmentHost, name: String) = {
    val diskActivityPacketDelay = Settings.get.diskActivitySoundDelay

    if (diskActivityPacketDelay >= 0) {
      val hostTimeouts = fileSystemAccessTimeouts.synchronized {
        fileSystemAccessTimeouts.getOrElseUpdate(node, CacheBuilder.newBuilder().concurrencyLevel(Settings.get.threads).maximumSize(250).expireAfterWrite(diskActivityPacketDelay, TimeUnit.MILLISECONDS).build[String, java.lang.Long]())
      }
      val lastHostTimeout = hostTimeouts.getIfPresent(name)
      if (lastHostTimeout == null || lastHostTimeout <= System.currentTimeMillis()) {
        val event = host match {
          case t: BlockEntity => new FileSystemAccessEvent.Server(name, t, node)
          case _ => new FileSystemAccessEvent.Server(name, host.getEnvironmentLevel, host.xPosition, host.yPosition, host.zPosition, node)
        }
        MinecraftForge.EVENT_BUS.post(event)
        if (!event.isCanceled) {
          hostTimeouts.put(name, System.currentTimeMillis() + diskActivityPacketDelay)

          val pb = new SimplePacketBuilder(PacketType.FileSystemActivity)

          pb.writeUTF(event.getSound)
          NbtIo.write(event.getData, pb)
          event.getBlockEntity match {
            case t: BlockEntity =>
              pb.writeBoolean(true)
              pb.writeTileEntity(t)
            case _ =>
              pb.writeBoolean(false)
              pb.writeUTF(event.getWorld.dimension.location.toString)
              pb.writeDouble(event.getX)
              pb.writeDouble(event.getY)
              pb.writeDouble(event.getZ)
          }

          pb.sendToPlayersNearHost(host, Option(Settings.get.maxNetworkClientSoundPacketDistance))
        }
      }
    }
  }

  def sendFileSystemActivity(node: Node, host: EnvironmentHost) = {
    val diskActivityPacketDelay = Settings.get.diskActivitySoundDelay

    if (diskActivityPacketDelay >= 0) {
      val hostTimeouts = fileSystemAccessTimeouts.synchronized {
        fileSystemAccessTimeouts.getOrElseUpdate(node, CacheBuilder.newBuilder().concurrencyLevel(Settings.get.threads).maximumSize(250).expireAfterWrite(diskActivityPacketDelay, TimeUnit.MILLISECONDS).build[String, java.lang.Long]())
      }
      val cacheKey = host match {
        case t: BlockEntity => t.getBlockPos.toString
        case _ => s"${host.xPosition},${host.yPosition},${host.zPosition}"
      }
      val lastHostTimeout = hostTimeouts.getIfPresent(cacheKey)
      if (lastHostTimeout == null || lastHostTimeout <= System.currentTimeMillis()) {
        val event = host match {
          case t: BlockEntity => new FileSystemAccessEvent.Server(null, t, node)
          case _ => new FileSystemAccessEvent.Server(null, host.getEnvironmentLevel, host.xPosition, host.yPosition, host.zPosition, node)
        }
        MinecraftForge.EVENT_BUS.post(event)
        if (!event.isCanceled) {
          hostTimeouts.put(cacheKey, System.currentTimeMillis() + diskActivityPacketDelay)

          val pb = new SimplePacketBuilder(PacketType.FileSystemActivity)

          pb.writeUTF(event.getSound)
          NbtIo.write(event.getData, pb)
          event.getBlockEntity match {
            case t: BlockEntity =>
              pb.writeBoolean(true)
              pb.writeTileEntity(t)
            case _ =>
              pb.writeBoolean(false)
              pb.writeUTF(event.getWorld.dimension.location.toString)
              pb.writeDouble(event.getX)
              pb.writeDouble(event.getY)
              pb.writeDouble(event.getZ)
          }

          pb.sendToPlayersNearHost(host, Option(Settings.get.maxNetworkClientSoundPacketDistance))
        }
      }
    }
  }

  def sendNetworkActivity(node: Node, host: EnvironmentHost): Unit = {

    val event = host match {
      case t: BlockEntity => new NetworkActivityEvent.Server(t, node)
      case _ => new NetworkActivityEvent.Server(host.getEnvironmentLevel, host.xPosition, host.yPosition, host.zPosition, node)
    }
    MinecraftForge.EVENT_BUS.post(event)
    if (!event.isCanceled) {

      val pb = new SimplePacketBuilder(PacketType.NetworkActivity)

      NbtIo.write(event.getData, pb)
      event.getBlockEntity match {
        case t: BlockEntity =>
          pb.writeBoolean(true)
          pb.writeTileEntity(t)
        case _ =>
          pb.writeBoolean(false)
          pb.writeUTF(event.getWorld.dimension.location.toString)
          pb.writeDouble(event.getX)
          pb.writeDouble(event.getY)
          pb.writeDouble(event.getZ)
      }

      pb.sendToPlayersNearHost(host, Option(Settings.get.maxNetworkClientEffectPacketDistance))
    }
  }

  def sendFloppyChange(t: blockentity.DiskDrive, stack: ItemStack = ItemStack.EMPTY): Unit = {
    val pb = new SimplePacketBuilder(PacketType.FloppyChange)

    pb.writeTileEntity(t)
    pb.writeItemStack(stack)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramClear(t: blockentity.Hologram): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramClear)

    pb.writeTileEntity(t)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramColor(t: blockentity.Hologram, index: Int, value: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramColor)

    pb.writeTileEntity(t)
    pb.writeInt(index)
    pb.writeInt(value)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramPowerChange(t: blockentity.Hologram): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramPowerChange)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.hasPower)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramScale(t: blockentity.Hologram): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramScale)

    pb.writeTileEntity(t)
    pb.writeDouble(t.scale)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramArea(t: blockentity.Hologram): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.HologramArea)

    pb.writeTileEntity(t)
    pb.writeByte(t.dirtyFromX)
    pb.writeByte(t.dirtyUntilX)
    pb.writeByte(t.dirtyFromZ)
    pb.writeByte(t.dirtyUntilZ)
    for (x <- t.dirtyFromX until t.dirtyUntilX) {
      for (z <- t.dirtyFromZ until t.dirtyUntilZ) {
        pb.writeInt(t.volume(x + z * t.width))
        pb.writeInt(t.volume(x + z * t.width + t.width * t.width))
      }
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramValues(t: blockentity.Hologram): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.HologramValues)

    pb.writeTileEntity(t)
    pb.writeInt(t.dirty.size)
    for (xz <- t.dirty) {
      val x = (xz >> 8).toByte
      val z = xz.toByte
      pb.writeShort(xz)
      val rangeStart: Int = x + z * t.width
      val rangeFinal: Int = x + z * t.width + t.width * t.width
      pb.writeInt(t.volume(rangeStart max 0 min t.volume.length - 1))
      pb.writeInt(t.volume(rangeFinal max 0 min t.volume.length - 1))
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramOffset(t: blockentity.Hologram): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramTranslation)

    pb.writeTileEntity(t)
    pb.writeDouble(t.translation.x)
    pb.writeDouble(t.translation.y)
    pb.writeDouble(t.translation.z)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramRotation(t: blockentity.Hologram): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramRotation)

    pb.writeTileEntity(t)
    pb.writeFloat(t.rotationAngle)
    pb.writeFloat(t.rotationX)
    pb.writeFloat(t.rotationY)
    pb.writeFloat(t.rotationZ)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendHologramRotationSpeed(t: blockentity.Hologram): Unit = {
    val pb = new SimplePacketBuilder(PacketType.HologramRotationSpeed)

    pb.writeTileEntity(t)
    pb.writeFloat(t.rotationSpeed)
    pb.writeFloat(t.rotationSpeedX)
    pb.writeFloat(t.rotationSpeedY)
    pb.writeFloat(t.rotationSpeedZ)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendLootDisks(p: ServerPlayer): Unit = {
    // Sending as separate packets, because NbtIo hiccups otherwise...
    val stacks = Loot.worldDisks.map(_._1)
    for (stack <- stacks) {
      val pb = new SimplePacketBuilder(PacketType.LootDisk)

      pb.writeItemStack(stack)

      pb.sendToPlayer(p)
    }
    for (stack <- Loot.disksForCyclingServer) {
      val pb = new SimplePacketBuilder(PacketType.CyclingDisk)

      pb.writeItemStack(stack)

      pb.sendToPlayer(p)
    }
  }

  def sendNanomachineConfiguration(player: Player): Unit = {
    val pb = new SimplePacketBuilder(PacketType.NanomachinesConfiguration)

    pb.writeEntity(player)
    api.Nanomachines.getController(player) match {
      case controller: ControllerImpl =>
        pb.writeBoolean(true)
        val nbt = new CompoundTag()
        controller.saveData(nbt)
        pb.writeNBT(nbt)
      case _ =>
        pb.writeBoolean(false)
    }

    pb.sendToPlayersNearEntity(player)
  }

  def sendNanomachineInputs(player: Player): Unit = {
    api.Nanomachines.getController(player) match {
      case controller: ControllerImpl =>
        val pb = new SimplePacketBuilder(PacketType.NanomachinesInputs)

        pb.writeEntity(player)
        val inputs = controller.configuration.triggers.map(i => if (i.isActive) 1.toByte else 0.toByte).toArray
        pb.writeInt(inputs.length)
        pb.write(inputs)

        pb.sendToPlayersNearEntity(player)
      case _ => // Wat.
    }
  }

  def sendNanomachinePower(player: Player): Unit = {
    api.Nanomachines.getController(player) match {
      case controller: ControllerImpl =>
        val pb = new SimplePacketBuilder(PacketType.NanomachinesPower)

        pb.writeEntity(player)
        pb.writeDouble(controller.getLocalBuffer)

        pb.sendToPlayersNearEntity(player)
      case _ => // Wat.
    }
  }

  def sendNetSplitterState(t: blockentity.NetSplitter): Unit = {
    val pb = new SimplePacketBuilder(PacketType.NetSplitterState)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.isInverted)
    pb.writeByte(t.compressSides)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendParticleEffect(position: BlockPosition, particleType: ParticleOptions, count: Int, velocity: Double, direction: Option[Direction] = None): Unit = if (count > 0) {
    val pb = new SimplePacketBuilder(PacketType.ParticleEffect)

    pb.writeUTF(position.world.get.dimension.location.toString)
    pb.writeInt(position.x)
    pb.writeInt(position.y)
    pb.writeInt(position.z)
    pb.writeDouble(velocity)
    pb.writeDirection(direction)
    pb.writeRegistryEntry(ForgeRegistries.PARTICLE_TYPES, particleType.getType())
    pb.writeByte(count.toByte)

    pb.sendToNearbyPlayers(position.world.get, position.x, position.y, position.z, Some(Settings.get.maxNetworkClientEffectPacketDistance / 2.0D))
  }

  def sendPetVisibility(name: Option[String] = None, player: Option[ServerPlayer] = None): Unit = {
    val pb = new SimplePacketBuilder(PacketType.PetVisibility)

    name match {
      case Some(n) =>
        pb.writeInt(1)
        pb.writeUTF(n)
        pb.writeBoolean(!PetVisibility.hidden.contains(n))
      case _ =>
        pb.writeInt(PetVisibility.hidden.size)
        for (n <- PetVisibility.hidden) {
          pb.writeUTF(n)
          pb.writeBoolean(false)
        }
    }

    player match {
      case Some(p) => pb.sendToPlayer(p)
      case _ => pb.sendToAllPlayers()
    }
  }

  def sendPowerState(t: PowerInformation): Unit = {
    val pb = new SimplePacketBuilder(PacketType.PowerState)

    pb.writeTileEntity(t)
    pb.writeDouble(math.round(t.globalBuffer))
    pb.writeDouble(t.globalBufferSize)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendPrinting(t: blockentity.Printer, printing: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.PrinterState)

    pb.writeTileEntity(t)
    pb.writeBoolean(printing)

    pb.sendToPlayersNearHost(t)
  }

  def sendRackInventory(t: blockentity.Rack): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RackInventory)

    pb.writeTileEntity(t)
    pb.writeInt(t.getContainerSize)
    for (slot <- 0 until t.getContainerSize) {
      pb.writeInt(slot)
      pb.writeItemStack(t.getItem(slot))
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRackInventory(t: blockentity.Rack, slot: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RackInventory)

    pb.writeTileEntity(t)
    pb.writeInt(1)
    pb.writeInt(slot)
    pb.writeItemStack(t.getItem(slot))

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRackMountableData(t: blockentity.Rack, mountable: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RackMountableData)

    pb.writeTileEntity(t)
    pb.writeInt(mountable)
    pb.writeNBT(t.lastData(mountable))

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRaidChange(t: blockentity.Raid): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RaidStateChange)

    pb.writeTileEntity(t)
    for (slot <- 0 until t.getContainerSize) {
      pb.writeBoolean(!t.getItem(slot).isEmpty)
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRedstoneState(t: RedstoneAware): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RedstoneState)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.isOutputEnabled)
    for (d <- Direction.values) {
      pb.writeByte(t.getOutput(d))
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRobotAssembling(t: blockentity.Assembler, assembling: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotAssemblingState)

    pb.writeTileEntity(t)
    pb.writeBoolean(assembling)

    pb.sendToPlayersNearHost(t)
  }

  def sendRobotMove(t: blockentity.Robot, position: BlockPos, direction: Direction): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotMove)

    // Custom pb.writeTileEntity() with fake coordinates (valid for the client).
    pb.writeUTF(t.getEnvironmentLevel.dimension.location.toString)
    pb.writeInt(position.getX)
    pb.writeInt(position.getY)
    pb.writeInt(position.getZ)
    pb.writeDirection(Option(direction))

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRobotAnimateSwing(t: blockentity.Robot): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotAnimateSwing)

    pb.writeTileEntity(t.proxy)
    pb.writeInt(t.animationTicksTotal)

    pb.sendToPlayersNearTileEntity(t, Option(Settings.get.maxNetworkClientEffectPacketDistance))
  }

  def sendRobotAnimateTurn(t: blockentity.Robot): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotAnimateTurn)

    pb.writeTileEntity(t.proxy)
    pb.writeByte(t.turnAxis)
    pb.writeInt(t.animationTicksTotal)

    pb.sendToPlayersNearTileEntity(t, Option(Settings.get.maxNetworkClientEffectPacketDistance))
  }

  def sendRobotInventory(t: blockentity.Robot, slot: Int, stack: ItemStack): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotInventoryChange)

    pb.writeTileEntity(t.proxy)
    pb.writeInt(slot)
    pb.writeItemStack(stack)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRobotLightChange(t: blockentity.Robot): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotLightChange)

    pb.writeTileEntity(t.proxy)
    pb.writeInt(t.info.lightColor)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRobotNameChange(t: blockentity.Robot): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotNameChange)

    pb.writeTileEntity(t.proxy)
    val name = t.name
    val len = name.length.toShort
    pb.writeShort(len)
    for (x <- 0 until len) {
      pb.writeChar(name(x))
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendRobotSelectedSlotChange(t: blockentity.Robot): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RobotSelectedSlotChange)

    pb.writeTileEntity(t.proxy)
    pb.writeInt(t.selectedSlot)

    pb.sendToPlayersNearTileEntity(t, Option(Settings.get.maxNetworkClientEffectPacketDistance / 4.0D))
  }

  def sendRotatableState(t: Rotatable): Unit = {
    val pb = new SimplePacketBuilder(PacketType.RotatableState)

    pb.writeTileEntity(t)
    pb.writeDirection(Option(t.pitch))
    pb.writeDirection(Option(t.yaw))

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendSwitchActivity(t: blockentity.Relay): Unit = {
    val pb = new SimplePacketBuilder(PacketType.SwitchActivity)

    pb.writeTileEntity(t)

    pb.sendToPlayersNearTileEntity(t, Option(Settings.get.maxNetworkClientEffectPacketDistance))
  }

  def appendTextBufferColorChange(pb: PacketBuilder, foreground: PackedColor.Color, background: PackedColor.Color): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiColorChange)

    pb.writeInt(foreground.value)
    pb.writeBoolean(foreground.isPalette)
    pb.writeInt(background.value)
    pb.writeBoolean(background.isPalette)
  }

  def appendTextBufferCopy(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiCopy)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeInt(tx)
    pb.writeInt(ty)
  }

  def appendTextBufferDepthChange(pb: PacketBuilder, value: api.internal.TextBuffer.ColorDepth): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiDepthChange)

    pb.writeInt(value.ordinal)
  }

  def appendTextBufferFill(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, c: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiFill)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeMedium(c)
  }

  def appendTextBufferPaletteChange(pb: PacketBuilder, index: Int, color: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiPaletteChange)

    pb.writeInt(index)
    pb.writeInt(color)
  }

  def appendTextBufferResolutionChange(pb: PacketBuilder, w: Int, h: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  def appendTextBufferViewportResolutionChange(pb: PacketBuilder, w: Int, h: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiViewportResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  def appendTextBufferMaxResolutionChange(pb: PacketBuilder, w: Int, h: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiMaxResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  def appendTextBufferSet(pb: PacketBuilder, col: Int, row: Int, s: String, vertical: Boolean): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiSet)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeUTF(s)
    pb.writeBoolean(vertical)
  }

  def appendTextBufferBitBlt(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, owner: String, id: Int, fromCol: Int, fromRow: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferBitBlt)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeUTF(owner)
    pb.writeInt(id)
    pb.writeInt(fromCol)
    pb.writeInt(fromRow)
  }

  def appendTextBufferRamInit(pb: PacketBuilder, address: String, id: Int, nbt: CompoundTag): Unit = {
    pb.writePacketType(PacketType.TextBufferRamInit)

    pb.writeUTF(address)
    pb.writeInt(id)
    pb.writeNBT(nbt)
  }

  def appendTextBufferRamDestroy(pb: PacketBuilder, owner: String, id: Int): Unit = {
    pb.writePacketType(PacketType.TextBufferRamDestroy)
    pb.writeUTF(owner)
    pb.writeInt(id)
  }

  def appendTextBufferRawSetText(pb: PacketBuilder, col: Int, row: Int, text: Array[Array[Int]]): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiRawSetText)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(text.length.toShort)
    for (y <- 0 until text.length.toShort) {
      val line = text(y)
      pb.writeShort(line.length.toShort)
      for (x <- 0 until line.length.toShort) {
        pb.writeMedium(line(x))
      }
    }
  }

  def appendTextBufferRawSetBackground(pb: PacketBuilder, col: Int, row: Int, color: Array[Array[Int]]): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiRawSetBackground)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(color.length.toShort)
    for (y <- 0 until color.length.toShort) {
      val line = color(y)
      pb.writeShort(line.length.toShort)
      for (x <- 0 until line.length.toShort) {
        pb.writeInt(line(x))
      }
    }
  }

  def appendTextBufferRawSetForeground(pb: PacketBuilder, col: Int, row: Int, color: Array[Array[Int]]): Unit = {
    pb.writePacketType(PacketType.TextBufferMultiRawSetForeground)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(color.length.toShort)
    for (y <- 0 until color.length.toShort) {
      val line = color(y)
      pb.writeShort(line.length.toShort)
      for (x <- 0 until line.length.toShort) {
        pb.writeInt(line(x))
      }
    }
  }

  def sendTextBufferInit(address: String, value: CompoundTag, player: ServerPlayer): Unit = {
    val pb = new CompressedPacketBuilder(PacketType.TextBufferInit)

    pb.writeUTF(address)
    pb.writeNBT(value)

    pb.sendToPlayer(player)
  }

  def sendTextBufferPowerChange(address: String, hasPower: Boolean, host: EnvironmentHost): Unit = {
    val pb = new SimplePacketBuilder(PacketType.TextBufferPowerChange)

    pb.writeUTF(address)
    pb.writeBoolean(hasPower)

    pb.sendToPlayersNearHost(host)
  }

  def sendScreenTouchMode(t: blockentity.Screen, value: Boolean): Unit = {
    val pb = new SimplePacketBuilder(PacketType.ScreenTouchMode)

    pb.writeTileEntity(t)
    pb.writeBoolean(value)

    pb.sendToPlayersNearTileEntity(t)
  }

  def sendSound(level: Level, x: Double, y: Double, z: Double, sound: ResourceLocation, category: SoundSource, range: Double): Unit = {
    val pb = new SimplePacketBuilder(PacketType.SoundEffect)

    pb.writeUTF(level.dimension.location.toString)
    pb.writeDouble(x)
    pb.writeDouble(y)
    pb.writeDouble(z)
    pb.writeUTF(sound.toString)
    pb.writeByte(category.ordinal())
    pb.writeFloat(range.toFloat)

    pb.sendToNearbyPlayers(level, x, y, z, Option(range))
  }

  def sendSound(level: Level, x: Double, y: Double, z: Double, frequency: Int, duration: Int): Unit = {
    val pb = new SimplePacketBuilder(PacketType.Sound)

    val blockPos = BlockPosition(x, y, z)
    pb.writeUTF(level.dimension.location.toString)
    pb.writeInt(blockPos.x)
    pb.writeInt(blockPos.y)
    pb.writeInt(blockPos.z)
    pb.writeShort(frequency.toShort)
    pb.writeShort(duration.toShort)

    pb.sendToNearbyPlayers(level, x, y, z, Option(Settings.get.maxNetworkClientSoundPacketDistance))
  }

  def sendSound(level: Level, x: Double, y: Double, z: Double, pattern: String): Unit = {
    val pb = new SimplePacketBuilder(PacketType.SoundPattern)

    val blockPos = BlockPosition(x, y, z)
    pb.writeUTF(level.dimension.location.toString)
    pb.writeInt(blockPos.x)
    pb.writeInt(blockPos.y)
    pb.writeInt(blockPos.z)
    pb.writeUTF(pattern)

    pb.sendToNearbyPlayers(level, x, y, z, Option(Settings.get.maxNetworkClientSoundPacketDistance))
  }

  def sendTransposerActivity(t: blockentity.Transposer): Unit = {
    val pb = new SimplePacketBuilder(PacketType.TransposerActivity)

    pb.writeTileEntity(t)

    pb.sendToPlayersNearTileEntity(t, Option(Settings.get.maxNetworkClientEffectPacketDistance / 2.0D))
  }

  def sendWaypointLabel(t: Waypoint): Unit = {
    val pb = new SimplePacketBuilder(PacketType.WaypointLabel)

    pb.writeTileEntity(t)
    pb.writeUTF(t.label)

    pb.sendToPlayersNearTileEntity(t)
  }
}