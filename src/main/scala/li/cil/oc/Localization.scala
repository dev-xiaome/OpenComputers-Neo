package li.cil.oc

import net.minecraft.ChatFormatting
import net.minecraft.locale.Language
import net.minecraft.network.chat.{ClickEvent, Component, HoverEvent}

import scala.util.matching.Regex

/**
 * 本地化辅助（原 1.7.10 使用 `StatCollector` + `Component`）。
 *
 * 1.21.1 迁移要点：
 *  - `StatCollector.canTranslate` → `Language.getInstance().has`
 *  - `ChatComponentTranslation` → `Component.translatable`
 *  - `ChatComponentText` → `Component.literal`
 *  - `getChatStyle.setChatClickEvent(...)` → `withStyle` 链式设置 `ClickEvent` / `HoverEvent`
 */
object Localization {
  private val nl = Regex.quote("[nl]")

  /** 客户端剪贴板命令名（原 `li.cil.oc.client.CommandHandler.SetClipboardCommand.name`）。 */
  private final val SetClipboardCommandName = "oc_setClipboard"

  private def resolveKey(key: String): Option[String] =
    if (canLocalize(Settings.namespace + key)) Option(Settings.namespace + key)
    else if (canLocalize(key)) Option(key)
    else Option.empty

  def canLocalize(key: String): Boolean = Language.getInstance.has(key)

  def localizeLater(formatKey: String, values: AnyRef*): Component =
    Component.translatable(resolveKey(formatKey).getOrElse(formatKey), values: _*)

  def localizeLater(key: String): Component =
    resolveKey(key).map(k => Component.translatable(k)).getOrElse(Component.literal(key))

  def localizeImmediately(formatKey: String, values: AnyRef*): String =
    Component.translatable(resolveKey(formatKey).getOrElse(formatKey), values: _*).getString
      .split(nl).map(_.trim).mkString("\n")

  def localizeImmediately(key: String): String =
    resolveKey(key).map(k => Component.translatable(k).getString).getOrElse(key)
      .split(nl).map(_.trim).mkString("\n")

  /** 便捷构造：给组件加上颜色前缀。 */
  private def prefixed(prefix: String, body: Component): Component =
    Component.literal(prefix).append(body)

  private def ocChat(body: Component): Component =
    prefixed("§aOpenComputers§f: ", body)

