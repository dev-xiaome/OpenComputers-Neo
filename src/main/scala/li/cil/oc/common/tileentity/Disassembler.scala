package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.{BlockPosition, ExtendedNBT, InventoryUtils, ItemUtils}
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * 拆解机（原 1.7.10 `common.tileentity.Disassembler`）：把物品拆回合成原料。
 *
 * 纹理：下/上 = DisassemblerTop，北 = DisassemblerFront，南 = DisassemblerBack，其它 = DisassemblerSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`；`decrStackSize` → `extractItem`。
 *  - `updateEntity()` → [[li.cil.oc.common.tileentity.traits.TileEntity#tick]]；
 *    `world.getTotalWorldTime` → `world.getGameTime`；`world.rand` → `world.getRandom`。
 *  - `world.notifyBlocksOfNeighborChange(x, y, z, block)` → `notifyNeighbors()`。
 *  - `stack.stackSize` → `stack.getCount`；`ItemStack.loadItemStackFromNBT` → `ItemStack.parseOptional`；
 *    `NBT.TAG_COMPOUND` → [[net.minecraft.nbt.Tag.TAG_COMPOUND]]；`nbt.getInteger` → `nbt.getInt`。
 *  - `player.capabilities.isCreativeMode` → `player.isCreative`；`world.isRemote` → `!isServer`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 *
 * 降级：
 *  - `li.cil.oc.common.template.DisassemblerTemplates` 未移植，见 [[DisassemblerTemplates]] 占位。
 *  - `ServerPacketSender.sendDisassemblerActive(this, isActive)` → [[markBlockForUpdate]] + TODO。
 */
class Disassembler(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.PowerAcceptor with traits.Inventory with traits.StateAware with traits.PlayerInputAware with DeviceInfo {

  val node = api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferConverter).
    create()

  var isActive = false

  val queue = mutable.ArrayBuffer.empty[ItemStack]

  var totalRequiredEnergy = 0.0

  var buffer = 0.0

  var disassembleNextInstantly = false

  def progress: Double = if (queue.isEmpty) 0.0 else (1 - (queue.size * Settings.get.disassemblerItemCost - buffer) / totalRequiredEnergy) * 100

  private def setActive(value: Boolean): Unit = if (value != isActive) {
    isActive = value
    // TODO(server.PacketSender): 原为 ServerPacketSender.sendDisassemblerActive(this, isActive)。
    markBlockForUpdate()
    notifyNeighbors()
  }

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Disassembler",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Break.3R-100"
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（只应由客户端渲染调用）。
  override protected def hasConnector(side: Direction): Boolean = side != Direction.UP

  override protected def connector(side: Direction): Option[api.network.Connector] =
    Option(if (side != Direction.UP) node else null)

  override def energyThroughput: Double = Settings.get.disassemblerRate

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (isActive) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else if (queue.nonEmpty) util.EnumSet.of(api.util.StateAware.State.CanWork)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    super.tick()
    if (world.getGameTime % Settings.get.tickFrequency == 0) {
      if (queue.isEmpty) {
        val instant = disassembleNextInstantly // Is reset via decrStackSize
        disassemble(extractItem(0, 1, false), instant)
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
          if (disassembleNextInstantly || world.getRandom.nextDouble() >= Settings.get.disassemblerBreakChance) {
            drop(stack)
          }
        }
      }
      disassembleNextInstantly = queue.nonEmpty // If we have nothing left to do, stop being creative.
    }
  }

  def disassemble(stack: ItemStack, instant: Boolean = false): Unit = {
    // Validate the item, never trust Minecraft / other Mods on anything!
    if (isItemValid(0, stack)) {
      val ingredients = ItemUtils.getIngredients(stack)
      DisassemblerTemplates.select(stack) match {
        case Some(template) =>
          val (stacks, drops) = template.disassemble(stack, ingredients)
          stacks.foreach(queue ++= _)
          drops.foreach(_.foreach(drop))
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
    if (stack != null && !stack.isEmpty) {
      for (side <- Direction.values() if stack.getCount > 0) {
        InventoryUtils.insertIntoInventoryAt(stack, BlockPosition(this).offset(side), Some(side.getOpposite))
      }
      if (stack.getCount > 0) {
        spawnStackInWorld(stack, Option(Direction.UP))
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    queue.clear()
    queue ++= nbt.getList(Settings.namespace + "queue", Tag.TAG_COMPOUND).
      map((tag: CompoundTag) => ItemStack.parseOptional(ExtendedNBT.fallbackRegistry, tag)).
      filter(stack => stack != null && !stack.isEmpty)
    buffer = nbt.getDouble(Settings.namespace + "buffer")
    totalRequiredEnergy = nbt.getDouble(Settings.namespace + "total")
    isActive = queue.nonEmpty
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    // `queue` 是 ArrayBuffer，显式转换到 `Iterable[Tag]`（避免依赖隐式链）。
    nbt.setNewTagList(Settings.namespace + "queue", queue.toIndexedSeq.map(ExtendedNBT.toNbt))
    nbt.putDouble(Settings.namespace + "buffer", buffer)
    nbt.putDouble(Settings.namespace + "total", totalRequiredEnergy)
  }

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override protected def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    isActive = nbt.getBoolean("isActive")
  }

  override protected def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putBoolean("isActive", isActive)
  }

  // ----------------------------------------------------------------------- //

  override def getSlots: Int = 1

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    allowDisassembling(stack) &&
      (((Settings.get.disassembleAllTheThings || api.Items.get(stack) != null) && ItemUtils.getIngredients(stack).nonEmpty) ||
        DisassemblerTemplates.select(stack).isDefined)

  private def allowDisassembling(stack: ItemStack): Boolean =
    stack != null && !stack.isEmpty &&
      (!stack.hasTag() || !stack.getTag().getBoolean(Settings.namespace + "undisassemblable"))

  override def setInventorySlotContents(slot: Int, stack: ItemStack): Unit = {
    super.setInventorySlotContents(slot, stack)
    if (isServer) {
      disassembleNextInstantly = false
    }
  }

  override def onSetInventorySlotContents(player: Player, slot: Int, stack: ItemStack): Unit = {
    if (isServer) {
      disassembleNextInstantly = stack != null && !stack.isEmpty && slot == 0 && player.isCreative
    }
  }

  // ----------------------------------------------------------------------- //
  // TODO(common.template): `li.cil.oc.common.template.DisassemblerTemplates` 尚未移植。
  //
  // 原实现在 `common/template/DisassemblerTemplates.scala` 里：由 IMC 注册的模板（机器人 /
  // 平板 / 微控制器 / 服务器 / 无人机）通过 `select(stack)` 选中，`disassemble(stack, ingredients)`
  // 返回「装配出的子部件」与「额外掉落」两组物品。
  //
  // 这里给出**最小占位**（`select` 恒返回 `None`），拆解退化为 `ItemUtils.getIngredients` 的
  // 纯合成原料拆解；模板层移植后把本节整体删除，改回
  // `import li.cil.oc.common.template.DisassemblerTemplates` 即可（调用点无需改动）。
  // ----------------------------------------------------------------------- //

  private object DisassemblerTemplates {
    def select(stack: ItemStack): Option[DisassemblerTemplate] = None
  }

  private trait DisassemblerTemplate {
    /** 返回（装配出的子部件，额外掉落）；两者都可能为 `None`。 */
    def disassemble(stack: ItemStack, ingredients: Array[ItemStack]): (Option[Array[ItemStack]], Option[Array[ItemStack]])
  }
}
