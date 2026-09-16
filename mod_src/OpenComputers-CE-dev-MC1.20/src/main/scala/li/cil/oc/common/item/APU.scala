package li.cil.oc.common.item

import li.cil.oc.common.Tier
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.extensions.IForgeItem

import scala.language.existentials

class APU(props: Properties, val tier: Int) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier with traits.CPULike with traits.GPULike {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  override protected def tierFromDriver(stack: ItemStack) = cpuTier

  override def cpuTier = math.min(Tier.Four, tier + 1)

  override def gpuTier = math.min(Tier.Four, tier)

  override protected def tooltipName = Option(unlocalizedName)

  override protected def tooltipData: Seq[Any] = {
    super[CPULike].tooltipData ++ super[GPULike].tooltipData
  }
}
