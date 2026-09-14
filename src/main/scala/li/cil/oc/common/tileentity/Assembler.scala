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
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.IItemHandler

import scala.jdk.CollectionConverters._

/**
 * 装配机（原 1.7.10 `common.tileentity.Assembler`）：把模板要求的部件组装成机器人 / 平板等物品。
 *
 * 纹理：下/上 = AssemblerTop，北 = AssemblerFront，南 = AssemblerBack，其它 = AssemblerSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `updateEntity()` → `tick()`；`world.getTotalWorldTime` → `world.getGameTime`。
 *  - `ItemStack.loadItemStackFromNBT` → `ItemStack.parseOptional`（走 [[li.cil.oc.util.ExtendedNBT]]）；
 *    `stack.writeToNBT` → `stack.save`。
 *  - 删除 `@SideOnly`。
 *
 * 降级：`li.cil.oc.common.template.AssemblerTemplates` 尚未移植，见 [[AssemblerTemplates]] 占位。
 */
class Assembler(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.PowerAcceptor with traits.Inventory with SidedEnvironment with traits.StateAware with DeviceInfo {

  val node = api.Network.newNode(this, Visibility.Network).
    withComponent("assembler").
    withConnector(Settings.get.bufferConverter).
    create()

  var output: Option[ItemStack] = None

  var totalRequiredEnergy = 0.0

  var requiredEnergy = 0.0

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Generic,
    DeviceAttribute.Description -> "Assembler",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Factorizer R1D1"
  )

  // 1.7.10 的 `scala.collection.convert.WrapAsJava._` 提供隐式转换；
  // 1.21.1（Scala 2.13）改为显式 `.asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`，1.21.1 删除注解（仅客户端渲染会调用 canConnect）。
  override def canConnect(side: Direction): Boolean = side != Direction.UP

  override def sidedNode(side: Direction): Node = if (side != Direction.UP) node else null

  override def hasConnector(side: Direction): Boolean = canConnect(side)

  override def connector(side: Direction): Option[Connector] = Option(if (side != Direction.UP) node else null)

  override def energyThroughput: Double = Settings.get.assemblerRate

  override def getCurrentState: util.EnumSet[api.util.StateAware.State] = {
    if (isAssembling) util.EnumSet.of(api.util.StateAware.State.IsWorking)
    else if (canAssemble) util.EnumSet.of(api.util.StateAware.State.CanWork)
    else util.EnumSet.noneOf(classOf[api.util.StateAware.State])
  }

  // ----------------------------------------------------------------------- //

  def canAssemble: Boolean = AssemblerTemplates.select(getStackInSlot(0)) match {
    case Some(template) => !isAssembling && output.isEmpty && template.validate(this)._1
    case _ => false
  }

  def isAssembling: Boolean = requiredEnergy > 0

  def progress: Double = (1 - requiredEnergy / totalRequiredEnergy) * 100

  def timeRemaining: Int = (requiredEnergy / Settings.get.assemblerTickAmount / 20).toInt

  // ----------------------------------------------------------------------- //

  def start(finishImmediately: Boolean = false): Boolean = this.synchronized {
    AssemblerTemplates.select(getStackInSlot(0)) match {
      case Some(template) if !isAssembling && output.isEmpty && template.validate(this)._1 =>
        for (slot <- 0 until getSlots) {
          val stack = getStackInSlot(slot)
          if (stack != null && !stack.isEmpty && !isItemValid(slot, stack)) return false
        }
        val (stack, energy) = template.assemble(this)
        output = Option(stack)
        if (finishImmediately) {
          totalRequiredEnergy = 0
        }
        else {
          totalRequiredEnergy = math.max(1.0, energy)
        }
        requiredEnergy = totalRequiredEnergy
        // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotAssembling(this, assembling = true)。
        markBlockForUpdate()

        for (slot <- 0 until getSlots) updateItems(slot, null)
        markDirty()

        true
      case _ => false
    }
  }

  // ----------------------------------------------------------------------- //

  @Callback(doc = """function(): string, number or boolean -- The current state of the assembler, `busy' or `idle', followed by the progress or template validity, respectively.""")
  def status(context: Context, args: Arguments): Array[Object] = {
    if (isAssembling) result("busy", Double.box(progress))
    else AssemblerTemplates.select(getStackInSlot(0)) match {
      case Some(template) if template.validate(this)._1 => result("idle", Boolean.box(true))
      case _ => result("idle", Boolean.box(false))
    }
  }

  @Callback(doc = """function():boolean -- Start assembling, if possible. Returns whether assembly was started or not.""")
  def start(context: Context, args: Arguments): Array[Object] = result(Boolean.box(start()))

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    super.tick()
    if (output.isDefined && world.getGameTime % Settings.get.tickFrequency == 0) {
      val want = math.max(1.0, math.min(requiredEnergy, Settings.get.assemblerTickAmount * Settings.get.tickFrequency))
      val have = want + (if (Settings.get.ignorePower) 0 else node.changeBuffer(-want))
      requiredEnergy -= have
      if (requiredEnergy <= 0) {
        setInventorySlotContents(0, output.get)
        output = None
        requiredEnergy = 0
      }
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendRobotAssembling(this, have > 0.5 && output.isDefined)。
      markBlockForUpdate()
    }
  }

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "output")) {
      output = Option(ItemStack.parseOptional(li.cil.oc.util.ExtendedNBT.fallbackRegistry, nbt.getCompound(Settings.namespace + "output")))
    }
    else if (nbt.contains(Settings.namespace + "robot")) {
      // Backwards compatibility.
      output = Option(ItemStack.parseOptional(li.cil.oc.util.ExtendedNBT.fallbackRegistry, nbt.getCompound(Settings.namespace + "robot")))
    }
    totalRequiredEnergy = nbt.getDouble(Settings.namespace + "total")
    requiredEnergy = nbt.getDouble(Settings.namespace + "remaining")
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    output.foreach(stack => nbt.setNewCompoundTag(Settings.namespace + "output",
      (tag: CompoundTag) => stack.save(li.cil.oc.util.ExtendedNBT.fallbackRegistry, tag)))
    nbt.putDouble(Settings.namespace + "total", totalRequiredEnergy)
    nbt.putDouble(Settings.namespace + "remaining", requiredEnergy)
  }

  // 原 `@SideOnly(Side.CLIENT)`，1.21.1 删除注解。
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    requiredEnergy = nbt.getDouble("remaining")
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.putDouble("remaining", requiredEnergy)
  }

  // ----------------------------------------------------------------------- //

  override def getSlots: Int = 22

  override def getInventoryStackLimit: Int = 1

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    if (slot == 0) {
      !isAssembling && AssemblerTemplates.select(stack).isDefined
    }
    else AssemblerTemplates.select(getStackInSlot(0)) match {
      case Some(template) =>
        val tplSlot =
          if ((1 until 4) contains slot) template.containerSlots(slot - 1)
          else if ((4 until 13) contains slot) template.upgradeSlots(slot - 4)
          else if ((13 until 21) contains slot) template.componentSlots(slot - 13)
          else AssemblerTemplates.NoSlot
        tplSlot.validate(this, slot, stack)
      case _ => false
    }

  // ----------------------------------------------------------------------- //
  // TODO(common.template): `li.cil.oc.common.template.AssemblerTemplates` 尚未移植。
  //
  // 原实现在 `common/template/AssemblerTemplates.scala` 里：由 IMC 注册的模板（机器人 / 平板 /
  // 微控制器 / 服务器 / 无人机）通过 `select(stack)` 选中，`validate(assembler)` 校验物品栏，
  // `assemble(assembler)` 返回产物与所需能量；槽位约束由 `Template.Slot` 描述。
  //
  // 这里给出**最小占位**（`select` 恒返回 `None`），保证 API 表面与类型不变：
  // 装配机当前不会真正装配，槽位校验一律返回 false。
  // 模板层移植后，把本节整体删除，改回 `import li.cil.oc.common.template.AssemblerTemplates`
  // 并让 `Template.Slot#validate` 接受 `IItemHandler` 即可（调用点无需改动）。
  // ----------------------------------------------------------------------- //

  private object AssemblerTemplates {
    /** 原 `AssemblerTemplates.NoSlot`：表示「没有匹配的槽位」。 */
    val NoSlot = new TemplateSlot(Slot.None, Tier.Any)

    def select(stack: ItemStack): Option[AssemblerTemplate] = None
  }

  private trait AssemblerTemplate {
    def validate(inventory: IItemHandler): (Boolean, String)

    def assemble(inventory: IItemHandler): (ItemStack, Double)

    def containerSlots: Array[TemplateSlot]

    def upgradeSlots: Array[TemplateSlot]

    def componentSlots: Array[TemplateSlot]
  }

  private class TemplateSlot(val slot: String, val tier: Int) {
    /** 原 `AssemblerTemplates.Slot#validate(inventory, slot, stack)`。 */
    def validate(inventory: IItemHandler, slot: Int, stack: ItemStack): Boolean = false
  }

}
