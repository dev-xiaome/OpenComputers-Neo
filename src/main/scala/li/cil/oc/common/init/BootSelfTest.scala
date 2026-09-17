package li.cil.oc.common.init

import li.cil.oc.OpenComputers
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * 无人值守的**开机链路自检**（临时脚手架，**默认完全关闭**）。
 *
 * 单独放一个文件、而不是写在 `common/EventHandler.scala` 里，是为了让 `EventHandler`
 * 保持与 OCCE 逐行对齐 —— 那边的代码里没有这些诊断逻辑。
 *
 * ==为什么需要它==
 * 这个项目在移植期最头疼的问题是「机箱开不了机」：存档里的机器是带着上次运行状态一起载入的，
 * 载入后不会再走 `Machine#start`，所以无人值守跑 `runClient` 时**根本看不到 BIOS / OpenOS 的
 * 启动日志**。于是就有了这套钩子，配合：
 *
 * {{{
 *   gradlew runClient -PquickPlay=<存档目录名> -PfillComponents
 * }}}
 *
 * 可以做到：自动进入存档 → 自动把附近机箱关机再开机（等于替玩家按一次电源键）→
 * 分几次把屏幕文本缓冲整屏打进日志 → 最后一次强制存盘，便于用外部工具核对存档内容。
 *
 * ==开关（都不设时本文件不做任何事）==
 *  - `-Doc.autoboot=true`：启用「登录时重启附近机箱 + 定时 dump 屏幕」。
 *  - `-Doc.fillcomponents=true`：重启前先往机箱里塞一整套能开机的组件
 *    （APU + 内存 + 硬盘 + BIOS + OpenOS + 显卡）。用于把「开机链路是否正常」与
 *    「玩家存档里的机箱是否已经被写坏」这两件事分开验证。
 *
 * ==经验教训（免得后人重走弯路）==
 *  - 单人游戏默认「失去焦点就暂停」：窗口在后台时集成服务端**根本不 tick**，机器不会运行。
 *    所以要配合把 `run/options.txt` 里的 `pauseOnLostFocus` 设为 `false`。
 *  - 后台运行时也不会自动存盘，所以这里在最后一次 dump 后显式 `saveEverything`。
 *  - 只读一次屏幕是不够的：OpenOS 加载模块需要时间，可能正好读到空屏，所以要分几次读。
 */
object BootSelfTest {

  private val AutoBoot = "oc.autoboot"

  private val FillComponents = "oc.fillcomponents"

  /** dump 屏幕的时机（开机后经过的 tick 数）。 */
  private val DumpAt = List(200, 600, 1400, 3000)

  private var dumpAt: List[Int] = Nil

  private var dumpElapsed = 0

  private var dumpLevel: ServerLevel = _

  private var dumpCenter: BlockPos = _

  /** 由主类在 mod 构造期调用；未开启开关时不注册任何监听器。 */
  def register(): Unit = {
    if (!java.lang.Boolean.getBoolean(AutoBoot)) return
    OpenComputers.log.info("[OC-DIAG] 开机自检已启用（-Doc.autoboot=true）。")
    NeoForge.EVENT_BUS.addListener((e: PlayerEvent.PlayerLoggedInEvent) => onPlayerLoggedIn(e))
    NeoForge.EVENT_BUS.addListener((e: ServerTickEvent.Post) => onServerTickEnd())
  }

  private def onPlayerLoggedIn(e: PlayerEvent.PlayerLoggedInEvent): Unit = e.getEntity match {
    case player: ServerPlayer => restartNearbyComputers(player)
    case _ =>
  }

  private def onServerTickEnd(): Unit = if (dumpAt.nonEmpty) {
    dumpElapsed += 1
    if (dumpElapsed >= dumpAt.head) {
      dumpAt = dumpAt.tail
      try dumpNearbyScreens() catch {
        case t: Throwable => OpenComputers.log.warn("Error dumping screens.", t)
      }
    }
  }

