package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item

/** 「太阳能发电机升级」（原 `li.cil.oc.common.item.UpgradeSolarGenerator`）。 */
class UpgradeSolarGenerator(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {
  override protected def tooltipData: Seq[Any] = Seq((Settings.get.solarGeneratorEfficiency * 100).toInt)
}
