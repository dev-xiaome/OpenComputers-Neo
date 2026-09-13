package li.cil.oc.util

import net.minecraft.world.item.Rarity

object Rarity {
  private val lookup = Array(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC)

  def byTier(tier: Int): Rarity = lookup(tier max 0 min (lookup.length - 1))
}
