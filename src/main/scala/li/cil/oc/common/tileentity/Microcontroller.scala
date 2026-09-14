package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 微控制器（原 1.7.10 `common.tileentity.Microcontroller`）：单方块计算机，
 * 通过 [[traits.Hub]] 的六个「插座（plug）」节点在六个面上收发网络包。
 *
 * 纹理：下/上 = MicrocontrollerTop，北 = MicrocontrollerFront，南 = MicrocontrollerBack，
 * 其它 = MicrocontrollerSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `ISidedInventory` 已移除：微控制器的物品栏就是 [[info]] 里的组件数组，
 *    「外部不可插入 / 不可抽取」的语义改为覆写 `isItemValid` / `insertItem` / `extractItem`。
 *  - `getSizeInventory` → `getSlots`。
 *  - `updateEntity()` → [[li.cil.oc.common.tileentity.traits.TileEntity#tick]]；
 *    `world.getTotalWorldTime` → `world.getGameTime`。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`；
 *    `ForgeDirection.getOrientation(i)` → `Direction.from3DDataValue(i)`。
 *  - `asJavaIterable(...)` → `.asJava`；`NBT.TAG_COMPOUND` → [[net.minecraft.nbt.Tag.TAG_COMPOUND]]。
 *  - `ListTag#toArray[T]` 在 1.21.1 会被 Java 的 `AbstractCollection#toArray` 抢走，
 *    改用 [[li.cil.oc.util.ExtendedNBT]] 提供的 `map`（见 [[readFromNBTForServer]]）。
 *  - `msg.data: _*` → `msg.data.toIndexedSeq: _*`（Scala 2.13 的数组到变参转换）。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 */
class Microcontroller(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.PowerAcceptor with traits.Hub with traits.Computer with internal.Microcontroller with DeviceInfo {

  val info = new MicrocontrollerData()

  override def node: Node = null

  val outputSides = Array.fill(6)(true)

  val snooperNode: ComponentConnector = api.Network.newNode(this, Visibility.Network).
    withComponent("microcontroller").
    withConnector(Settings.get.bufferMicrocontroller).
    create()

  // 注意：`Array.fill(6)(expr)` 只求值一次，因此六项指向**同一个**节点对象；
  // 这与 1.7.10 原实现一致，保持不变以免改变网络行为。
  val componentNodes = Array.fill(6)(api.Network.newNode(this, Visibility.Network).
    withComponent("microcontroller").
    create())

  if (machine != null) {
    machine.node.asInstanceOf[Connector].setLocalBufferSize(0)
    machine.setCostPerTick(Settings.get.microcontrollerCost)
  }

  override def tier: Int = info.tier

  override def runSound: Option[String] = None // Microcontrollers are silent.

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.System,
    DeviceAttribute.Description -> "Microcontroller",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Cubicle",
    DeviceAttribute.Capacity -> getSlots.toString
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（只应由客户端渲染调用）。
  override def canConnect(side: Direction): Boolean = side != facing

  override def sidedNode(side: Direction): Node = if (side != facing) super.sidedNode(side) else null

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override def hasConnector(side: Direction): Boolean = side != facing

  override def connector(side: Direction): Option[Connector] = Option(if (side != facing) snooperNode else null)

  override def energyThroughput: Double = Settings.get.caseRate(Tier.One)

  override def getWorld = world

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = {
    super.onAnalyze(player, side, hitX, hitY, hitZ)
    if (Direction.from3DDataValue(side) != facing)
      Array(componentNodes(side))
    else
      Array(machine.node)
  }

  // ----------------------------------------------------------------------- //

  override def internalComponents(): java.lang.Iterable[ItemStack] = info.components.toIndexedSeq.asJava

  override def componentSlot(address: String): Int =
    components.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function():boolean -- Starts the microcontroller. Returns true if the state changed.""")
  def start(context: Context, args: Arguments): Array[AnyRef] =
    result(!machine.isPaused && machine.start())

  @Callback(doc = """function():boolean -- Stops the microcontroller. Returns true if the state changed.""")
  def stop(context: Context, args: Arguments): Array[AnyRef] =
    result(machine.stop())

  @Callback(direct = true, doc = """function():boolean -- Returns whether the microcontroller is running.""")
  def isRunning(context: Context, args: Arguments): Array[AnyRef] =
    result(machine.isRunning)

  @Callback(direct = true, doc = """function():string -- Returns the reason the microcontroller crashed, if applicable.""")
  def lastError(context: Context, args: Arguments): Array[AnyRef] =
    result(machine.lastError)

  @Callback(direct = true, doc = """function(side:number):boolean -- Get whether network messages are sent via the specified side.""")
  def isSideOpen(context: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideExcept(0, facing)
    result(outputSides(side.ordinal()))
  }

  @Callback(doc = """function(side:number, open:boolean):boolean -- Set whether network messages are sent via the specified side.""")
  def setSideOpen(context: Context, args: Arguments): Array[AnyRef] = {
    val side = args.checkSideExcept(0, facing)
    val oldValue = outputSides(side.ordinal())
    outputSides(side.ordinal()) = args.checkBoolean(1)
    result(oldValue)
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    super.tick()

    // Pump energy into the internal network.
    if (isServer && world.getGameTime % Settings.get.tickFrequency == 0) {
      for (side <- Direction.values() if side != facing) {
        sidedNode(side) match {
          case connector: Connector =>
            val demand = snooperNode.globalBufferSize - snooperNode.globalBuffer
            val available = demand + connector.changeBuffer(-demand)
            snooperNode.changeBuffer(available)
          case _ =>
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def connectItemNode(node: Node): Unit = {
    if (machine != null && machine.node != null && node != null) {
      api.Network.joinNewNetwork(machine.node)
      machine.node.connect(node)
    }
  }

  // ----------------------------------------------------------------------- //

  override def createNode(plug: Plug): Node = api.Network.newNode(plug, Visibility.Network).
    withConnector().
    create()

  override def onPlugConnect(plug: Plug, node: Node): Unit = {
    super.onPlugConnect(plug, node)
    if (machine != null) {
      if (node == plug.node) {
        api.Network.joinNewNetwork(machine.node)
        machine.node.connect(snooperNode)
        connectComponents()
      }
    }
    if (plug.isPrimary)
      plug.node.connect(componentNodes(plug.side.ordinal()))
    else
      componentNodes(plug.side.ordinal).remove()
  }

  override def onPlugDisconnect(plug: Plug, node: Node): Unit = {
    super.onPlugDisconnect(plug, node)
    if (plug.isPrimary && node != plug.node)
      plug.node.connect(componentNodes(plug.side.ordinal()))
    else
      componentNodes(plug.side.ordinal).remove()
    if (node == plug.node)
      disconnectComponents()
  }

  override def onPlugMessage(plug: Plug, message: Message): Unit = {
    if (message.name == "network.message" && message.source.network != snooperNode.network) {
      snooperNode.sendToReachable(message.name, message.data.toIndexedSeq: _*)
    }
  }

  override def onMessage(message: Message): Unit = {
    if (message.name == "network.message" && message.source.network == snooperNode.network) {
      for (side <- Direction.values() if outputSides(side.ordinal) && side != facing) {
        val node = sidedNode(side)
        if (node != null) {
          node.sendToReachable(message.name, message.data.toIndexedSeq: _*)
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    // Load info before inventory and such, to avoid initializing components
    // to empty inventory.
    info.load(nbt.getCompound(Settings.namespace + "info"))
    // 注意：1.7.10 原实现写的是 `nbt.getBooleanArray(Settings.namespace + "outputs")`，
    // 返回值被丢弃（疑似笔误），导致 outputSides 读档后不会恢复。这里按显然的意图恢复赋值。
    nbt.getBooleanArray(Settings.namespace + "outputs").copyToArray(outputSides)
    nbt.getList(Settings.namespace + "componentNodes", Tag.TAG_COMPOUND).
      map((tag: CompoundTag) => tag).
      zipWithIndex.foreach {
      case (tag, index) => if (index < componentNodes.length) componentNodes(index).load(tag)
    }
    snooperNode.load(nbt.getCompound(Settings.namespace + "snooper"))
    super.readFromNBTForServer(nbt)
    if (machine != null) {
      api.Network.joinNewNetwork(machine.node)
      machine.node.connect(snooperNode)
    }
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.setNewCompoundTag(Settings.namespace + "info", info.save)
    nbt.setBooleanArray(Settings.namespace + "outputs", outputSides)
    nbt.setNewTagList(Settings.namespace + "componentNodes", componentNodes.toIndexedSeq.map {
      case node: Node =>
        val tag = new CompoundTag()
        node.save(tag)
        tag
      case _ => new CompoundTag()
    })
    nbt.setNewCompoundTag(Settings.namespace + "snooper", snooperNode.save)
  }

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    info.load(nbt.getCompound("info"))
    super.readFromNBTForClient(nbt)
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.setNewCompoundTag("info", info.save)
  }

  // ----------------------------------------------------------------------- //
  // 物品栏：微控制器的组件数组由 `info.components` 承载，外部不可插入 / 抽取。
  // ----------------------------------------------------------------------- //

  override def items: Array[Option[ItemStack]] =
    info.components.map(stack => Option(stack).filter(s => s != null && !s.isEmpty))

  override def updateItems(slot: Int, stack: ItemStack): Unit = info.components(slot) = stack

  override def getSlots: Int = info.components.length

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = false

  // Nope.
  override def setInventorySlotContents(slot: Int, stack: ItemStack): Unit = {}

  // Nope.
  override def insertItem(slot: Int, stack: ItemStack, simulate: Boolean): ItemStack = stack

  // Nope.
  override def extractItem(slot: Int, amount: Int, simulate: Boolean): ItemStack = ItemStack.EMPTY

  // For hotswapping EEPROMs.
  def changeEEPROM(newEeprom: ItemStack): Option[ItemStack] = {
    val eeprom = api.Items.get(Constants.ItemName.EEPROM)
    val oldEepromIndex = info.components.indexWhere(stack => stack != null && api.Items.get(stack) == eeprom)
    if (oldEepromIndex >= 0) {
      val oldEeprom = info.components(oldEepromIndex)
      super.setInventorySlotContents(oldEepromIndex, newEeprom)
      Option(oldEeprom)
    }
    else {
      val last = getSlots - 1
      assert(last < 0 || info.components(last) == null || info.components(last).isEmpty)
      if (last >= 0) {
        super.setInventorySlotContents(last, newEeprom)
      }
      None
    }
  }
}
