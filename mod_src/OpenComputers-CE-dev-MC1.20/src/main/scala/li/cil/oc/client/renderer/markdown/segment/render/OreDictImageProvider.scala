package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.InteractiveImageRenderer
import li.cil.oc.client.Textures
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.tags._
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.ForgeRegistries

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object OreDictImageProvider extends ImageProvider {
  //
  override def getImage(data: String): ImageRenderer = {
    val desired = ResourceLocation.tryParse(data.toLowerCase)
    val stacks = mutable.ArrayBuffer.empty[ItemStack]
    val itemTagKey = TagKey.create(BuiltInRegistries.ITEM.key(), desired)
    val itemTag = ForgeRegistries.ITEMS.tags().getTag(itemTagKey)
    if (!itemTag.isEmpty) {
      stacks ++= itemTag.asScala.map(new ItemStack(_))
    }
    if (stacks.isEmpty) {
      val blockTagKey = TagKey.create(BuiltInRegistries.BLOCK.key(), desired)
      val blockTag = ForgeRegistries.BLOCKS.tags().getTag(blockTagKey)

      if (!blockTag.isEmpty) {
        stacks ++= blockTag.asScala.map(new ItemStack(_))
      }
    }
    if (stacks.nonEmpty) new ItemStackImageRenderer(stacks.toArray)
    else new TextureImageRenderer(TextureImageProvider.ManualMissingItem) with InteractiveImageRenderer {
      override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.OreDictMissing"

      override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
    }
  }
}
