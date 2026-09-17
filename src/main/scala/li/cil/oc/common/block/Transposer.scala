package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos

class Transposer(props: Properties) extends SimpleBlock(props) {
  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.Transposer(pos, state)
}
