package li.cil.oc.common

import li.cil.oc.api.audio.AudioReceiver
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.{ChunkAccess, LevelChunk}
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.level.{BlockEvent, ChunkEvent, LevelEvent}

import java.util.Calendar

//import appeng.api.networking.IGridBlock
//import appeng.api.util.AEPartLocation
import li.cil.oc._
import li.cil.oc.api.Network
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.internal.Colored
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.internal.Server
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.SidedComponent
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.common.capabilities._
import li.cil.oc.common.component.TerminalServer
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.common.item.traits
import li.cil.oc.common.blockentity.Robot
import li.cil.oc.common.blockentity.traits.power
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util
import li.cil.oc.server.component.Keyboard
import li.cil.oc.server.machine.Callbacks
import li.cil.oc.server.machine.Machine
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedLevel._
import li.cil.oc.util.StackOption._
import li.cil.oc.util._
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.minecraft.Util
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent._
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.sounds.SoundSource
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ChunkHolder

object EventHandler {
  private var serverTicks = 0L
  private val pendingServerTimed = mutable.PriorityQueue.empty[(Long, () => Unit)](Ordering.by(x => -x._1))

  private val pendingServer = mutable.Buffer.empty[() => Unit]

  private val pendingClient = mutable.Buffer.empty[() => Unit]

  private val runningRobots = mutable.Set.empty[Robot]

  private val keyboards = java.util.Collections.newSetFromMap[Keyboard](new java.util.WeakHashMap[Keyboard, java.lang.Boolean])

  private val machines = mutable.Set.empty[Machine]

  def onRobotStart(robot: Robot): Unit = runningRobots += robot

  def onRobotStopped(robot: Robot): Unit = runningRobots -= robot

  def addKeyboard(keyboard: Keyboard): Unit = keyboards.asScala += keyboard

  def scheduleClose(machine: Machine): Unit = machines += machine

  def unscheduleClose(machine: Machine): Unit = machines -= machine

  def scheduleServer(tileEntity: BlockEntity): Unit = {
    if (SideTracker.isServer) pendingServer.synchronized {
      pendingServer += (() => Network.joinOrCreateNetwork(tileEntity))
    }
  }

  def scheduleServer(f: () => Unit): Unit = {
    pendingServer.synchronized {
      pendingServer += f
    }
  }

  def scheduleServer(f: () => Unit, delay: Int): Unit = {
    pendingServerTimed.synchronized {
      pendingServerTimed += (serverTicks + (delay max 0)) -> f
    }
  }

  def scheduleClient(f: () => Unit): Unit = {
    pendingClient.synchronized {
      pendingClient += f
    }
  }

  //object AE2 {
  //  def scheduleAE2Add(tileEntity: power.AppliedEnergistics2): Unit = {
  //    if (SideTracker.isServer) pendingServer.synchronized {
  //      pendingServer += (() => tileEntity.updateGridNodeState())
  //    }
  //  }
  //}

  def scheduleWirelessRedstone(rs: server.component.RedstoneWireless): Unit = {
    if (SideTracker.isServer) pendingServer.synchronized {
      pendingServer += (() => if (rs.node.network != null) {
        util.WirelessRedstone.addReceiver(rs)
        util.WirelessRedstone.updateOutput(rs)
      })
    }
  }

  // 1.21.1 移除：原 `onAttachCapabilitiesItemStack` / `onAttachCapabilities`
  // （`AttachCapabilitiesEvent[ItemStack]` / `AttachCapabilitiesEvent[BlockEntity]`）。
  //
  // NeoForge 1.21 把 Forge 1.20 的 `AttachCapabilitiesEvent` 整套删掉了：能力不再由
  // 事件逐实例挂载，而是在 `RegisterCapabilitiesEvent` 里按「类型 + 谓词」注册提供者。
  // 因此这两个监听器在本版已无对应事件，能力注册改由
  // [[li.cil.oc.common.capabilities.Capabilities.onRegisterCapabilities]] 承担
  // （主类里 `modBus.register(Capabilities)`）。

