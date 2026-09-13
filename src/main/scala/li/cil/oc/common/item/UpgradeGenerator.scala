package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item

/** 「发电机升级」（原 `li.cil.oc.common.item.UpgradeGenerator`）。 */
class UpgradeGenerator(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {
  override protected def tooltipData: Seq[Any] = Seq((Settings.get.generatorEfficiency * 100).toInt)
}
