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
import net.minecraft.core.registries.BuiltInRegistries

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object OreDictImageProvider extends ImageProvider {
  //
  override def getImage(data: String): ImageRenderer = {
    val desired = ResourceLocation.tryParse(data.toLowerCase)
    val stacks = mutable.ArrayBuffer.empty[ItemStack]
    // 1.21.1: Registry.getTags 现在返回 Stream<Pair<TagKey, HolderSet.Named>>，
    // 取单个标签改用 Registry.getTag(TagKey)，返回 Optional<HolderSet.Named>。
    val itemTagKey = TagKey.create(BuiltInRegistries.ITEM.key(), desired)
    val itemTag = BuiltInRegistries.ITEM.getTag(itemTagKey)
    if (itemTag.isPresent) {
      stacks ++= itemTag.get.asScala.map(holder => new ItemStack(holder.value()))
    }
    if (stacks.isEmpty) {
      val blockTagKey = TagKey.create(BuiltInRegistries.BLOCK.key(), desired)
      val blockTag = BuiltInRegistries.BLOCK.getTag(blockTagKey)

      if (blockTag.isPresent) {
        stacks ++= blockTag.get.asScala.map(holder => new ItemStack(holder.value()))
      }
    }
    if (stacks.nonEmpty) new ItemStackImageRenderer(stacks.toArray)
    else new TextureImageRenderer(TextureImageProvider.ManualMissingItem) with InteractiveImageRenderer {
      override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.OreDictMissing"

      override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
    }
  }
}
