package li.cil.oc.util

import net.minecraft.ChatFormatting
import net.minecraft.world.item.{Rarity => MCRarity}

object Rarity {
  import MCRarity._
  private val lookup = Array(MCRarity.COMMON, MCRarity.UNCOMMON, MCRarity.RARE, MCRarity.EPIC)
  
  val LEGENDARY: MCRarity = MCRarity.create("legendary", ChatFormatting.GOLD)

  def byTier(tier: Int) = lookup(tier max 0 min (lookup.length - 1))
}
