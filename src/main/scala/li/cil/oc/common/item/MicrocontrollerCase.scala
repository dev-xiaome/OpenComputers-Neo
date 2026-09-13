package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「微控制器外壳」（原 `li.cil.oc.common.item.MicrocontrollerCase`）。
 *
 * 对应 `Constants.ItemName.MicrocontrollerCaseTier1 / Tier2 / Creative`。
 */
class MicrocontrollerCase(props: Item.Properties, val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tierFromDriver(stack: ItemStack): Int = tier

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)
}
