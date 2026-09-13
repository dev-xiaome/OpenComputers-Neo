package li.cil.oc.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.{Item, ItemStack, Items}

/**
 * OC 的 16 色板工具。
 *
 * 1.21.1 迁移要点：
 *  - 原 1.7.10 通过 `OreDictionary` 的 `dyeXxx` 矿辞名识别染料，1.21.1 已无
 *    OreDictionary（见 `docs/PORTING.md`），改为按物品标签识别：
 *    优先匹配物品自带的 `dyes/<name>` 标签，其次匹配 `c:dyes/<name>`（NeoForge
 *    通用标签），最后回退到原版 `DyeColor` 对应的 16 个染料物品。
 *  - `dyes` / `byOreName` 等常量保留原样，供 Lua 侧 `colors` API 使用。
 */
object Color {
  val Black = 0x444444
  // 0x1E1B1B
  val Red = 0xB3312C
  val Green = 0x339911
  // 0x3B511A
  val Brown = 0x51301A
  val Blue = 0x6666FF
  // 0x253192
  val Purple = 0x7B2FBE
  val Cyan = 0x66FFFF
  // 0x287697
  val LightGray = 0xABABAB
  val Gray = 0x666666
  // 0x434343
  val Pink = 0xD88198
  val Lime = 0x66FF66
  // 0x41CD34
  val Yellow = 0xFFFF66
  // 0xDECF2A
  val LightBlue = 0xAAAAFF
  // 0x6689D3
  val Magenta = 0xC354CD
  val Orange = 0xEB8844
  val White = 0xF0F0F0

  val dyes = Array(
    "dyeBlack",
    "dyeRed",
    "dyeGreen",
    "dyeBrown",
    "dyeBlue",
    "dyePurple",
    "dyeCyan",
    "dyeLightGray",
    "dyeGray",
    "dyePink",
    "dyeLime",
    "dyeYellow",
    "dyeLightBlue",
    "dyeMagenta",
    "dyeOrange",
    "dyeWhite")

  val byOreName = Map(
    "dyeBlack" -> Black,
    "dyeRed" -> Red,
    "dyeGreen" -> Green,
    "dyeBrown" -> Brown,
    "dyeBlue" -> Blue,
    "dyePurple" -> Purple,
    "dyeCyan" -> Cyan,
    "dyeLightGray" -> LightGray,
    "dyeGray" -> Gray,
    "dyePink" -> Pink,
    "dyeLime" -> Lime,
    "dyeYellow" -> Yellow,
    "dyeLightBlue" -> LightBlue,
    "dyeMagenta" -> Magenta,
    "dyeOrange" -> Orange,
    "dyeWhite" -> White)

  val byTier = Array(LightGray, Yellow, Cyan, Magenta)

  /** 旧矿辞名 → 原版染料颜色名（用于标签 / 物品回退查找）。 */
  private val dyeColorNames: Map[String, String] = Map(
    "dyeBlack" -> "black",
    "dyeRed" -> "red",
    "dyeGreen" -> "green",
    "dyeBrown" -> "brown",
    "dyeBlue" -> "blue",
    "dyePurple" -> "purple",
    "dyeCyan" -> "cyan",
    "dyeLightGray" -> "light_gray",
    "dyeGray" -> "gray",
    "dyePink" -> "pink",
    "dyeLime" -> "lime",
    "dyeYellow" -> "yellow",
    "dyeLightBlue" -> "light_blue",
    "dyeMagenta" -> "magenta",
    "dyeOrange" -> "orange",
    "dyeWhite" -> "white")

  /** 原版染料物品回退表（标签数据缺失时仍可识别原版染料）。 */
  private lazy val vanillaDyes: Map[String, Item] = Map(
    "black" -> Items.BLACK_DYE,
    "red" -> Items.RED_DYE,
    "green" -> Items.GREEN_DYE,
    "brown" -> Items.BROWN_DYE,
    "blue" -> Items.BLUE_DYE,
    "purple" -> Items.PURPLE_DYE,
    "cyan" -> Items.CYAN_DYE,
    "light_gray" -> Items.LIGHT_GRAY_DYE,
    "gray" -> Items.GRAY_DYE,
    "pink" -> Items.PINK_DYE,
    "lime" -> Items.LIME_DYE,
    "yellow" -> Items.YELLOW_DYE,
    "light_blue" -> Items.LIGHT_BLUE_DYE,
    "magenta" -> Items.MAGENTA_DYE,
    "orange" -> Items.ORANGE_DYE,
    "white" -> Items.WHITE_DYE)

  /** 读取物品上 `dyes/` 前缀的通用标签，取得其颜色名。 */
  private def dyeTagPaths(stack: ItemStack): Set[String] = {
    BuiltInRegistries.ITEM.getTagNames
      .iterator()
      .asScala
      .map(_.location().getPath)
      .filter(_.startsWith("dyes/"))
      .map(_.stripPrefix("dyes/"))
      .toSet
  }

  def byMeta(meta: Int) = byOreName(dyes(15 - meta))

  /** 等价于 1.7.10 的矿辞染料查找，返回 `dyeXxx` 形式的标识名。 */
  def findDye(stack: ItemStack): Option[String] = {
    if (stack == null || stack.isEmpty) None
    else {
      val tagged = dyeTagPaths(stack)
      byOreName.keys.find(name => dyeColorNames.get(name).exists(tagged.contains)) match {
        case some@Some(_) => some
        case None =>
          byOreName.keys.find(name => dyeColorNames.get(name).flatMap(vanillaDyes.get).exists(stack.is))
      }
    }
  }

  def isDye(stack: ItemStack): Boolean = findDye(stack).isDefined

  def dyeColor(stack: ItemStack): Int = findDye(stack).fold(0xFF00FF)(byOreName(_))
}
