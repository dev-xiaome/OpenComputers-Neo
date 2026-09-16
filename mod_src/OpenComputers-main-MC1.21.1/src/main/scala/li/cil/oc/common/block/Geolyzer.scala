package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import net.minecraft.world.level.block.state.BlockBehaviour.{Properties => Properties}
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class Geolyzer(props: Properties) extends SimpleBlock(props) {
  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Geolyzer(pos, state)
}
