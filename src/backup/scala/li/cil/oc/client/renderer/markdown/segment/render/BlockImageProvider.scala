package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.{ImageProvider, ImageRenderer, InteractiveImageRenderer}
import li.cil.oc.client.Textures
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.{ItemStack, Items}

/**
 * 手册图片前缀 `block`：按方块注册名取图标。
 *
 * 1.7.10 用 `Block.blockRegistry.getObject(name)`，再用 `Item.getItemFromBlock(block)`
 * 拿到对应的物品堆叠；1.21.1 的方块物品统一登记在 `BuiltInRegistries.ITEM` 里，
 * 因此直接查方块对应的 `BlockItem` 即可。
 */
object BlockImageProvider extends ImageProvider {
  override def getImage(data: String): ImageRenderer = {
    val stack = forBlock(data)
    if (!stack.isEmpty) new ItemStackImageRenderer(Array(stack))
    else new TextureImageRenderer(Textures.guiManualMissingItem) with InteractiveImageRenderer {
      override def getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.BlockMissing"

      override def onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
    }
  }

  private def forBlock(data: String): ItemStack = {
    val rl = ManualImages.location(data)
    if (rl == null) return ItemStack.EMPTY
    val block = BuiltInRegistries.BLOCK.get(rl)
    if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return ItemStack.EMPTY
    val item = block.asItem
    if (item == null || item == Items.AIR) ItemStack.EMPTY
    else new ItemStack(item)
  }
}
