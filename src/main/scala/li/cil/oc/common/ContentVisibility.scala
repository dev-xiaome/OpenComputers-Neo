package li.cil.oc.common

import li.cil.oc.Settings
import net.minecraft.world.item.{Item, ItemStack}

import java.util

/**
 * Optional content that stays registered for world compatibility, but can be
 * hidden from catalogs and have its recipes gated.
 */
object ContentVisibility {
  def isTier4(item: Item): Boolean = tier4Items.contains(item)

  def isOpenScreens(item: Item): Boolean = openScreensItems.contains(item)

  def isHidden(stack: ItemStack): Boolean = !stack.isEmpty && isHidden(stack.getItem)

  def isHidden(item: Item): Boolean =
    (Settings.get.hideTier4 && isTier4(item)) ||
      (Settings.get.hideOpenScreens && isOpenScreens(item))

  def hiddenItems: util.Set[Item] = {
    val hidden = new util.HashSet[Item]
    if (Settings.get.hideTier4) hidden.addAll(tier4Items)
    if (Settings.get.hideOpenScreens) hidden.addAll(openScreensItems)
    util.Set.copyOf(hidden)
  }

  // Tier 4 items (the OCCE tier four variants) were pruned together with the
  // rest of the non-vanilla content, so this set is intentionally empty.
  lazy val tier4Items: util.Set[Item] = util.Set.of()

  // Open screens (the OCCE flat screens) were pruned with the rest of the
  // non-vanilla blocks, so this set is intentionally empty.
  lazy val openScreensItems: util.Set[Item] = util.Set.of()
}
