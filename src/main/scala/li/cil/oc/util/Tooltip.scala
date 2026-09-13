package li.cil.oc.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.{FormattedCharSequence, FormattedCharSink}

import scala.jdk.CollectionConverters._

/**
 * 物品提示（tooltip）构建工具。
 *
 * 1.21.1 迁移要点：
 *  - `Minecraft.getMinecraft` → `Minecraft.getInstance()`，`fontRenderer` → `font`
 *  - `Font#getStringWidth` → `Font#width`
 *  - `Font#listFormattedStringToWidth` → `Font#split(FormattedText, width)`，
 *    返回 `FormattedCharSequence`，这里统一转回普通字符串
 *  - 行尾不再追加空格（1.21.1 的换行器已保证渲染宽度，不再依赖空格避免单词粘连）
 *  - 返回值仍为 `java.util.List[String]`，兼容既有调用方
 *  - `Font#split` / `Font#width` 仍返回普通宽度单位，`maxWidth` 沿用旧值即可
 *
 * TODO(客户端): 快捷键提示依赖 [[li.cil.oc.client.KeyBindings]]，而 `li.cil.oc.client`
 * 包尚未移植（见 docs/PORTING.md 的移植顺序第 6 步）。这里用
 * [[TooltipKeyBindings]] 占位：不显示快捷键名、`showExtendedTooltips` 恒为 `false`，
 * 等客户端阶段恢复真实实现后删掉该占位对象即可。
 */
object Tooltip {
  private val maxWidth = 220

  private def font = {
    val mc = Minecraft.getInstance
    if (mc == null) null else mc.font
  }

  def get(name: String, args: Any*): java.util.List[String] = {
    if (!Localization.canLocalize(Settings.namespace + "tooltip." + name)) return Seq.empty[String].asJava
    val tooltip = Localization.localizeImmediately("tooltip." + name).
      format(args.map(_.toString): _*)
    val f = font
    if (f == null) return tooltip.linesIterator.toList.asJava // Some mods request tooltips before font renderer is available.
    val isSubTooltip = name.contains(".")
    val shouldShorten = (isSubTooltip || f.width(tooltip) > maxWidth) && !TooltipKeyBindings.showExtendedTooltips
    if (shouldShorten) {
      if (isSubTooltip) Seq.empty[String].asJava
      else Seq(Localization.localizeImmediately("tooltip.TooLong", TooltipKeyBindings.extendedTooltipName)).asJava
    }
    else wrap(f, tooltip)
  }

  def extended(name: String, args: Any*): java.util.List[String] = {
    val f = font
    if (TooltipKeyBindings.showExtendedTooltips && f != null) {
      wrap(f, Localization.localizeImmediately("tooltip." + name).
        format(args.map(_.toString): _*))
    }
    else Seq.empty[String].asJava
  }

  /** 按最大宽度换行，并把每一行转成普通字符串。 */
  private def wrap(f: net.minecraft.client.gui.Font, text: String): java.util.List[String] =
    text.linesIterator.
      flatMap(line => f.split(FormattedText.of(line), maxWidth).asScala.map(plainText)).
      map(_.trim).
      toList.
      asJava

  /**
   * TODO(渲染): `FormattedCharSequence` 没有公开的“取纯文本”方法，
   * 这里用 `FormattedCharSink` 收集码点重建字符串（会丢失样式信息，
   * 旧版 `String` 提示本身也不携带样式，因此行为一致）。
   *
   * 注意用 `java.lang.StringBuilder`：Scala 的 `mutable.StringBuilder`（`new StringBuilder`
   * 解析到它）没有 `appendCodePoint`。这里按 UTF-16 单位追加，与 `StringBuilder` 的
   * `appendCodePoint` 语义等价（`toChars` 之后再 append 即可正确处理增补平面字符）。
   */
  private def plainText(sequence: FormattedCharSequence): String = {
    val builder = new java.lang.StringBuilder
    sequence.accept(new FormattedCharSink {
      override def accept(index: Int, style: net.minecraft.network.chat.Style, codePoint: Int): Boolean = {
        builder.appendCodePoint(codePoint)
        true
      }
    })
    builder.toString
  }
}

/**
 * TODO(客户端): [[li.cil.oc.client.KeyBindings]] 的临时占位。
 *
 * `li.cil.oc.client` 包尚未移植，这里只保留 tooltip 需要的两个语义：
 *  - `showExtendedTooltips`：是否按住扩展提示键，占位为 `false`（即不展开）
 *  - `extendedTooltipName`：扩展提示键的显示名，占位为空串（"tooltip.TooLong" 的
 *    `%s` 参数会是空串，因此只显示“过长”的说明，不显示按键）
 */
private object TooltipKeyBindings {
  def showExtendedTooltips: Boolean = false

  def extendedTooltipName: String = ""
}
