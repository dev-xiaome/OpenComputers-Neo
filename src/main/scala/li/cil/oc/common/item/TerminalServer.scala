package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IItemExtension


class TerminalServer(props: Properties) extends Item(props) with traits.SimpleItem with IItemExtension {
  override protected def tooltipData = Seq(Settings.get.terminalsPerServer)
}
