package li.cil.oc.common.item

import net.minecraft.world.item.Item

/** 「牵引光束升级」（原 `li.cil.oc.common.item.UpgradeTractorBeam`）。 */
class UpgradeTractorBeam(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier
