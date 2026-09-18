package li.cil.oc.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style
import net.minecraft.world.item.{Rarity => MCRarity}
import net.neoforged.fml.common.asm.enumextension.EnumProxy

import java.util.function.UnaryOperator
import scala.annotation.meta.field

object Rarity {
  import MCRarity._
  private val lookup = Array(MCRarity.COMMON, MCRarity.UNCOMMON, MCRarity.RARE, MCRarity.EPIC)

  @field
  final val LEGENDARY: MCRarity = RarityExt.LEGENDARY.getValue;

  def byTier(tier: Int) = lookup(tier max 0 min (lookup.length - 1))
}
