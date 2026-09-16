package li.cil.oc.common.item

import li.cil.oc.Localization
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedLevel._
import net.minecraft.world.level.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.ItemStack
import net.minecraft.core.Direction
import net.minecraftforge.common.extensions.IForgeItem
import net.minecraft.world.entity.player.Player
import net.minecraft.Util

class TexturePicker(props: Properties) extends Item(props) with IForgeItem with traits.SimpleItem {
  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    player.level.getBlock(position) match {
      case block: Block =>
        if (player.level.isClientSide) {
          val pos = position.toBlockPos
          val model = Minecraft.getInstance.getBlockRenderer.getBlockModel(player.level.getBlockState(pos))
          val be = player.level.getBlockEntity(pos)
          val particle = if (model != null) model.getParticleIcon(be.getModelData) else null
          if (particle != null && particle.contents.name != null) {
            player.sendSystemMessage(Localization.Chat.TextureName(particle.contents.name.toString))
          }
        }
        true
      case _ => super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
    }
  }
}