  /**
   * 服务端 tick 前半段（对应 1.20 Forge 的 `TickEvent.Phase.START`）。
   *
   * NeoForge 1.21 把 `TickEvent` 拆成了 `ServerTickEvent.Pre` / `ServerTickEvent.Post`，
   * 不再有 `phase` 字段，所以原先的单个 `onServerTick` 必须拆成两个方法。
   */
  @SubscribeEvent
  def onServerTickPre(e: ServerTickEvent.Pre): Unit = {
    pendingServer.synchronized {
      val adds = pendingServer.toArray
      pendingServer.clear()
      adds
    } foreach (callback => {
      try callback() catch {
        case t: Throwable => OpenComputers.log.warn("Error in scheduled tick action.", t)
      }
    })

    serverTicks += 1
    while (pendingServerTimed.nonEmpty && pendingServerTimed.head._1 < serverTicks) {
      val (_, callback) = pendingServerTimed.dequeue()
      try callback() catch {
        case t: Throwable => OpenComputers.log.warn("Error in scheduled tick action.", t)
      }
    }

    val invalid = mutable.ArrayBuffer.empty[Robot]
    runningRobots.foreach(robot => {
      if (robot.isRemoved) invalid += robot
      else if (robot.getEnvironmentLevel != null) robot.machine.update()
    })
    runningRobots --= invalid
  }

  /** 服务端 tick 后半段（对应 1.20 Forge 的 `TickEvent.Phase.END`）。 */
  @SubscribeEvent
  def onServerTickPost(e: ServerTickEvent.Post): Unit = {
    // Clean up machines *after* a tick, to allow stuff to be saved, first.
    val closed = mutable.ArrayBuffer.empty[Machine]
    machines.foreach(machine => if (machine.tryClose()) {
      closed += machine
      if (machine.host.getEnvironmentLevel == null || !machine.host.getEnvironmentLevel.blockExists(BlockPosition(machine.host))) {
        if (machine.node != null) machine.node.remove()
      }
    })
    machines --= closed
  }

  @SubscribeEvent
  @OnlyIn(Dist.CLIENT)
  def onScreenOpening(e: ScreenEvent.Opening): Unit = {
    if (e.getScreen.isPauseScreen) {
      setSinglePlayerPause(true)
    }
  }

  @SubscribeEvent
  @OnlyIn(Dist.CLIENT)
  def onScreenClosing(e: ScreenEvent.Closing): Unit = {
    if (e.getScreen.isPauseScreen) {
      setSinglePlayerPause(false)
      pendingClient.synchronized {
        pendingClient += { () =>
          if (!Minecraft.getInstance.isPaused) {
            setSinglePlayerPause(false)
          }
        }
      }
    }
  }

  private def setSinglePlayerPause(paused: Boolean): Unit = {
    if (paused != SinglePlayerPause.isPaused) {
      SinglePlayerPause.isPaused = paused
    }
  }

  @SubscribeEvent
  def onClientTick(e: ClientTickEvent.Pre): Unit = {
    pendingClient.synchronized {
      val adds = pendingClient.toArray
      pendingClient.clear()
      adds
    } foreach (callback => {
      try callback() catch {
        case t: Throwable => OpenComputers.log.warn("Error in scheduled tick action.", t)
      }
    })
  }

