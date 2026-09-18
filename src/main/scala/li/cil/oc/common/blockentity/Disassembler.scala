package li.cil.oc.common.blockentity

import li.cil.oc.{Constants, Settings, api}
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.{DeviceAttribute, DeviceClass}
import li.cil.oc.api.network.{Connector, Visibility}
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.menu
import li.cil.oc.common.template.DisassemblerTemplates
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.{BlockPosition, InventoryUtils, ItemUtils}
import net.minecraft.core.component.DataComponents
import net.minecraft.core.{BlockPos, Direction, HolderLookup}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.{Inventory, Player}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.api.distmarker.{Dist, OnlyIn}
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import java.util
import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class Disassembler(pos: BlockPos, state: BlockState) 
  extends BlockEntity(BlockEntityTypes.DISASSEMBLER.get(), pos, state) with traits.Environment with traits.PowerAcceptor
  with traits.Inventory with traits.StateAware with traits.PlayerInputAware with traits.Tickable with DeviceInfo with MenuProvider
    with IBlockEntityExtension {

  val node: Connector = api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferConverter).
    create()

  var isActive = false

  val queue: ArrayBuffer[ItemStack] = mutable.ArrayBuffer.empty[ItemStack]

  var totalRequiredEnergy = 0.0

  override def getMaxStackSize: Int = 1

  var buffer = 0.0

  var disassembleNextInstantly = false

  def progress: Double = if (queue.isEmpty) 0.0 else (1 - (queue.size * Settings.get.disassemblerItemCost - buffer) / totalRequiredEnergy) * 100

  private def setActive(value: Boolean) = if (value != isActive) {
    isActive = value
    ServerPacketSender.sendDisassemblerActive(this, isActive)
    getLevel.updateNeighborsAt(getBlockPos, getBlockState.getBlock)
  }

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Disassembler",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Break.3R-100"
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  @OnlyIn(Dist.CLIENT)
  override protected def hasConnector(side: Direction): Boolean = side != Direction.UP

  override protected def connector(side: Direction) = Option(if (side != Direction.UP) node else null)

  override def energyThroughput: Double = Settings.get.disassemblerRate

  override def getCurrentState: util.EnumSet[StateAware.State] = {
    if (isActive) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else if (queue.nonEmpty) util.EnumSet.of(api.util.StateAware.State.CanWork)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //

  override def updateEntity(): Unit = {
    super.updateEntity()
    if (isServer && getLevel.getGameTime % Settings.get.tickFrequency == 0) {
      if (queue.isEmpty) {
        val instant = disassembleNextInstantly // Is reset via removeItem
        disassemble(removeItem(0, 1), instant)
        disassembleNextInstantly = instant && queue.nonEmpty
        setActive(queue.nonEmpty)
      }
      else {
        if (buffer < Settings.get.disassemblerItemCost) {
          val want = Settings.get.disassemblerTickAmount
          val success = node.tryChangeBuffer(-want)
          setActive(success) // If energy is insufficient indicate it visually.
          if (success) {
            buffer += want
          }
        }
        while (buffer >= Settings.get.disassemblerItemCost && queue.nonEmpty) {
          buffer -= Settings.get.disassemblerItemCost
          val stack = queue.remove(0)
          if (disassembleNextInstantly || getLevel.random.nextDouble >= Settings.get.disassemblerBreakChance) {
            drop(stack)
          }
        }
        if (queue.isEmpty) disassembleNextInstantly = false
      }
    }
  }

  def disassemble(stack: ItemStack, instant: Boolean = false): Unit = {
    // Validate the item, never trust Minecraft / other Mods on anything!
    if (canPlaceItem(0, stack)) {
      val ingredients = ItemUtils.getIngredients(getLevel.getRecipeManager, stack)
      DisassemblerTemplates.select(stack) match {
        case Some(template) =>
          val (stacks, drops) = template.disassemble(stack, ingredients)
          stacks match {
            case Some(output) =>
              queue ++= output
              drops.foreach(_.foreach(drop))
            case None =>
              // The input was already removed from the inventory. Preserve
              // it if a callback failed or returned an unsupported result.
              drop(stack)
          }
        case _ => queue ++= ingredients
      }
      totalRequiredEnergy = queue.size * Settings.get.disassemblerItemCost
      if (instant) {
        buffer = totalRequiredEnergy
      }
    }
    else {
      drop(stack)
    }
  }

  private def drop(stack: ItemStack): Unit = {
    if (!stack.isEmpty) {
      for (side <- Direction.values if stack.getCount > 0) {
        InventoryUtils.insertIntoInventoryAt(stack, BlockPosition(this).offset(side), Some(side.getOpposite))
      }
      if (stack.getCount > 0) {
        spawnStackInWorld(stack, Option(Direction.UP))
      }
    }
  }

  // ----------------------------------------------------------------------- //

  private final val QueueTag = Settings.namespace + "queue"
  private final val BufferTag = Settings.namespace + "buffer"
  private final val TotalTag = Settings.namespace + "total"
  private final val IsActiveTag = Settings.namespace + "isActive"

  override def loadForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForServer(nbt, provider)
    queue.clear()
    queue ++= nbt.getList(QueueTag, Tag.TAG_COMPOUND).
      map((tag: CompoundTag) => ItemStack.parseOptional(provider, tag))
    buffer = nbt.getDouble(BufferTag)
    totalRequiredEnergy = nbt.getDouble(TotalTag)
    isActive = queue.nonEmpty
  }

  override def saveForServer(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForServer(nbt, provider)
    nbt.setNewTagList(QueueTag, queue)
    nbt.putDouble(BufferTag, buffer)
    nbt.putDouble(TotalTag, totalRequiredEnergy)
  }

  override def loadForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.loadForClient(nbt, provider)
    isActive = nbt.getBoolean(IsActiveTag)
  }

  override def saveForClient(nbt: CompoundTag, provider: HolderLookup.Provider): Unit = {
    super.saveForClient(nbt, provider)
    nbt.putBoolean(IsActiveTag, isActive)
  }

  // ----------------------------------------------------------------------- //

  override def getContainerSize = 1

  override def canPlaceItem(i: Int, stack: ItemStack): Boolean =
    allowDisassembling(stack) &&
      (((Settings.get.disassembleAllTheThings || api.Items.get(stack) != null) && ItemUtils.getIngredients(getLevel.getRecipeManager, stack).nonEmpty) ||
        DisassemblerTemplates.select(stack).isDefined)

  private def allowDisassembling(stack: ItemStack) = !stack.isEmpty && (!stack.has(DataComponents.CUSTOM_DATA) || !stack.get(DataComponents.CUSTOM_DATA).getUnsafe.getBoolean(Settings.namespace + "undisassemblable"))

  override def setItem(slot: Int, stack: ItemStack): Unit = {
    super.setItem(slot, stack)
    if (!getLevel.isClientSide) {
      disassembleNextInstantly = false
    }
  }

  override def onSetInventorySlotContents(player: Player, slot: Int, stack: ItemStack): Unit = {
    if (!getLevel.isClientSide) {
      disassembleNextInstantly = !stack.isEmpty && slot == 0 && player.isCreative
    }
  }

  // ----------------------------------------------------------------------- //

  override def createMenu(id: Int, playerInventory: Inventory, player: Player) =
    new menu.Disassembler(id, playerInventory, this)
}
