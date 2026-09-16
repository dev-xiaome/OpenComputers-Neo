package li.cil.oc.common.item

import li.cil.oc.Settings
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraftforge.common.extensions.IForgeItem

class SolidStateDrive(props: Properties, val tier: Int) extends Item(props) with IForgeItem with traits.SimpleItem with traits.ItemTier with traits.FileSystemLike {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId + tier

  val kiloBytes: Int = Settings.get.ssdSizes(tier)

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