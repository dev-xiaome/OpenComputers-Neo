package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import li.cil.oc.integration.util.Wrench
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties => Properties}
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.{InteractionResult => ActionResultType}
import net.minecraft.core.Direction
import net.minecraft.world.{InteractionHand => Hand}
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.{BlockHitResult => BlockRayTraceResult}
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}

class NetSplitter(props: Properties) extends RedstoneAware(props) {
  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.NetSplitter(pos, state)

  // ----------------------------------------------------------------------- //

  // NOTE: must not be final for immibis microblocks to work.
  override def useWithoutItem(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hitResult: BlockRayTraceResult): ActionResultType = {
    if (Wrench.holdsApplicableWrench(player, pos)) {
      val side = hitResult.getDirection
      val sideToToggle = if (player.isCrouching) side.getOpposite else side
      world.getBlockEntity(pos) match {
        case splitter: blockentity.NetSplitter =>
          if (!world.isClientSide) {
            val oldValue = splitter.openSides(sideToToggle.ordinal())
            splitter.setSideOpen(sideToToggle, !oldValue)
          }
          ActionResultType.sidedSuccess(world.isClientSide)
        case _ => ActionResultType.PASS
      }
    }
    else super.useWithoutItem(state, world, pos, player, hitResult)
  }
}
