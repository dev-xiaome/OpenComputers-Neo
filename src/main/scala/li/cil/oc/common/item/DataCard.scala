package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IItemExtension


class DataCard(props: Properties, val tier: Int) extends Item(props) with traits.ComponentItem with traits.ItemTier with IItemExtension {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

}