  @SubscribeEvent
  def playerLoggedIn(e: PlayerLoggedInEvent): Unit = {
    if (SideTracker.isServer) e.getEntity match {
      case _: FakePlayer => // Nope
      case player: ServerPlayer =>
        // 1.21.1 移除：原实现会在原生 Lua 不可用时提示「已回退到 LuaJ」。
        // 原生 Lua（`server/machine/luac`）已整体移出编译集（见 `src/main/scala-pending`），
        // 本版只有 LuaJ 一种架构，因此这条告警不再有意义。
        // Gaaah, MC 1.8 y u do this to me? Sending the packets here directly can lead to them
        // arriving on the client before it has a world and player instance, which causes all
        // sorts of trouble. It worked perfectly fine in MC 1.7.10... oSWDEG'PIl;dg'poinEG\a'pi=
        EventHandler.scheduleServer(() => {
          ServerPacketSender.sendPetVisibility(None, Some(player))
          ServerPacketSender.sendLootDisks(player)
        })
        // Do update check in local games and for OPs.
        val server = ServerLifecycleHooks.getCurrentServer
        if (!server.isDedicatedServer || server.getPlayerList.isOp(player.getGameProfile)) {
          Future {
            UpdateCheck.info foreach {
              case Some(release) => player.sendSystemMessage(Localization.Chat.InfoNewVersion(release.tag_name))
              case _ =>
            }
          }
        }
      case _ =>
    }
  }

  @SubscribeEvent
  @OnlyIn(Dist.CLIENT)
  def clientLoggedIn(e: ClientPlayerNetworkEvent.LoggingIn): Unit = {
    PetRenderer.isInitialized = false
    PetRenderer.hidden.clear()
    Loot.disksForClient.clear()
    Loot.disksForCyclingClient.clear()

    client.Sound.startLoop(null, "computer_running", 0f)
    scheduleServer(() => client.Sound.stopLoop(null))
  }

  @SubscribeEvent
  def onBlockBreak(e: BlockEvent.BreakEvent): Unit = {
    e.getLevel.getBlockEntity(e.getPos) match {
      case c: blockentity.Case =>
        if (c.isCreative && (!e.getPlayer.isCreative || !c.canInteract(e.getPlayer.getName.getString))) {
          e.setCanceled(true)
        }
      case r: blockentity.RobotProxy =>
        val robot = r.robot
        if (robot.isCreative && (!e.getPlayer.isCreative || !robot.canInteract(e.getPlayer.getName.getString))) {
          e.setCanceled(true)
        }
      case _ =>
    }
  }

  @SubscribeEvent
  def onPlayerRespawn(e: PlayerRespawnEvent): Unit = {
    keyboards.asScala.foreach(_.releasePressedKeys(e.getEntity))
  }

  @SubscribeEvent
  def onPlayerChangedDimension(e: PlayerChangedDimensionEvent): Unit = {
    keyboards.asScala.foreach(_.releasePressedKeys(e.getEntity))
  }

  @SubscribeEvent
  def onPlayerLogout(e: PlayerLoggedOutEvent): Unit = {
    keyboards.asScala.foreach(_.releasePressedKeys(e.getEntity))
  }

  @SubscribeEvent
  def onEntityJoinLevel(e: EntityJoinLevelEvent): Unit = {
    if (Settings.get.giveManualToNewPlayers && !e.getLevel.isClientSide) e.getEntity match {
      case player: Player if !player.isInstanceOf[FakePlayer] =>
        val persistedData = PlayerUtils.persistedData(player)
        if (!persistedData.getBoolean(Settings.namespace + "receivedManual")) {
          persistedData.putBoolean(Settings.namespace + "receivedManual", true)
          player.getInventory.add(api.Items.get(Constants.ItemName.Manual).createItemStack(1))
        }
      case _ =>
    }
  }

  lazy val drone: ItemInfo = api.Items.get(Constants.ItemName.Drone)
  lazy val eeprom: ItemInfo = api.Items.get(Constants.ItemName.EEPROM)
  lazy val mcu: ItemInfo = api.Items.get(Constants.BlockName.Microcontroller)
  lazy val navigationUpgrade: ItemInfo = api.Items.get(Constants.ItemName.NavigationUpgrade)
  lazy val robot: ItemInfo = api.Items.get(Constants.BlockName.Robot)
  lazy val tablet: ItemInfo = api.Items.get(Constants.ItemName.Tablet)

