package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「按键组」（原 `li.cil.oc.common.item.ButtonGroup`）。 */
class ButtonGroup(props: Item.Properties) extends Item(props) with traits.Delegate {
  override protected def tooltipName: Option[String] = None
}
