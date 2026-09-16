package li.cil.oc.common.item

import li.cil.oc.api
import li.cil.oc.common.block.SimpleBlock
import net.minecraft.core.BlockPos
import net.minecraft.world.{InteractionHand, InteractionResult}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.{Item, ItemStack}
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.{Level, LevelReader}
import net.minecraft.world.level.block.{Block, Rotation}
import net.neoforged.neoforge.common.extensions.IItemExtension

class Wrench(props: Properties) extends Item(props) with traits.SimpleItem with api.internal.Wrench with IItemExtension {
  override def doesSneakBypassUse(stack: ItemStack, world: LevelReader, pos: BlockPos, player: Player): Boolean = true

  override def onItemUseFirst(stack: ItemStack, ctx: UseOnContext): InteractionResult = {
    val world = ctx.getLevel
    val pos = ctx.getClickedPos
    val player = ctx.getPlayer
    if (player != null && world.isLoaded(pos) && world.mayInteract(player, pos)) {
      val state = world.getBlockState(pos)
      state.getBlock match {
        case block: SimpleBlock if block.rotateBlock(world, pos, ctx.getClickedFace) =>
          state.onNeighborChange(world, pos, pos)
          InteractionResult.sidedSuccess(world.isClientSide)
        case _ =>
          val updated = state.rotate(world, pos, Rotation.CLOCKWISE_90)
          if (updated != state) {
            world.setBlock(pos, updated, Block.UPDATE_ALL)
            InteractionResult.sidedSuccess(world.isClientSide)
          }
          else super.onItemUseFirst(stack, ctx)
      }
    }
    else super.onItemUseFirst(stack, ctx)
  }

  def useWrenchOnBlock(player: Player, world: Level, pos: BlockPos, simulate: Boolean): Boolean = {
    if (!simulate) player.swing(InteractionHand.MAIN_HAND)
    true
  }
}
