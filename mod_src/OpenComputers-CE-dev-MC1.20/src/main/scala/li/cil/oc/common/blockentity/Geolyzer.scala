package li.cil.oc.common.blockentity

import li.cil.oc.server.component
import net.minecraft.core.BlockPos
import net.minecraft.nbt.{CompoundTag => CompoundNBT}
import net.minecraft.world.level.block.entity.{BlockEntity => TileEntity}
import net.minecraft.world.level.block.entity.{BlockEntityType => TileEntityType}
import net.minecraft.world.level.block.state.BlockState

class Geolyzer(pos: BlockPos, state: BlockState) 
  extends TileEntity(BlockEntityTypes.GEOLYZER.get(), pos, state) with traits.Environment {
  val geolyzer = new component.Geolyzer(this)

  def node = geolyzer.node

  override def loadForServer(nbt: CompoundNBT): Unit = {
    super.loadForServer(nbt)
    geolyzer.loadData(nbt)
  }

  override def saveForServer(nbt: CompoundNBT): Unit = {
    super.saveForServer(nbt)
    geolyzer.saveData(nbt)
  }
}
