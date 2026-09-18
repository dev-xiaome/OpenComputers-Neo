package li.cil.oc.common.blockentity

import li.cil.oc.api.Driver
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.{Analyzable, Visibility}
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.common.{Slot, menu}
import li.cil.oc.server.component.FileSystem
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedDataComponentHolder._
import li.cil.oc.{Settings, api}
import net.minecraft.core.component.{DataComponentHolder, DataComponentMap}
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import java.nio.ByteBuffer
import java.util.UUID
import java.util.function.Consumer

class Raid(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.RAID.get(), pos, state) with traits.Environment with traits.Inventory with traits.Rotatable with Analyzable with MenuProvider
    with IBlockEntityExtension {
  val node = api.Network.newNode(this, Visibility.None).create()

  var filesystem: Option[FileSystem] = None

  val label = new RaidLabel()

  // Used on client side to check whether to render disk activity indicators.
  var lastAccess = 0L

  // For client side rendering.
  val presence = Array.fill(getContainerSize)(false)

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = Array(filesystem.map(_.node).orNull)

  // ----------------------------------------------------------------------- //

  override def getContainerSize = 3

  override def getMaxStackSize = 1

  override def canPlaceItem(slot: Int, stack: ItemStack) = Option(Driver.driverFor(stack, getClass)) match {
    case Some(driver) => driver.slot(stack) == Slot.HDD
    case _ => false
  }

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    if (isServer) this.synchronized {
      ServerPacketSender.sendRaidChange(this)
      tryCreateRaid(UUID.randomUUID().toString)
    }
  }

  override def setChanged(): Unit = {
    super.setChanged()
    // Makes the implementation of the comparator output easier.
    items.map(!_.isEmpty).copyToArray(presence)
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (isServer) this.synchronized {
      ServerPacketSender.sendRaidChange(this)
      filesystem.foreach(fs => {
        fs.fileSystem.close()
        fs.fileSystem.list("/").foreach(fs.fileSystem.delete)
        fs.saveData(new CompoundTag(), this.getLevel.registryAccess()) // Flush buffered fs.
        fs.node.remove()
        filesystem = None
      })
    }
  }

  // Uses the loot system, so nope.
  override def forAllLoot(dst: Consumer[ItemStack]) = ()

  override def dropSlot(slot: Int, count: Int = getMaxStackSize, direction: Option[Direction]) = false

  override def dropAllSlots() = ()

  def tryCreateRaid(id: String): Unit = {
    if (items.count(!_.isEmpty) == items.length && filesystem.fold(true)(fs => fs.node == null || fs.node.address != id)) {
      filesystem.foreach(fs => if (fs.node != null) fs.node.remove())
      items.foreach(fsStack => {
        val drive = new DriveData(fsStack)
        drive.lockInfo = ""
        drive.isUnmanaged = false
        drive.saveData(fsStack)
      })
      val fs = api.FileSystem.asManagedEnvironment(
        api.FileSystem.fromSaveDirectory(id, wipeDisksAndComputeSpace, Settings.get.bufferChanges),
        label, this, Settings.resourceDomain + ":hdd_access", 6).
        asInstanceOf[FileSystem]
      fs.node.loadData(DataComponentMap.builder()
        .set(OCComponents.ADDRESS, id)
        .set(OCComponents.VISIBILITY, Visibility.Network)
        .build())
      // Ensure we're in a network before connecting the raid fs.
      api.Network.joinNewNetwork(node)
      node.connect(fs.node)
      filesystem = Option(fs)
    }
  }

  private def wipeDisksAndComputeSpace = items.foldLeft(0L) {
    case (acc, hdd) if !hdd.isEmpty => acc + (Option(api.Driver.driverFor(hdd)) match {
      case Some(driver) => driver.createEnvironment(hdd, this) match {
        case fs: FileSystem =>
          fs.loadData(hdd)
          fs.fileSystem.close()
          fs.fileSystem.list("/").foreach(fs.fileSystem.delete)
          fs.saveData(hdd)
          fs.fileSystem.spaceTotal
        case _ => 0L // Ignore.
      }
      case _ => 0L
    })
    case (acc, ItemStack.EMPTY) => acc
  }

  // ----------------------------------------------------------------------- //

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Raid(id, playerInventory, this)

  // ----------------------------------------------------------------------- //

  private final val FileSystemTag = Settings.namespace + "fs"
  private final val PresenceTag = Settings.namespace + "presence"
  private final val LabelTag = Settings.namespace + "label"

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    super.loadComponentsForServer(holder)

    for(address <- holder.getComponent(OCComponents.ADDRESS)) {
      tryCreateRaid(address)

      for(fs <- filesystem) {
        fs.loadData(holder)
      }
    }

    label.loadData(holder)
  }

  override def saveComponentsForServer(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForServer(holder)

    for(fs <- filesystem) fs.saveData(holder)
    label.saveData(holder)
  }

  override def loadComponentsForClient(holder: DataComponentHolder): Unit = {
    super.loadComponentsForClient(holder)
    for(p <- holder.getComponent(OCComponents.PRESENCE)) {
      val bytes: Array[Byte] = Array.fill(presence.length)(0)
      p.get(bytes)
      bytes.map(_ != 0).copyToArray(presence)
    }
    this.label.loadData(holder)
  }

  override def saveComponentsForClient(holder: MutableDataComponentHolder): Unit = {
    super.saveComponentsForClient(holder)
    holder.setComponent(OCComponents.PRESENCE, ByteBuffer.wrap(items.map(s => if(s.isEmpty) 0.toByte else 1.toByte)))
    label.saveData(holder)
  }

  // ----------------------------------------------------------------------- //

  class RaidLabel extends Label {
    var label = "raid"

    override def getLabel(provider: HolderLookup.Provider): String = label

    override def setLabel(value: String) = label = Option(value).map(_.take(16)).orNull

    override def loadData(holder: DataComponentHolder): Unit = {
      for(label <- holder.getComponent(OCComponents.LABEL)) {
        this.label = label
      }
    }

    override def saveData(holder: MutableDataComponentHolder): Unit = {
      holder.setComponent(OCComponents.LABEL, label)
    }
  }

}
