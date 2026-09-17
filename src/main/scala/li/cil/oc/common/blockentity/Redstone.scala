package li.cil.oc.common.blockentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.blockentity.traits.RedstoneChangedEventArgs
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.component
import li.cil.oc.server.component.RedstoneVanilla
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.nbt.CompoundTag

class Redstone(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.REDSTONE_IO.get(), pos, state) with traits.Environment with traits.BundledRedstoneAware with traits.Tickable {
  val instance: RedstoneVanilla =
    if (BundledRedstone.isAvailable)
      new component.Redstone.Bundled(this)
    else
      new component.Redstone.Vanilla(this)
  instance.wakeNeighborsOnly = false
  val node: Component = instance.node
  val dummyNode: Node = if (node != null) {
    node.setVisibility(Visibility.Network)
    _isOutputEnabled = true
    api.Network.newNode(this, Visibility.None).create()
  }
  else null

  // ----------------------------------------------------------------------- //

  private final val RedstoneTag = Settings.namespace + "redstone"

  override def loadForServer(nbt: CompoundTag): Unit = {
    super.loadForServer(nbt)
    instance.loadData(nbt.getCompound(RedstoneTag))
  }

  override def saveForServer(nbt: CompoundTag): Unit = {
    super.saveForServer(nbt)
    nbt.setNewCompoundTag(RedstoneTag, instance.saveData)
  }

  // ----------------------------------------------------------------------- //

  override protected def onRedstoneInputChanged(args: RedstoneChangedEventArgs): Unit = {
    super.onRedstoneInputChanged(args)
    if (node != null && node.network != null) {
      node.connect(dummyNode)
      dummyNode.sendToNeighbors("redstone.changed", args)
    }
  }
}
