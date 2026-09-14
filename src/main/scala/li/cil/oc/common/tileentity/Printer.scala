package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network._
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.util.ExtendedNBT
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

import scala.jdk.CollectionConverters._

/**
 * 3D 打印机（原 1.7.10 `common.tileentity.Printer`）。
 *
 * 纹理：下/上 = PrinterTop，北 = PrinterFront，南 = PrinterBack，其它 = PrinterSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `ISidedInventory` 已移除：物品栏改为 `IItemHandler`（由 [[traits.Inventory]] 提供），
 *    `getSizeInventory` → `getSlots`、`isItemValidForSlot` → `isItemValid`。
 *    原来按面限制槽位的方法（[[getAccessibleSlotsFromSide]] / [[canInsertItem]] /
 *    [[canExtractItem]]）保留为普通方法，供 `Registry` 注册按面的物品能力时使用。
 *  - `updateEntity()` → [[li.cil.oc.common.tileentity.traits.TileEntity#tick]]（覆写时先 `super.tick()`）。
 *  - `world.markBlockForUpdate(x, y, z)` → [[li.cil.oc.common.tileentity.traits.TileEntity#markBlockForUpdate]]。
 *  - `AABB.getBoundingBox(...)` → `new AABB(...)`；
 *    `presentStack.isItemEqual(out) && ItemStack.areItemStackTagsEqual(...)` →
 *    `ItemStack.isSameItemSameComponents`（1.21.1 的物品组件同时涵盖旧版 damage + NBT）。
 *  - `Item#hasContainerItem/getContainerItem` → `ItemStack#getCraftingRemainingItem`。
 *  - `stack.stackSize` → `stack.getCount` / `stack.setCount`；
 *    `nbt.getInteger` → `nbt.getInt`；`ItemStack.loadItemStackFromNBT` → `ItemStack.parseOptional`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 *
 * 降级：原 `ServerPacketSender.sendPrinting(this, printing)` → [[markBlockForUpdate]] + TODO。
 */
