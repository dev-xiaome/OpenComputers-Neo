package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item

/** 「终端服务器」（原 `li.cil.oc.common.item.TerminalServer`）。 */
class TerminalServer(props: Item.Properties) extends Item(props) with traits.Delegate {
  override protected def tooltipData: Seq[Any] = Seq(Settings.get.terminalsPerServer)
}
