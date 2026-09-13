package li.cil.oc.common.tileentity

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.Constants.NBT
import net.minecraft.core.Direction

class PowerDistributor extends traits.Environment with traits.PowerBalancer with traits.NotAnalyzable {
  val node = null

  private val nodes = Array.fill(6)(api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferDistributor).
    create())

  override protected def isConnected = nodes.exists(node => node.address != null && node.network != null)

  override def canUpdate = isServer

  // ----------------------------------------------------------------------- //

  @SideOnly(Dist.CLIENT)
  override def canConnect(side: Direction) = true

  override def sidedNode(side: Direction) = nodes(side.ordinal)

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    nbt.getList(Settings.namespace + "connector", NBT.TAG_COMPOUND).toArray[CompoundTag].
      zipWithIndex.foreach {
      case (tag, index) => nodes(index).load(tag)
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    // Side check for Waila (and other mods that may call this client side).
    if (isServer) {
      nbt.setNewTagList(Settings.namespace + "connector", nodes.map(connector => {
        val connectorNbt = new CompoundTag()
        connector.save(connectorNbt)
        connectorNbt
      }))
    }
  }
}