  object Analyzer {
    def Address(value: String): Component = {
      localizeLater("gui.Analyzer.Address", value).copy().withStyle(style => style
        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, s"/$SetClipboardCommandName $value"))
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, localizeLater("gui.Analyzer.CopyToClipboard"))))
    }

    def AddressCopied: Component = localizeLater("gui.Analyzer.AddressCopied")

    def ChargerSpeed(value: Double): Component = localizeLater("gui.Analyzer.ChargerSpeed", (value * 100).toInt + "%")

    def ComponentName(value: String): Component = localizeLater("gui.Analyzer.ComponentName", value)

    def Components(count: Int, maxCount: Int): Component = localizeLater("gui.Analyzer.Components", count + "/" + maxCount)

    def LastError(value: String): Component = localizeLater("gui.Analyzer.LastError", localizeLater(value))

    def RobotOwner(owner: String): Component = localizeLater("gui.Analyzer.RobotOwner", owner)

    def RobotName(name: String): Component = localizeLater("gui.Analyzer.RobotName", name)

    def RobotXp(experience: Double, level: Int): Component = localizeLater("gui.Analyzer.RobotXp", f"$experience%.2f", level.toString)

    def StoredEnergy(value: String): Component = localizeLater("gui.Analyzer.StoredEnergy", value)

    def TotalEnergy(value: String): Component = localizeLater("gui.Analyzer.TotalEnergy", value)

    def Users(list: Iterable[String]): Component = localizeLater("gui.Analyzer.Users", list.mkString(", "))

    def WirelessStrength(value: Double): Component = localizeLater("gui.Analyzer.WirelessStrength", value.toInt.toString)
  }

  object Assembler {
    def InsertTemplate: String = localizeImmediately("gui.Assembler.InsertCase")

    def CollectResult: String = localizeImmediately("gui.Assembler.Collect")

    def InsertCPU: Component = localizeLater("gui.Assembler.InsertCPU")

    def InsertRAM: Component = localizeLater("gui.Assembler.InsertRAM")

    def Complexity(complexity: Int, maxComplexity: Int): Component = {
      val message = localizeLater("gui.Assembler.Complexity", complexity.toString, maxComplexity.toString)
      if (complexity > maxComplexity) Component.literal("§4").append(message)
      else message
    }

    def Run: String = localizeImmediately("gui.Assembler.Run")

    def Progress(progress: Double, timeRemaining: String): String = localizeImmediately("gui.Assembler.Progress", progress.toInt.toString, timeRemaining)

    def Warning(name: String): Component = Component.literal("§7- ").append(localizeLater("gui.Assembler.Warning." + name))

    def Warnings: Component = localizeLater("gui.Assembler.Warnings")
  }

  object Chat {
    def WarningLuaFallback: Component = ocChat(localizeLater("gui.Chat.WarningLuaFallback"))

    def WarningProjectRed: Component = ocChat(localizeLater("gui.Chat.WarningProjectRed"))

    def WarningRecipes: Component = ocChat(localizeLater("gui.Chat.WarningRecipes"))

    def WarningClassTransformer: Component = ocChat(localizeLater("gui.Chat.WarningClassTransformer"))

    def WarningSimpleComponent: Component = ocChat(localizeLater("gui.Chat.WarningSimpleComponent"))

    def WarningLink(url: String): Component = ocChat(localizeLater("gui.Chat.WarningLink", url))

    def InfoNewVersion(version: String): Component = ocChat(localizeLater("gui.Chat.NewVersion", version))

    def TextureName(name: String): Component = ocChat(localizeLater("gui.Chat.TextureName", name))
  }

  object Computer {
    def TurnOff: String = localizeImmediately("gui.Robot.TurnOff")

    def TurnOn: String = localizeImmediately("gui.Robot.TurnOn")

    def Power: String = localizeImmediately("gui.Robot.Power")
  }

  object Drive {
    def Managed: String = localizeImmediately("gui.Drive.Managed")

    def Unmanaged: String = localizeImmediately("gui.Drive.Unmanaged")

    def Warning: String = localizeImmediately("gui.Drive.Warning")

    def ReadOnlyLock: String = localizeImmediately("gui.Drive.ReadOnlyLock")

    def LockWarning: String = localizeImmediately("gui.Drive.ReadOnlyLockWarning")
  }

  object Raid {
    def Warning: String = localizeImmediately("gui.Raid.Warning")
  }

  object Rack {
    def Top: String = localizeImmediately("gui.Rack.Top")

    def Bottom: String = localizeImmediately("gui.Rack.Bottom")

    def Left: String = localizeImmediately("gui.Rack.Left")

    def Right: String = localizeImmediately("gui.Rack.Right")

    def Back: String = localizeImmediately("gui.Rack.Back")

    def None: String = localizeImmediately("gui.Rack.None")

    def RelayEnabled: String = localizeImmediately("gui.Rack.Enabled")

    def RelayDisabled: String = localizeImmediately("gui.Rack.Disabled")

    def RelayModeTooltip: String = localizeImmediately("gui.Rack.RelayModeTooltip")

    def OrientationTooltip: String = localizeImmediately("gui.Rack.OrientationTooltip")
  }

  object Switch {
    def TransferRate: String = localizeImmediately("gui.Switch.TransferRate")

    def PacketsPerCycle: String = localizeImmediately("gui.Switch.PacketsPerCycle")

    def QueueSize: String = localizeImmediately("gui.Switch.QueueSize")
  }

  object Terminal {
    def InvalidKey: Component = localizeLater("gui.Terminal.InvalidKey")

    def OutOfRange: Component = localizeLater("gui.Terminal.OutOfRange")
  }

  object Tooltip {
    def DiskUsage(used: Long, capacity: Long): String = localizeImmediately("tooltip.DiskUsage", used.toString, capacity.toString)

    def DiskMode(isUnmanaged: Boolean): String = localizeImmediately(if (isUnmanaged) "tooltip.DiskModeUnmanaged" else "tooltip.DiskModeManaged")

    def DiskLock(lockInfo: String): String = if (lockInfo.isEmpty) "" else localizeImmediately("tooltip.DiskLocked", lockInfo)

    def Materials: String = localizeImmediately("tooltip.Materials")

    def Tier(tier: Int): String = localizeImmediately("tooltip.Tier", tier.toString)

    def PrintBeaconBase: String = localizeImmediately("tooltip.Print.BeaconBase")

    def PrintLightValue(level: Int): String = localizeImmediately("tooltip.Print.LightValue", level.toString)

    def PrintRedstoneLevel(level: Int): String = localizeImmediately("tooltip.Print.RedstoneLevel", level.toString)

    def MFULinked(isLinked: Boolean): String = localizeImmediately(if (isLinked) "tooltip.UpgradeMF.Linked" else "tooltip.UpgradeMF.Unlinked")
  }

  /** 格式化辅助，供其它模块复用。 */
  def format(value: String): String = value

  /** 颜色常量便捷引用。 */
  val darkRed: ChatFormatting = ChatFormatting.DARK_RED
}
