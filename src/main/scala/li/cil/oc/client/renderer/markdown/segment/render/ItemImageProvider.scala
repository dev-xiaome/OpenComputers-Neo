package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.InteractiveImageRenderer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.registries.BuiltInRegistries

object ItemImageProvider extends ImageProvider {
  override def getImage(data: String): ImageRenderer = {
    BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(data.toLowerCase)) match {
      case item: Item => new ItemStackImageRenderer(Array(new ItemStack(item)))
      case _ => new TextureImageRenderer(TextureImageProvider.ManualMissingItem) with InteractiveImageRenderer {
        override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.ItemMissing"

        override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
      }
    }
  }
}