  @SubscribeEvent
  def onCrafting(e: ItemCraftedEvent): Unit = {
    var didRecraft = false

    didRecraft = recraft(e, navigationUpgrade, stack => {
      // Restore the map currently used in the upgrade.
      Option(api.Driver.driverFor(e.getCrafting)) match {
        case Some(driver) => StackOption(ItemStack.parseOptional(li.cil.oc.util.RegistryAccessHelper.getOrEmpty(), driver.dataTag(stack).getCompound(Settings.namespace + "map")))
        case _ => EmptyStack
      }
    }) || didRecraft

    didRecraft = recraft(e, mcu, stack => {
      // Restore EEPROM currently used in microcontroller.
      new MicrocontrollerData(stack).components.find(api.Items.get(_) == eeprom).asStackOption
    }) || didRecraft

    didRecraft = recraft(e, drone, stack => {
      // Restore EEPROM currently used in drone.
      new MicrocontrollerData(stack).components.find(api.Items.get(_) == eeprom).asStackOption
    }) || didRecraft

    didRecraft = recraft(e, robot, stack => {
      // Restore EEPROM currently used in robot.
      new RobotData(stack).components.find(api.Items.get(_) == eeprom).asStackOption
    }) || didRecraft

    didRecraft = recraft(e, tablet, stack => {
      // Restore EEPROM currently used in tablet.
      new TabletData(stack).items.collect { case item if !item.isEmpty => item }.find(api.Items.get(_) == eeprom).asStackOption
    }) || didRecraft

    // Presents?
    e.getEntity match {
      case _: FakePlayer => // No presents for you, automaton. Such discrimination. Much bad conscience.
      case player: ServerPlayer if player.level != null && !player.level.isClientSide =>
        // Presents!? If we didn't recraft, it's an OC item, and the time is right...
        if (Settings.get.presentChance > 0 && !didRecraft && api.Items.get(e.getCrafting) != null &&
          e.getEntity.getRandom.nextFloat() < Settings.get.presentChance && timeForPresents) {
          // Presents!
          val present = api.Items.get(Constants.ItemName.Present).createItemStack(1)
          e.getEntity.level.playSound(e.getEntity, e.getEntity.getX, e.getEntity.getY, e.getEntity.getZ, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 0.2f, 1f)
          InventoryUtils.addToPlayerInventory(present, e.getEntity)
        }
      case _ => // Nope.
    }

    Achievement.onCraft(e.getCrafting, e.getEntity)
  }

  /**
   * 玩家捡起物品。
   *
   * 1.21.1 迁移：Forge 1.20 的 `ItemPickupEvent` 已被 NeoForge 移除，对应事件是
   * [[net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent]]，且只有
   * `Pre` / `Post` 两个子类。这里关心的是「捡起之后」，所以监听 `Post`：
   *  - `getOriginalEntity` → `getItemEntity`（物品实体）
   *  - `getEntity` → `getPlayer`
   *  - 被捡起的堆叠直接从 `getOriginalStack` 取，不必再绕物品实体。
   */
  @SubscribeEvent
  def onPickup(e: ItemEntityPickupEvent.Post): Unit = {
    val stack = e.getOriginalStack
    if (stack != null && !stack.isEmpty) {
      Achievement.onAssemble(stack, e.getPlayer)
      Achievement.onCraft(stack, e.getPlayer)
    }
  }

  private def timeForPresents = {
    val now = Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
    // On the 12th day of Christmas, my robot brought to me~
    (month == Calendar.DECEMBER && dayOfMonth > 24) || (month == Calendar.JANUARY && dayOfMonth < 7) ||
      (month == Calendar.FEBRUARY && dayOfMonth == 14) ||
      (month == Calendar.APRIL && dayOfMonth == 22) ||
      (month == Calendar.MAY && dayOfMonth == 1) ||
      (month == Calendar.OCTOBER && dayOfMonth == 3) ||
      (month == Calendar.DECEMBER && dayOfMonth == 14)
  }

