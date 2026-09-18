package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.common.init.OCItems
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IItemExtension


class InkCartridge(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override def hasCraftingRemainingItem(stack: ItemStack): Boolean = true

  override def getCraftingRemainingItem(stack: ItemStack): ItemStack =
    OCItems.get(Constants.ItemName.InkCartridgeEmpty).createItemStack(1)
}
