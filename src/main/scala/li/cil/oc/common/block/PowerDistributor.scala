package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.{BlockGetter => IBlockReader}
import net.minecraft.world.level.{Level => World}
import net.minecraft.world.level.block.state.BlockState

class PowerDistributor(props: Properties) extends SimpleBlock(props) with traits.Tickable {
  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.PowerDistributor(pos, state)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.POWER_DISTRIBUTOR.get()
}

