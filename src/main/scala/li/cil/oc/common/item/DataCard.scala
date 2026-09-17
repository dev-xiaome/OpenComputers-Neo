package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IForgeItem

class DataCard(props: Properties, val tier: Int) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier
}