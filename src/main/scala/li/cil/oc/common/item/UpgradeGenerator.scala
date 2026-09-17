package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties


class UpgradeGenerator(props: Properties) extends Item(props) with traits.SimpleItem with traits.ItemTier {
  override protected def tooltipData = Seq((Settings.get.generatorEfficiency * 100).toInt)
}
