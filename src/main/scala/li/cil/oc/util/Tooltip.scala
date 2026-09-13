package li.cil.oc.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.client.KeyBindings
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
      format(args.map(_.toString).toSeq: _*)
    val f = font
    if (f == null) return tooltip.linesIterator.toList.asJava // Some mods request tooltips before font renderer is available.
    val isSubTooltip = name.contains(".")
    val shouldShorten = (isSubTooltip || f.width(tooltip) > maxWidth) && !KeyBindings.showExtendedTooltips
    if (shouldShorten) {
      if (isSubTooltip) Seq.empty[String].asJava
      else Seq(Localization.localizeImmediately("tooltip.TooLong", KeyBindings.getKeyBindingName(KeyBindings.extendedTooltip))).asJava
    }
    else wrap(f, tooltip)
  }

  def extended(name: String, args: Any*): java.util.List[String] = {
    val f = font
    if (KeyBindings.showExtendedTooltips && f != null) {
      wrap(f, Localization.localizeImmediately("tooltip." + name).
        format(args.map(_.toString).toSeq: _*))
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
   */
  private def plainText(sequence: FormattedCharSequence): String = {
    val builder = new StringBuilder
    sequence.accept(new FormattedCharSink {
      override def accept(index: Int, style: net.minecraft.network.chat.Style, codePoint: Int): Boolean = {
        builder.appendCodePoint(codePoint)
        true
      }
    })
    builder.toString
  }
}
