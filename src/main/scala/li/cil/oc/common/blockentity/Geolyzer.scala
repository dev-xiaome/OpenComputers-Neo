package li.cil.oc.common.blockentity

import li.cil.oc.server.component
import net.minecraft.core.{BlockPos, HolderLookup}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

class Geolyzer(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.GEOLYZER.get(), pos, state) with traits.Environment with IBlockEntityExtension {
  val geolyzer = new component.Geolyzer(this)

  def node = geolyzer.node

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    geolyzer.loadData(nbt, provider)
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    geolyzer.saveData(nbt, provider)
  }
}
