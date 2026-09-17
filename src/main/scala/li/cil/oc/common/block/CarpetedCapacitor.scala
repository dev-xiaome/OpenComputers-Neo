package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.BlockBehaviour.Properties

class CarpetedCapacitor(props: Properties) extends Capacitor(props) with traits.Tickable {
  override def newBlockEntity(pos: BlockPos, state: BlockState) = new blockentity.CarpetedCapacitor(pos, state)

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.CARPETED_CAPACITOR.get()
}
