package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties


class ButtonGroup(props: Properties) extends Item(props) with traits.SimpleItem {
  override protected def tooltipName = None
}
