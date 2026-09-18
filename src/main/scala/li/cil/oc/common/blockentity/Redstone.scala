package li.cil.oc.common.blockentity

import li.cil.oc.{Settings, api}
import li.cil.oc.api.network.{Component, Node, Visibility}
import li.cil.oc.common.blockentity.traits.RedstoneChangedEventArgs
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.component
import li.cil.oc.server.component.RedstoneVanilla
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, HolderLookup}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

class Redstone(pos: BlockPos, state: BlockState) extends BlockEntity(BlockEntityTypes.REDSTONE_IO.get(), pos, state)
  with traits.Environment with traits.BundledRedstoneAware with traits.Tickable with IBlockEntityExtension {
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

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    instance.loadData(nbt.getCompound(RedstoneTag), provider)
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    nbt.setNewCompoundTag(RedstoneTag, (nbt: CompoundTag) => instance.saveData(nbt, provider))
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
