package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IItemExtension


class UpgradeSolarGenerator(props: Properties) extends Item(props) with traits.SimpleItem with traits.ItemTier with IItemExtension {
  override protected def tooltipData = Seq((Settings.get.solarGeneratorEfficiency * 100).toInt)
}