class Printer(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.Inventory with traits.Rotatable with SidedEnvironment with traits.StateAware with DeviceInfo {

  // 显式保留 1.7.10 的推断类型 `ComponentConnector`（`withComponent` + `withConnector` 的组合），
  // 后面的 `node.changeBuffer` 需要 `Connector` 接口。
  val node: ComponentConnector = api.Network.newNode(this, Visibility.Network).
    withComponent("printer3d").
    withConnector(Settings.get.bufferConverter).
    create()

  val maxAmountMaterial = 256000
  var amountMaterial = 0
  val maxAmountInk = 100000
  var amountInk = 0

  var data = new PrintData()
  var isActive = false
  var limit = 0
  var output: Option[ItemStack] = None
  var totalRequiredEnergy = 0.0
  var requiredEnergy = 0.0

  val slotMaterial = 0
  val slotInk = 1
  val slotOutput = 2

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Printer,
    DeviceAttribute.Description -> "3D Printer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Omni-Materializer T6.1"
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解（该方法只应由客户端渲染调用）。
  override def canConnect(side: Direction): Boolean = side != Direction.UP

  override def sidedNode(side: Direction): Node = if (side != Direction.UP) node else null

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (isPrinting) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else if (canPrint) util.EnumSet.of(api.util.StateAware.State.CanWork)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //

  def canPrint: Boolean = data.stateOff.nonEmpty && data.stateOff.size <= Settings.get.maxPrintComplexity && data.stateOn.size <= Settings.get.maxPrintComplexity

  def isPrinting: Boolean = output.isDefined

  def progress: Double = (1 - requiredEnergy / totalRequiredEnergy) * 100

  def timeRemaining: Int = (requiredEnergy / Settings.get.assemblerTickAmount / 20).toInt

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function() -- Resets the configuration of the printer and stop printing (current job will finish).""")
  def reset(context: Context, args: Arguments): Array[Object] = {
    data = new PrintData()
    isActive = false // Needs committing.
    null
  }

  @Callback(doc = """function(value:string) -- Set a label for the block being printed.""")
  def setLabel(context: Context, args: Arguments): Array[Object] = {
    data.label = Option(args.optString(0, null)).map(_.take(24))
    if (data.label.fold(false)(_.isEmpty)) data.label = None
    isActive = false // Needs committing.
    null
  }

  @Callback(doc = """function():string -- Get the current label for the block being printed.""")
  def getLabel(context: Context, args: Arguments): Array[Object] = {
    result(data.label.orNull)
  }

  @Callback(doc = """function(value:string) -- Set a tooltip for the block being printed.""")
  def setTooltip(context: Context, args: Arguments): Array[Object] = {
    data.tooltip = Option(args.optString(0, null)).map(_.take(128))
    if (data.tooltip.fold(false)(_.isEmpty)) data.tooltip = None
    isActive = false // Needs committing.
    null
  }

  @Callback(doc = """function():string -- Get the current tooltip for the block being printed.""")
  def getTooltip(context: Context, args: Arguments): Array[Object] = {
    result(data.tooltip.orNull)
  }

  @Callback(doc = """function(value:number) -- Set what light level the printed block should have.""")
  def setLightLevel(context: Context, args: Arguments): Array[Object] = {
    data.lightLevel = args.checkInteger(0) max 0 min Settings.get.maxPrintLightLevel
    isActive = false // Needs committing.
    null
  }

  @Callback(doc = """function():number -- Get which light level the printed block should have.""")
  def getLightLevel(context: Context, args: Arguments): Array[Object] = {
    result(data.lightLevel)
  }

  @Callback(doc = """function(value:boolean or number) -- Set whether the printed block should emit redstone when in its active state.""")
  def setRedstoneEmitter(context: Context, args: Arguments): Array[Object] = {
    if (args.isBoolean(0)) data.redstoneLevel = if (args.checkBoolean(0)) 15 else 0
    else data.redstoneLevel = args.checkInteger(0) max 0 min 15
    isActive = false // Needs committing.
    null
  }

  @Callback(doc = """function():boolean, number -- Get whether the printed block should emit redstone when in its active state.""")
  def isRedstoneEmitter(context: Context, args: Arguments): Array[Object] = {
    result(data.emitRedstone, data.redstoneLevel)
  }

  @Callback(doc = """function(value:boolean) -- Set whether the printed block should automatically return to its off state.""")
  def setButtonMode(context: Context, args: Arguments): Array[Object] = {
    data.isButtonMode = args.checkBoolean(0)
    isActive = false // Needs committing.
    null
  }

  @Callback(doc = """function():boolean -- Get whether the printed block should automatically return to its off state.""")
  def isButtonMode(context: Context, args: Arguments): Array[Object] = {
    result(data.isButtonMode)
  }

  @Callback(doc = """function(collideOff:boolean, collideOn:boolean) -- Set whether the printed block should be collidable or not.""")
  def setCollidable(context: Context, args: Arguments): Array[Object] = {
    val (collideOff, collideOn) = (args.checkBoolean(0), args.checkBoolean(1))
    data.noclipOff = !collideOff
    data.noclipOn = !collideOn
    null
  }

  @Callback(doc = """function():boolean, boolean -- Get whether the printed block should be collidable or not.""")
  def isCollidable(context: Context, args: Arguments): Array[Object] = {
    result(!data.noclipOff, !data.noclipOn)
  }

  @Callback(doc = """function(minX:number, minY:number, minZ:number, maxX:number, maxY:number, maxZ:number, texture:string[, state:boolean=false][,tint:number]) -- Adds a shape to the printers configuration, optionally specifying whether it is for the off or on state.""")
  def addShape(context: Context, args: Arguments): Array[Object] = {
    if (data.stateOff.size > Settings.get.maxPrintComplexity || data.stateOn.size > Settings.get.maxPrintComplexity) {
      return result(Unit, "model too complex")
    }
    val minX = (args.checkInteger(0) max 0 min 16) / 16f
    val minY = (args.checkInteger(1) max 0 min 16) / 16f
    val minZ = (16 - (args.checkInteger(2) max 0 min 16)) / 16f
    val maxX = (args.checkInteger(3) max 0 min 16) / 16f
    val maxY = (args.checkInteger(4) max 0 min 16) / 16f
    val maxZ = (16 - (args.checkInteger(5) max 0 min 16)) / 16f
    val texture = args.checkString(6).take(64)
    val state = if (args.isBoolean(7)) args.checkBoolean(7) else false
    val tint = if (args.isInteger(7)) Option(args.checkInteger(7)) else if (args.isInteger(8)) Option(args.checkInteger(8)) else None

    if (minX == maxX) throw new IllegalArgumentException("empty block")
    if (minY == maxY) throw new IllegalArgumentException("empty block")
    if (minZ == maxZ) throw new IllegalArgumentException("empty block")

    val list = if (state) data.stateOn else data.stateOff
    // 原 `AABB.getBoundingBox(...)`；1.21.1 直接用构造器。
    list += new PrintData.Shape(new AABB(
      math.min(minX, maxX),
      math.min(minY, maxY),
      math.min(minZ, maxZ),
      math.max(maxX, minX),
      math.max(maxY, minY),
      math.max(maxZ, minZ)),
      texture, tint)
    isActive = false // Needs committing.

    markBlockForUpdate()

    result(true)
  }

  @Callback(doc = """function():number -- Get the number of shapes in the current configuration.""")
  def getShapeCount(context: Context, args: Arguments): Array[Object] = result(data.stateOff.size, data.stateOn.size)

  @Callback(doc = """function():number -- Get the maximum allowed number of shapes.""")
  def getMaxShapeCount(context: Context, args: Arguments): Array[Object] = result(Settings.get.maxPrintComplexity)

  @Callback(doc = """function([count:number]):boolean -- Commit and begin printing the current configuration.""")
  def commit(context: Context, args: Arguments): Array[Object] = {
    if (!canPrint) {
      return result(Unit, "model invalid")
    }
    limit = (args.optDouble(0, 1) max 0 min Integer.MAX_VALUE).toInt
    isActive = limit > 0
    result(true)
  }

  @Callback(doc = """function(): string, number or boolean -- The current state of the printer, `busy' or `idle', followed by the progress or model validity, respectively.""")
  def status(context: Context, args: Arguments): Array[Object] = {
    if (isPrinting) result("busy", progress)
    else if (canPrint) result("idle", true)
    else result("idle", false)
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    super.tick()

    def canMergeOutput: Boolean = {
      val presentStack = getStackInSlot(slotOutput)
      val outputStack = data.createItemStack()
      presentStack == null || presentStack.isEmpty || ItemStack.isSameItemSameComponents(presentStack, outputStack)
    }

    if (isActive && output.isEmpty && canMergeOutput) {
      PrintData.computeCosts(data) match {
        case Some((materialRequired, inkRequired)) =>
          totalRequiredEnergy = Settings.get.printCost
          requiredEnergy = totalRequiredEnergy

          if (amountMaterial >= materialRequired && amountInk >= inkRequired) {
            amountMaterial -= materialRequired
            amountInk -= inkRequired
            limit -= 1
            output = Option(data.createItemStack())
            if (limit < 1) isActive = false
            // TODO(server.PacketSender): 原为 ServerPacketSender.sendPrinting(this, printing = true)。
            markBlockForUpdate()
          }
        case _ =>
          isActive = false
          data = new PrintData()
      }
    }

    if (output.isDefined) {
      val want = math.max(1, math.min(requiredEnergy, Settings.get.printerTickAmount))
      val have = want + (if (Settings.get.ignorePower) 0 else node.changeBuffer(-want))
      requiredEnergy -= have
      if (requiredEnergy <= 0) {
        val present = getStackInSlot(slotOutput)
        if (present == null || present.isEmpty) {
          setInventorySlotContents(slotOutput, output.get)
        }
        else if (present.getCount < present.getMaxStackSize && canMergeOutput /* Should never fail, but just in case... */ ) {
          present.grow(1)
          markDirty()
        }
        else {
          return
        }
        requiredEnergy = 0
        output = None
      }
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendPrinting(this, have > 0.5 && output.isDefined)。
      markBlockForUpdate()
    }

    val inputValue = PrintData.materialValue(getStackInSlot(slotMaterial))
    if (inputValue > 0 && maxAmountMaterial - amountMaterial >= inputValue) {
      val material = extractItem(slotMaterial, 1, false)
      if (material != null && !material.isEmpty) {
        amountMaterial += inputValue
      }
    }

    val inkValue = PrintData.inkValue(getStackInSlot(slotInk))
    if (inkValue > 0 && maxAmountInk - amountInk >= inkValue) {
      val material = extractItem(slotInk, 1, false)
      if (material != null && !material.isEmpty) {
        amountInk += inkValue
        // 原 `Item#hasContainerItem/getContainerItem`；1.21.1 合并为 `ItemStack#getCraftingRemainingItem`。
        val container = material.getCraftingRemainingItem
        if (container != null && !container.isEmpty) {
          setInventorySlotContents(slotInk, container)
        }
      }
    }
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    amountMaterial = nbt.getInt(Settings.namespace + "amountMaterial")
    amountInk = nbt.getInt(Settings.namespace + "amountInk")
    data.load(nbt.getCompound(Settings.namespace + "data"))
    isActive = nbt.getBoolean(Settings.namespace + "active")
    limit = nbt.getInt(Settings.namespace + "limit")
    if (nbt.contains(Settings.namespace + "output")) {
      output = Option(ItemStack.parseOptional(ExtendedNBT.fallbackRegistry, nbt.getCompound(Settings.namespace + "output"))).
        filter(stack => stack != null && !stack.isEmpty)
    }
    totalRequiredEnergy = nbt.getDouble(Settings.namespace + "total")
    requiredEnergy = nbt.getDouble(Settings.namespace + "remaining")
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    nbt.putInt(Settings.namespace + "amountMaterial", amountMaterial)
    nbt.putInt(Settings.namespace + "amountInk", amountInk)
    nbt.setNewCompoundTag(Settings.namespace + "data", data.save)
    nbt.putBoolean(Settings.namespace + "active", isActive)
    nbt.putInt(Settings.namespace + "limit", limit)
    output.foreach(stack => nbt.setNewCompoundTag(Settings.namespace + "output",
      (tag: CompoundTag) => stack.save(ExtendedNBT.fallbackRegistry, tag)))
    nbt.putDouble(Settings.namespace + "total", totalRequiredEnergy)
    nbt.putDouble(Settings.namespace + "remaining", requiredEnergy)
  }

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    data.load(nbt.getCompound(Settings.namespace + "data"))
    requiredEnergy = nbt.getDouble("remaining")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.setNewCompoundTag(Settings.namespace + "data", data.save)
    nbt.putDouble("remaining", requiredEnergy)
  }

  // ----------------------------------------------------------------------- //

  override def getSlots: Int = 3

  override def getSlotLimit(slot: Int): Int = 64

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    if (slot == slotMaterial)
      PrintData.materialValue(stack) > 0
    else if (slot == slotInk)
      PrintData.inkValue(stack) > 0
    else false

  // ----------------------------------------------------------------------- //
  // 旧 `ISidedInventory` 的按面访问限制。
  //
  // TODO(能力): 1.21.1 的面区分由 `Capabilities.ItemHandler.BLOCK` 的能力提供方按面返回不同的
  // `IItemHandler` 实现来完成。下面三个方法保持原语义，供 `Registry` 注册能力时构造按面包装。
  // ----------------------------------------------------------------------- //

  def getAccessibleSlotsFromSide(side: Int): Array[Int] = Array(slotMaterial, slotInk, slotOutput)

  def canExtractItem(slot: Int, stack: ItemStack, side: Int): Boolean = !isItemValid(slot, stack)

  def canInsertItem(slot: Int, stack: ItemStack, side: Int): Boolean = slot != slotOutput
}