  def isItTime: Boolean = {
    val now = Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
    month == Calendar.APRIL && dayOfMonth == 1
  }

  private def recraft(e: ItemCraftedEvent, item: ItemInfo, callback: ItemStack => StackOption): Boolean = {
    if (api.Items.get(e.getCrafting) == item) {
      for (slot <- 0 until e.getInventory.getContainerSize) {
        val stack = e.getInventory.getItem(slot)
        if (api.Items.get(stack) == item) {
          callback(stack).foreach(extra =>
            InventoryUtils.addToPlayerInventory(extra, e.getEntity))
        }
      }
      true
    }
    else false
  }

  // 1.21.1 移除：原 `getChunks(world: ServerLevel)` 会遍历 `ChunkMap#getChunks`。
  // 1.21.1 的 `ChunkMap#getChunks()` 是 `protected`，外部（Scala 的无关类）无法访问，
  // 且它在本类里本来就没有任何调用点，因此直接删除。

  // This is called from the ServerThread *and* the ClientShutdownThread, which
  // can potentially happen at the same time... for whatever reason. So let's
  // synchronize what we're doing here to avoid race conditions (e.g. when
  // disposing networks, where this actually triggered an assert).
  @SubscribeEvent
  def onWorldUnload(e: LevelEvent.Unload): Unit = this.synchronized {
    val level = e.getLevel

    if (!level.isClientSide) {
      val serverLevel = level.asInstanceOf[ServerLevel]

      // 1.21.1 迁移说明：原实现在这里遍历 `ChunkMap#getChunks`，对本维度所有已加载
      // 区块里的 `BaseBlockEntity` 调 `dispose()`。1.21.1 没有公开的「枚举本维度所有已加载
      // 区块」接口（`ChunkMap#getChunks()` 是 protected，`ServerChunkCache` 也没有对应方法），
      // 因此这一段改为依赖 `BaseBlockEntity` 在区块卸载 / 被移除时自身回调的 `dispose()`
      // （见 `onChunkUnloaded`）。语义差异：世界卸载时若某个区块没有单独触发卸载事件，
      // 其方块实体可能不会在此处被 dispose —— 属已知降级点。
      serverLevel.getAllEntities.asScala.foreach {
        case host: MachineHost => host.machine.stop()
        case _ =>
      }

      Callbacks.clear()
    } else {
      TerminalServer.loaded.clear()
    }
  }

  @SubscribeEvent
  def onChunkUnloaded(e: ChunkEvent.Unload): Unit = {
    val levelAccessor = e.getLevel

    if (!levelAccessor.isClientSide && levelAccessor.isInstanceOf[Level]) {
      val level = levelAccessor.asInstanceOf[Level]

      e.getChunk match {
        case chunk: LevelChunk =>
          chunk.getBlockEntities.values().asScala.foreach {
            case host: MachineHost => host.machine match {
              case machine: Machine => scheduleClose(machine)
              case _ =>
            }
            case rack: Rack =>
              (0 until rack.getContainerSize)
                .map(rack.getMountable)
                .foreach {
                  case server: Server if server.machine != null => server.machine.stop()
                  case _ =>
                }
            case _ =>
          }
          val chunkPos = chunk.getPos
          val aabb = new AABB(
            chunkPos.getMinBlockX, level.getMinBuildHeight, chunkPos.getMinBlockZ,
            chunkPos.getMaxBlockX, level.getMaxBuildHeight, chunkPos.getMaxBlockZ
          )
          level.getEntitiesOfClass(classOf[Entity], aabb).asScala.foreach {
            case host: MachineHost => host.machine match {
              case machine: Machine => scheduleClose(machine)
              case _ =>
            }
            case _ =>
          }

        case _ =>
      }
    }
  }
}
