package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.common.init.Items
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.item.Item.Properties
import net.minecraftforge.common.extensions.IForgeItem

class InkCartridge(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  override def hasCraftingRemainingItem(stack: ItemStack): Boolean = true

  override def getCraftingRemainingItem(stack: ItemStack): ItemStack =
    Items.get(Constants.ItemName.InkCartridgeEmpty).createItemStack(1)
}
