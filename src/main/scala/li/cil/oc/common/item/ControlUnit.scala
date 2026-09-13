package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「控制单元 CU」（原 `li.cil.oc.common.item.ControlUnit`）。 */
class ControlUnit(props: Item.Properties) extends Item(props) with traits.Delegate
