package li.cil.oc.common.item

import li.cil.oc.util.BlockPosition
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「贴图拾取器」（原 `li.cil.oc.common.item.TexturePicker`）。
 *
 * 1.21.1 迁移要点：
 *  - `Block#getIcon(world, x, y, z, side)` 已删除：1.21.1 的方块贴图走烘焙模型，
 *    客户端通过 `BlockModelShaper.stateToModelLocation(state)`（或 `BakedModel` 的
 *    `getParticleIcon`）取贴图名。为了不在通用侧引入 `client` 包的 import，
 *    这里退化为输出「方块注册名 + BlockState」，等客户端阶段再接真实贴图名。
 */
class TexturePicker(props: Item.Properties) extends Item(props) with traits.Delegate {

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                         side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    position.world match {
      case Some(world) =>
        val pos = position.toChunkCoordinates
        val state = world.getBlockState(pos)
        // TODO(客户端): 改用 `Minecraft.getInstance.getModelManager.getBlockModelShaper
        //   .stateToModelLocation(state)` 输出真实贴图名（与原 icon.getIconName 语义一致）。
        if (world.isClientSide) {
          val name = BuiltInRegistries.BLOCK.getKey(state.getBlock).toString
          player.displayClientMessage(li.cil.oc.Localization.Chat.TextureName(name), false)
        }
        true
      case _ => super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
    }
  }
}
