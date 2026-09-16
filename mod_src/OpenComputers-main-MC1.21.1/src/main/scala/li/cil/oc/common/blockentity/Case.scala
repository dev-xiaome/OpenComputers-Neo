package li.cil.oc.common.blockentity

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.{Driver, Persistable, internal}
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.Connector
import li.cil.oc.common
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.block.property.PropertyRunning
import li.cil.oc.common.datacomponents.OCComponents
import li.cil.oc.common.menu
import li.cil.oc.common.menu.MenuTypes
import li.cil.oc.util.Color
import li.cil.oc.util.ExtendedDataComponentHolder._
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.MenuProvider
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.neoforge.common.MutableDataComponentHolder
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import scala.jdk.CollectionConverters._

class Case(pos: BlockPos, state: BlockState, var tier: Int)
  extends BlockEntity(BlockEntityTypes.CASE.get(), pos, state) with traits.PowerAcceptor with traits.Computer
    with traits.Colored with internal.Case with DeviceInfo with MenuProvider with IBlockEntityExtension {
  def this(pos: BlockPos, state: BlockState) = {
    // BlockEntityType factories only receive position/state. The block itself
    // is the authoritative tier source and must be used before the lazy
    // inventory is ever forced; otherwise every reloaded case starts with the
    // tier-one 7-slot backing array even if components later restore tier five.
    this(pos, state, state.getBlock match {
      case computerCase: common.block.Case => computerCase.tier
      case _ => Tier.One
    })
  }

  // Used on client side to check whether to render disk activity/network indicators.
  var lastFileSystemAccess = 0L
  var lastNetworkActivity = 0L

  setColor(Color.byTier(tier))

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.System,
    DeviceAttribute.Description -> "Computer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Blocker",
    DeviceAttribute.Capacity -> getContainerSize.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override protected def hasConnector(side: Direction) = side != facing

  override protected def connector(side: Direction) = Option(if (side != facing && machine != null) machine.node.asInstanceOf[Connector] else null)

  override def energyThroughput = Settings.get.caseRate(tier)

  def isCreative = tier == Tier.Five

  // ----------------------------------------------------------------------- //

  override def componentSlot(address: String) = componentSlots.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  // ----------------------------------------------------------------------- //

  override def updateEntity(): Unit = {
    if (isServer && isCreative && getLevel.getGameTime % Settings.get.tickFrequency == 0) {
      // Creative case, make it generate power.
      node.asInstanceOf[Connector].changeBuffer(Double.PositiveInfinity)
    }
    super.updateEntity()
  }

  // ----------------------------------------------------------------------- //

  override protected def onRunningChanged(): Unit = {
    super.onRunningChanged()
    getBlockState.getBlock match {
      case block: common.block.Case => {
        val state = getLevel.getBlockState(getBlockPos)
        // race condition that the world no longer has this block at the position (e.g. it was broken)
        if (block == state.getBlock) {
          getLevel.setBlockAndUpdate(getBlockPos, state.setValue(PropertyRunning.Running, Boolean.box(isRunning)))
        }
      }
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //

  private final val TierTag = Settings.namespace + "tier"

  override def loadComponentsForServer(holder: DataComponentHolder): Unit = {
    for(t <- holder.getComponent(OCComponents.TIER)) {
      tier = t
    }
    super.loadComponentsForServer(holder)
    isSizeInventoryReady = true
  }

  override def saveComponentsForServer(holder: MutableDataComponentHolder): Unit = {
    holder.setComponent(OCComponents.TIER, tier.toByte)
    super.saveComponentsForServer(holder)
  }

  // ----------------------------------------------------------------------- //

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    if (isServer) {
      if (InventorySlots.computer(tier)(slot).slot == Slot.Floppy) {
        common.Sound.playDiskInsert(this)
      }
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (isServer) {
      val slotType = InventorySlots.computer(tier)(slot).slot
      if (slotType == Slot.Floppy) {
        common.Sound.playDiskEject(this)
      }
      if (slotType == Slot.CPU) {
        machine.stop()
      }
    }
  }

  override def getContainerSize = if (tier < 0 || tier >= InventorySlots.computer.length) 0 else InventorySlots.computer(tier).length

  override def stillValid(player: Player) =
    super.stillValid(player) && (!isCreative || player.isCreative)

  override def canPlaceItem(slot: Int, stack: ItemStack) =
    Option(Driver.driverFor(stack, getClass)).fold(false)(driver => {
      val provided = InventorySlots.computer(tier)(slot)
      driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
    })

  // ----------------------------------------------------------------------- //

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Case(id, playerInventory, this, tier)
}
