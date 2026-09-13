package li.cil.oc.common.item

import net.minecraft.world.item.ItemStack

class TabletCase(val parent: Delegator, val tier: Int) extends traits.Delegate with traits.ItemTier {
  override val unlocalizedName = super.unlocalizedName + tier

  override protected def tierFromDriver(stack: ItemStack) = tier

  override protected def tooltipName = Option(super.unlocalizedName)
}