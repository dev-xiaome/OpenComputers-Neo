package li.cil.oc.util

import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation

import scala.collection.convert.ImplicitConversionsToScala._

object Color {
  val rgbValues = Map(
    DyeColor.BLACK -> 0x444444, // 0x1E1B1B
    DyeColor.RED -> 0xB3312C,
    DyeColor.GREEN -> 0x339911, // 0x3B511A
    DyeColor.BROWN -> 0x51301A,
    DyeColor.BLUE -> 0x6666FF, // 0x253192
    DyeColor.PURPLE -> 0x7B2FBE,
    DyeColor.CYAN -> 0x66FFFF, // 0x287697
    DyeColor.LIGHT_GRAY -> 0xABABAB,
    DyeColor.GRAY -> 0x666666, // 0x434343
    DyeColor.PINK -> 0xD88198,
    DyeColor.LIME -> 0x66FF66, // 0x41CD34
    DyeColor.YELLOW -> 0xFFFF66, // 0xDECF2A
    DyeColor.LIGHT_BLUE -> 0xAAAAFF, // 0x6689D3
    DyeColor.MAGENTA -> 0xC354CD,
    DyeColor.ORANGE -> 0xEB8844,
    DyeColor.WHITE -> 0xF0F0F0
  )

  val byName = DyeColor.values().map(col => (col.getName, col)).toMap

  private def getDyeTag(color: DyeColor): TagKey[Item] = {
    TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", s"dyes/${color.getName}"))
  }

  val byTag: Map[TagKey[Item], DyeColor] = DyeColor.values().map(col => (getDyeTag(col), col)).toMap

  private val tierRgbValues = Array(
    rgbValues(DyeColor.LIGHT_GRAY),
    rgbValues(DyeColor.YELLOW),
    rgbValues(DyeColor.CYAN),
    0x9A7D7D,
    rgbValues(DyeColor.MAGENTA)
  )

  def byTier(tier: Int): Int = tierRgbValues(tier max 0 min (tierRgbValues.length - 1))

  def findDye(stack: ItemStack): Option[TagKey[Item]] = {
    if (stack.isEmpty) None
    else byTag.keys.find(stack.is)
  }

  def isDye(stack: ItemStack) = findDye(stack).isDefined

  def dyeColor(stack: ItemStack) = findDye(stack).fold(DyeColor.MAGENTA)(byTag(_))
}
