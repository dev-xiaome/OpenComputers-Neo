package li.cil.oc.common

import java.util.Calendar

import li.cil.oc._
import li.cil.oc.api.Network
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.internal.Server
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.common.component.TerminalServer
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.item.data.StackSerializer
import li.cil.oc.common.item.data.TabletData
import li.cil.oc.common.recipe.Recipes
import li.cil.oc.common.tileentity.Robot
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.WirelessRedstone
import li.cil.oc.integration.util.Wrench
import li.cil.oc.server.component.Keyboard
import li.cil.oc.server.machine.Callbacks
import li.cil.oc.server.machine.Machine
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util._
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * 全局事件处理器：把「延迟到下一个 tick 执行的动作」集中起来，并处理机器人 tick、
 * 玩家登录 / 登出、合成重做（re-craft）等杂项。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` + `MinecraftForge.EVENT_BUS.register` 改为 [[initialize]] 里显式
 *    `NeoForge.EVENT_BUS.addListener`：NeoForge 对 Scala object 的注解扫描不可靠，
 *    注解一旦没被扫到就是静默失效。
 *  - `TickEvent.Phase.START / END` 拆成 `ServerTickEvent.Pre / Post` 两个监听器。
 *  - 客户端专用监听器（客户端 tick、连接服务器、宠物渲染器 / 声音循环复位）已整体搬到
 *    `li.cil.oc.client.ClientListeners`，只在物理客户端由
 *    [[li.cil.oc.common.ClientHooks.initializeClientListeners]] 软引用注册：
 *    `common` 包不能有对 `client` 包的编译期引用，否则整个 `client` 包会被拖进
 *    增量编译集（详见 `common/ClientHooks.scala` 的说明）。
 *  - `cpw.mods.fml.common.gameevent.PlayerEvent.*` → `net.neoforged.neoforge.event.entity.player.PlayerEvent.*`，
 *    getter 名统一改为 `getEntity`。
 *  - `EntityJoinWorldEvent` → [[net.neoforged.neoforge.event.entity.EntityJoinLevelEvent]]。
 *  - `WorldEvent.*` → `LevelEvent.*`；`BlockEvent.BreakEvent` 的坐标改为 `getPos`。
 *  - `ItemCraftedEvent.getCraftMatrix` 已移除，改用 `getInventory`（`Container`）。
 *  - 第三方能量 / 多方块集成（AE2、IC2、ForgeMultipart）整体移除，
 *    `scheduleFMP` / `scheduleAE2Add` / `scheduleIC2Add` 一并删除（见 docs/PORTING.md）。
 *  - `common.asm.ClassTransformer` 随 ASM coremod 一起删除，相关告警不再发送。
 *
 * 成就（Achievement）相关的调用已删除：1.21.1 用 advancement 取代 achievement，
 * 需要数据包 JSON + 触发器，`common/Achievement.scala` 已整体删除（详见汇报与 docs/PROGRESS.md）。
 */
object EventHandler {
  private val pendingServer = mutable.Buffer.empty[() => Unit]

  private val pendingClient = mutable.Buffer.empty[() => Unit]

  private val runningRobots = mutable.Set.empty[Robot]

  private val keyboards = java.util.Collections.newSetFromMap[Keyboard](new java.util.WeakHashMap[Keyboard, java.lang.Boolean])

  private val machines = mutable.Set.empty[Machine]

  def onRobotStart(robot: Robot): Unit = runningRobots += robot

  def onRobotStopped(robot: Robot): Unit = runningRobots -= robot

  // 注意：`keyboards` 是 Java 集合，Scala 2.13 的 `CollectionConverters` 只提供显式的
  // `.asScala`（不像 1.7.10 的 `WrapAsScala` 那样隐式转换），因此增删与遍历都必须写 `.asScala`。
  def addKeyboard(keyboard: Keyboard): Unit = keyboards.asScala += keyboard

  def scheduleClose(machine: Machine): Unit = machines += machine

  def unscheduleClose(machine: Machine): Unit = machines -= machine

  def scheduleServer(tileEntity: BlockEntity): Unit = {
    // 1.7.10 用 `SideTracker.isServer`（有效侧）在这里做判定；1.21.1 的
    // `ServerTickEvent` 只在逻辑服务端触发，集成服务器（单人游戏）同样会触发，
    // 因此不再需要额外判定，去掉它反而修掉了单人游戏下不生效的问题。
    pendingServer.synchronized {
      pendingServer += (() => Network.joinOrCreateNetwork(tileEntity))
    }
  }

  def scheduleServer(f: () => Unit): Unit = {
    pendingServer.synchronized {
      pendingServer += f
    }
  }

  def scheduleClient(f: () => Unit): Unit = {
    pendingClient.synchronized {
      pendingClient += f
    }
  }

  def scheduleWirelessRedstone(rs: server.component.RedstoneWireless): Unit = {
    pendingServer.synchronized {
      pendingServer += (() => if (rs.node.network != null) {
        WirelessRedstone.addReceiver(rs)
        WirelessRedstone.updateOutput(rs)
      })
    }
  }

  def onServerTickStart(): Unit = {
    pendingServer.synchronized {
      val adds = pendingServer.toArray
      pendingServer.clear()
      adds
    } foreach (callback => {
      try callback() catch {
        case t: Throwable => OpenComputers.log.warn("Error in scheduled tick action.", t)
      }
    })

    val invalid = mutable.ArrayBuffer.empty[Robot]
    runningRobots.foreach(robot => {
      if (robot.isRemoved) invalid += robot
      // TODO(server.machine): 机器层移植前 `machine` 会返回 null，这里做空值保护。
      else if (robot.world != null && robot.machine != null) robot.machine.update()
    })
    runningRobots --= invalid
  }

  def onServerTickEnd(): Unit = {
    // 在一个 tick *之后* 清理机器，先给存档留出机会。
    val closed = mutable.ArrayBuffer.empty[Machine]
    machines.foreach(machine => if (machine.tryClose()) {
      closed += machine
      val host = machine.host
      if (host.world() == null || !host.world().blockExists(BlockPosition(host))) {
        if (machine.node != null) machine.node.remove()
      }
    })
    machines --= closed
  }

  def onClientTick(): Unit = {
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
  def playerLoggedIn(e: PlayerEvent.PlayerLoggedInEvent): Unit = {
    e.getEntity match {
      case _: FakePlayer => // 不处理。
      case player: ServerPlayer =>
        // 原实现会在这里提示「原生 Lua 不可用，已回退到 LuaJ」。
        // 原生 Lua（`server/machine/luac`）按 docs/PROGRESS.md 第 7 条不再移植，
        // 只有 LuaJ 一种架构，因此这条告警已删除。
        if (Recipes.hadErrors) {
          player.sendSystemMessage(Localization.Chat.WarningRecipes)
        }
        // 原实现还会发送 ClassTransformer / SimpleComponent 的告警；
        // ASM coremod 与 SimpleComponent 模板已整体删除，这两条告警随之移除。
        ServerPacketSender.sendPetVisibility(None, Some(player))
        ServerPacketSender.sendLootDisks(player)
        // 只在本地游戏与 OP 身上做更新检查。
        val server = player.getServer
        if (!Mods.VersionChecker.isAvailable &&
          (server == null || !server.isDedicatedServer || server.getPlayerList.isOp(player.getGameProfile))) {
          Future {
            // `Future#onSuccess` 在 Scala 2.13 已移除，改用 `foreach`（语义相同）。
            UpdateCheck.info.foreach {
              case Some(release) => player.sendSystemMessage(Localization.Chat.InfoNewVersion(release.tag_name))
              case None =>
            }
          }
        }
      case _ =>
    }
  }

  def onBlockBreak(e: BlockEvent.BreakEvent): Unit = e.getLevel match {
    case world: Level => world.getBlockEntity(e.getPos) match {
      case c: tileentity.Case =>
        if (c.isCreative && (!e.getPlayer.getAbilities.instabuild || !c.canInteract(e.getPlayer.getScoreboardName))) {
          e.setCanceled(true)
        }
      case r: tileentity.RobotProxy =>
        val robot = r.robot
        if (robot.isCreative && (!e.getPlayer.getAbilities.instabuild || !robot.canInteract(e.getPlayer.getScoreboardName))) {
          e.setCanceled(true)
        }
      case _ =>
    }
    case _ =>
  }

  def onPlayerRespawn(e: PlayerEvent.PlayerRespawnEvent): Unit = {
    keyboards.asScala.foreach(_.releasePressedKeys(e.getEntity))
  }

  def onPlayerChangedDimension(e: PlayerEvent.PlayerChangedDimensionEvent): Unit = {
    keyboards.asScala.foreach(_.releasePressedKeys(e.getEntity))
  }

  def onPlayerLogout(e: PlayerEvent.PlayerLoggedOutEvent): Unit = {
    keyboards.asScala.foreach(_.releasePressedKeys(e.getEntity))
  }

  def onEntityJoinLevel(e: EntityJoinLevelEvent): Unit = {
    if (Settings.get.giveManualToNewPlayers && !e.getLevel.isClientSide) e.getEntity match {
      case player: Player if !player.isInstanceOf[FakePlayer] =>
        val persistedData = PlayerUtils.persistedData(player)
        if (!persistedData.getBoolean(Settings.namespace + "receivedManual")) {
          persistedData.putBoolean(Settings.namespace + "receivedManual", true)
          InventoryUtils.addToPlayerInventory(api.Items.get(Constants.ItemName.Manual).createItemStack(1), player)
        }
      case _ =>
    }
  }

  lazy val drone = api.Items.get(Constants.ItemName.Drone)
  lazy val eeprom = api.Items.get(Constants.ItemName.EEPROM)
  lazy val floppy = api.Items.get(Constants.ItemName.Floppy)
  lazy val mcu = api.Items.get(Constants.BlockName.Microcontroller)
  lazy val navigationUpgrade = api.Items.get(Constants.ItemName.NavigationUpgrade)
  lazy val robot = api.Items.get(Constants.BlockName.Robot)
  lazy val tablet = api.Items.get(Constants.ItemName.Tablet)

  def onCrafting(e: PlayerEvent.ItemCraftedEvent): Unit = {
    var didRecraft = false

    didRecraft = recraft(e, navigationUpgrade, stack => {
      // 还原导航升级里当前使用的地图。
      Option(api.Driver.driverFor(e.getCrafting)) match {
        case Some(driver) =>
          Option(StackSerializer.loadItemStack(driver.dataTag(stack).getCompound(Settings.namespace + "map"))).
            filter(!_.isEmpty)
        case _ => None
      }
    }) || didRecraft

    didRecraft = recraft(e, mcu, stack => {
      // 还原单片机里当前使用的 EEPROM。
      new MicrocontrollerData(stack).components.find(api.Items.get(_) == eeprom)
    }) || didRecraft

    didRecraft = recraft(e, drone, stack => {
      // 还原无人机里当前使用的 EEPROM。
      new MicrocontrollerData(stack).components.find(api.Items.get(_) == eeprom)
    }) || didRecraft

    didRecraft = recraft(e, robot, stack => {
      // 还原机器人里当前使用的 EEPROM。
      new RobotData(stack).components.find(api.Items.get(_) == eeprom)
    }) || didRecraft

    didRecraft = recraft(e, tablet, stack => {
      // 还原平板里当前使用的 EEPROM。
      new TabletData(stack).items.collect { case Some(item) => item }.find(api.Items.get(_) == eeprom)
    }) || didRecraft

    didRecraft = {
      if (Loot.isLootDisk(e.getCrafting)) {
        val stacks = (0 until e.getInventory.getContainerSize).
          flatMap(i => Option(e.getInventory.getItem(i)).filter(!_.isEmpty)).
          toArray
        if (stacks.length == 2) stacks.find(Wrench.isWrench) match {
          case Some(stack) =>
            // 1.7.10 的 `stack.stackSize += 1` → 1.21.1 的 `grow`。
            stack.grow(1)
            true
          case _ => didRecraft
        }
        else didRecraft
      }
      else didRecraft
    }

    // 礼物？
    e.getEntity match {
      case _: FakePlayer => // 自动机没有礼物。
      case player: ServerPlayer if !player.level().isClientSide =>
        // 没有重做、是 OC 的物品、而且时间正好……
        if (Settings.get.presentChance > 0 && !didRecraft && api.Items.get(e.getCrafting) != null &&
          player.getRandom.nextFloat() < Settings.get.presentChance && timeForPresents) {
          val present = api.Items.get(Constants.ItemName.Present).createItemStack(1)
          player.level().playSound(null, player.getX, player.getY, player.getZ,
            SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.2f, 1f)
          InventoryUtils.addToPlayerInventory(present, player)
        }
      case _ => // 不处理。
    }

    // 成就系统已删除：原实现在这里调用 `Achievement.onCraft(e.crafting, e.player)`。
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

  def isItTime = {
    val now = Calendar.getInstance()
    val month = now.get(Calendar.MONTH)
    val dayOfMonth = now.get(Calendar.DAY_OF_MONTH)
    month == Calendar.APRIL && dayOfMonth == 1
  }

  private def recraft(e: PlayerEvent.ItemCraftedEvent, item: ItemInfo, callback: ItemStack => Option[ItemStack]): Boolean = {
    if (api.Items.get(e.getCrafting) == item) {
      for (slot <- 0 until e.getInventory.getContainerSize) {
        val stack = e.getInventory.getItem(slot)
        if (stack != null && !stack.isEmpty && api.Items.get(stack) == item) {
          callback(stack).foreach(extra =>
            InventoryUtils.addToPlayerInventory(extra, e.getEntity))
        }
      }
      true
    }
    else false
  }

  /**
   * 世界卸载。
   *
   * 原实现会遍历 `loadedTileEntityList` 逐个 `dispose()`；1.21.1 没有公开的
   * 「枚举所有已加载方块实体」接口，改为依赖 [[li.cil.oc.common.tileentity.BlockEntityBase]]
   * 在 `onChunkUnloaded` / `setRemoved` 里回调的 `dispose()`。
   *
   * 这个方法既会被服务端线程调用，也会被客户端关闭线程调用，两者可能同时发生，
   * 因此整体加锁，避免竞争（例如销毁网络时触发断言）。
   */
  def onWorldUnload(e: LevelEvent.Unload): Unit = this.synchronized {
    e.getLevel match {
      case level: Level if level.isClientSide =>
        TerminalServer.loaded.clear()
      case level: ServerLevel =>
        level.getAllEntities.asScala.foreach {
          case host: MachineHost if host.machine != null => host.machine.stop()
          case _ =>
        }
        Callbacks.clear()
      case _ =>
    }
  }

  def onChunkUnload(e: ChunkEvent.Unload): Unit = {
    if (!e.getLevel.isClientSide) e.getChunk match {
      case chunk: LevelChunk =>
        chunk.getBlockEntities.values.asScala.foreach {
          case rack: Rack =>
            // 原实现还会遍历区块里的实体列表，把 MachineHost 的机器加入待关闭队列；
            // 1.21.1 的区块不再持有实体列表，实体那部分改由 [[onEntityLeaveLevel]] 处理。
            for (slot <- 0 until rack.getSlots) rack.getMountable(slot) match {
              case server: Server if server.machine != null => server.machine.stop()
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
  }

  /** 实体离开世界（区块卸载或实体被移除）时关闭它上面的机器。 */
  def onEntityLeaveLevel(e: EntityLeaveLevelEvent): Unit = {
    if (!e.getLevel.isClientSide) e.getEntity match {
      case host: MachineHost => host.machine match {
        case machine: Machine => scheduleClose(machine)
        case _ => // 机器实现未知，忽略。
      }
      case _ =>
    }
  }

  /**
   * 注册**服务端 / 双端通用**的监听器；由 [[li.cil.oc.common.event.EventHandlers]] 调用一次。
   *
   * 客户端专用监听器不在本方法里注册（1.7.10 里它们在 `Client` 子对象中）：
   * 1.21.1 的增量编译集不允许 `common` 包出现对 `client` 包的编译期引用，因此它们已搬到
   * `li.cil.oc.client.ClientListeners`，由
   * [[li.cil.oc.common.ClientHooks.initializeClientListeners]] 在物理客户端上软引用注册。
   */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: ServerTickEvent.Pre) => onServerTickStart())
    NeoForge.EVENT_BUS.addListener((e: ServerTickEvent.Post) => onServerTickEnd())
    NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerLoggedInEvent) => playerLoggedIn(e))
    NeoForge.EVENT_BUS.addListener((e: BlockEvent.BreakEvent) => onBlockBreak(e))
    NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerRespawnEvent) => onPlayerRespawn(e))
    NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerChangedDimensionEvent) => onPlayerChangedDimension(e))
    NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerLoggedOutEvent) => onPlayerLogout(e))
    NeoForge.EVENT_BUS.addListener((e: EntityJoinLevelEvent) => onEntityJoinLevel(e))
    NeoForge.EVENT_BUS.addListener((e: PlayerEvent.ItemCraftedEvent) => onCrafting(e))
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (e: LevelEvent.Unload) => onWorldUnload(e))
    NeoForge.EVENT_BUS.addListener((e: ChunkEvent.Unload) => onChunkUnload(e))
    NeoForge.EVENT_BUS.addListener((e: EntityLeaveLevelEvent) => onEntityLeaveLevel(e))

    // 客户端专属监听器（客户端 tick、登录清理、宠物渲染器 / 声音循环复位）已整体搬到
    // `li.cil.oc.client.ClientListeners`：`common` 包不能有对 `client` 包的编译期引用，
    // 否则整个 `client` 包会被拖进增量编译集。注册由
    // [[li.cil.oc.common.ClientHooks.initializeClientListeners]] 在物理客户端上软引用完成。
  }
}
