package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.common.extensions.IItemExtension

class HardDiskDrive(props: Properties, val tier: Int) extends Item(props) with traits.SimpleItem with traits.ItemTier with traits.FileSystemLike with IItemExtension {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  val kiloBytes: Int = Settings.get.hddSizes(tier)
  val platterCount: Int = Settings.get.hddPlatterCounts(tier)

  override def getName(stack: ItemStack): Component = {
    val localizedName = super.getName(stack).copy()
    if (kiloBytes >= 1024) {
      localizedName.append(s" (${kiloBytes / 1024}MB)")
    }
    else {
      localizedName.append(s" (${kiloBytes}KB)")
    }
    localizedName
  }
}