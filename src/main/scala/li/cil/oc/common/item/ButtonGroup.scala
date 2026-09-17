package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IForgeItem

class ButtonGroup(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  override protected def tooltipName = None
}