  /** 扫描玩家身周一小片区域，把找到的机箱关机再开机一次。 */
  private def restartNearbyComputers(player: ServerPlayer): Unit = {
    val level = player.level()
    val origin = player.blockPosition()
    val fill = java.lang.Boolean.getBoolean(FillComponents)
    var found = 0
    for (dx <- -24 to 24; dy <- -12 to 12; dz <- -24 to 24) {
      val pos = origin.offset(dx, dy, dz)
      level.getBlockEntity(pos) match {
        case computer: li.cil.oc.common.tileentity.traits.Computer if computer.machine != null =>
          found += 1
          val slots = computer.getSlots
          val contents = (0 until slots).map(slot => s"$slot=${computer.getStackInSlot(slot).getItem}").mkString(", ")
          OpenComputers.log.info(s"[OC-DIAG] autoboot: 在 $pos 发现机器，tier=${computer match {
            case c: li.cil.oc.common.tileentity.Case => c.tier.toString
            case _ => "?"
          }} slots=$slots 内容=[$contents]")
          if (fill) fillComponents(computer)
          computer.machine.stop()
          computer.machine.start()
        case _ =>
      }
    }
    OpenComputers.log.info(s"[OC-DIAG] autoboot: 扫描完毕，共处理 $found 台机器。")
    if (found > 0) {
      dumpLevel = level match {
        case server: ServerLevel => server
        case _ => null
      }
      dumpCenter = origin
      dumpAt = DumpAt
      dumpElapsed = 0
    }
  }

  /**
   * 把附近屏幕的文本缓冲整屏打印到日志里。
   *
   * 这是判断「BIOS 是否成功加载 OpenOS、内核是否跑起来」最直接的证据 ——
   * 其它日志只能证明「没有报错」，只有屏幕内容能证明「确实跑起来了」。
   */
  private def dumpNearbyScreens(): Unit = {
    val level = dumpLevel
    val origin = dumpCenter
    if (level == null || origin == null) return
    var screens = 0
    OpenComputers.log.info(s"[OC-DIAG] ===== 屏幕 dump（开机后第 $dumpElapsed tick）=====")
    for (dx <- -24 to 24; dy <- -12 to 12; dz <- -24 to 24) {
      val pos = origin.offset(dx, dy, dz)
      level.getBlockEntity(pos) match {
        case screen: li.cil.oc.common.tileentity.traits.TextBuffer =>
          val buffer = screen.buffer
          if (buffer != null) {
            screens += 1
            OpenComputers.log.info(
              s"[OC-DIAG] 屏幕 $pos 内容（${buffer.getWidth}x${buffer.getHeight} " +
                s"power=${buffer.getPowerState} depth=${buffer.getColorDepth}）:")
            for (row <- 0 until buffer.getHeight) {
              val text = (0 until buffer.getWidth)
                .map(col => buffer.getCodePoint(col, row))
                .takeWhile(_ != 0)
                .map(cp => new String(Character.toChars(cp)))
                .mkString
              OpenComputers.log.info(s"[OC-DIAG]   |$text|")
            }
          }
        case computer: li.cil.oc.common.tileentity.traits.Computer if computer.machine != null =>
          val machine = computer.machine
          val slots = computer.getSlots
          val contents = (0 until slots).map(s => s"$s=${computer.getStackInSlot(s).getItem}").mkString(", ")
          OpenComputers.log.info(
            s"[OC-DIAG] 机器 $pos: isRunning=${machine.isRunning} isPaused=${machine.isPaused} " +
              s"lastError=${Option(machine.lastError).getOrElse("<无>")} " +
              s"components=${machine.components.size} 槽位=[$contents]")
        case _ =>
      }
    }
    OpenComputers.log.info(s"[OC-DIAG] 屏幕 dump 完毕，共 $screens 块屏幕。")
    // 只在最后一次强制存盘，避免反复卡服；存一次盘才能用外部工具核对存档内容。
    if (dumpAt.isEmpty) {
      val server = level.getServer
      if (server != null) {
        server.saveEverything(false, true, true)
        OpenComputers.log.info("[OC-DIAG] 已强制存盘一次。")
      }
    }
  }

  /** 往机箱里塞一整套能开机的组件（清单抄自 `/oc_spawnComputer`，另加一张显卡）。 */
  private def fillComponents(computer: AnyRef): Unit = computer match {
    case be: net.minecraft.world.level.block.entity.BlockEntity if be.getLevel != null =>
      val pos = li.cil.oc.util.BlockPosition(be.getBlockPos.getX, be.getBlockPos.getY, be.getBlockPos.getZ, be.getLevel)
      def give(name: String, count: Int): Unit = {
        val info = li.cil.oc.api.Items.get(name)
        if (info == null) OpenComputers.log.warn(s"[OC-DIAG] autoboot: 找不到物品 $name")
        else li.cil.oc.util.InventoryUtils.insertIntoInventoryAt(info.createItemStack(count), pos)
      }
      give(li.cil.oc.Constants.ItemName.APUCreative, 1)
      give(li.cil.oc.Constants.ItemName.RAMTier6, 2)
      give(li.cil.oc.Constants.ItemName.HDDTier3, 1)
      give(li.cil.oc.Constants.ItemName.LuaBios, 1)
      give(li.cil.oc.Constants.ItemName.OpenOS, 1)
      give(li.cil.oc.Constants.ItemName.GraphicsCardTier3, 1)
      OpenComputers.log.info("[OC-DIAG] autoboot: 已尝试填充一整套组件。")
    case _ =>
  }
}
