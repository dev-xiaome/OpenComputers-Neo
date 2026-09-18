package li.cil.oc.common.blockentity

import li.cil.oc.server.component
import net.minecraft.core.{BlockPos, HolderLookup}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

class Transposer(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.TRANSPOSER.get(), pos, state) with traits.Environment with IBlockEntityExtension {
  val transposer = new component.Transposer.Block(this)

  def node = transposer.node

  // Used on client side to check whether to render activity indicators.
  var lastOperation = 0L

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    transposer.loadData(nbt, provider)
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    transposer.saveData(nbt, provider)
  }
}
