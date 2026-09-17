package li.cil.oc.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.neoforged.neoforge.common.extensions.IForgeItem

class CreativeMemory(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  @Deprecated
  override def getDescriptionId = super.getDescriptionId

  override protected def tooltipName = Option(unlocalizedName)
}
