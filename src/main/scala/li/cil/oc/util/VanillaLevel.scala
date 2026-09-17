package li.cil.oc.util

import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/** Vanilla fire extinguishing without Level.extinguishFire (removed in 1.20). */
object VanillaLevel {
  def extinguishFire(level: Level, player: Player, pos: BlockPos, side: Direction): Boolean = {
    val firePos = new BlockPos(pos.getX + side.getStepX, pos.getY + side.getStepY, pos.getZ + side.getStepZ)
    val state = level.getBlockState(firePos)
    if (state.is(BlockTags.FIRE)) {
      level.removeBlock(firePos, false)
      true
    } else {
      false
    }
  }
}
