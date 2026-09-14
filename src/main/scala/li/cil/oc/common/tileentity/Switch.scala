package li.cil.oc.common.tileentity

import li.cil.oc.api.Driver
import li.cil.oc.api.network.Packet
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

// TODO Remove in 1.7
/**
 * 网络交换机（对应 1.7.10 的 `common.tileentity.Switch`）。
 *
 * 继承 [[traits.SwitchLike]]（= [[traits.Hub]] + 中继冷却 / 活动指示灯），
 * 并带三个组件槽（CPU / 内存 / 硬盘），它们分别影响中继延迟、中继并发量与队列上限。
 *
 * 纹理：下 = None，上 = SwitchTop，其它四面 = SwitchSide（活动时用 SwitchSideOn）。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - 物品栏后端改为 `IItemHandler`：`getSizeInventory` → `getSlots`，
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `readFromNBTForServer` 在 1.21.1 是 `protected` 钩子。
 *
 * ==降级说明==
 *  - TODO(common.InventorySlots): `li.cil.oc.common.InventorySlots` 尚未纳入编译范围，
 *    这里内联了 `InventorySlots.switch` 的槽位表（CPU / Memory / HDD，均为三级）。
 *  - TODO(common.item.Delegator): 原实现用 `Delegator.subItem(stack)` 识别 `item.Memory`
 *    以取内存条的等级；`Delegator` 未纳入编译范围，这里统一用驱动报告的等级。
 *  - TODO(integration.Mods): 原实现在 `Mods.ComputerCraft.isAvailable` 时把网络包转发给
 *    ComputerCraft 的电脑（`IComputerAccess#queueEvent`）。ComputerCraft 集成未移植
 *    （`dan200.computercraft.*` 不存在），[[queueMessage]] 保留签名但不再分发事件。
 */
class Switch(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.SwitchLike with traits.NotAnalyzable with traits.ComponentInventory {

  override def isWirelessEnabled: Boolean = false

  override def isLinkedEnabled: Boolean = false

  override def canUpdate: Boolean = isServer

  // ----------------------------------------------------------------------- //

  /**
   * 把中继出去的包转发给本机上的 ComputerCraft 电脑（原 `queueMessage`）。
   *
   * TODO(integration.Mods): 原实现遍历 `computers` 里的
   * `dan200.computercraft.api.peripheral.IComputerAccess`，用
   * `s"cc${computer.getID}_${computer.getAttachmentName}"` 算出地址，匹配源 / 目标地址与
   * 已打开的端口后调用 `queueEvent("modem_message", ...)`。ComputerCraft 集成不再移植，
   * 这里保留方法签名与 `computers` / `openPorts` 字段（由未来的集成层维护）但不分发事件。
   */
  protected def queueMessage(source: String, destination: String, port: Int, answerPort: Int, args: Array[AnyRef]): Unit = {
  }

  // ----------------------------------------------------------------------- //

  override def tryEnqueuePacket(sourceSide: Option[Direction], packet: Packet): Boolean = {
    if (Switch.computerCraftAvailable) {
      packet.data.headOption match {
        case Some(answerPort: java.lang.Double) => queueMessage(packet.source, packet.destination, packet.port, answerPort.toInt, packet.data.drop(1))
        case _ => queueMessage(packet.source, packet.destination, packet.port, -1, packet.data)
      }
    }
    super.tryEnqueuePacket(sourceSide, packet)
  }

  override protected def relayPacket(sourceSide: Option[Direction], packet: Packet): Unit = {
    super.relayPacket(sourceSide, packet)
    onSwitchActivity()
  }

  // ----------------------------------------------------------------------- //

  override protected def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    updateLimits(slot, stack)
  }

  private def updateLimits(slot: Int, stack: ItemStack): Unit = {
    Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver) if driver.slot(stack) == Slot.CPU =>
        relayDelay = math.max(1, relayBaseDelay - ((driver.tier(stack) + 1) * relayDelayPerUpgrade).toInt)
      case Some(driver) if driver.slot(stack) == Slot.Memory =>
        // 原实现：`Delegator.subItem(stack)` 命中 `item.Memory` 时按 `(ram.tier + 1) * relayAmountPerUpgrade`，
        // 否则按 `(driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)`；这里统一走后者。
        relayAmount = math.max(1, relayBaseAmount + (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2))
      case Some(driver) if driver.slot(stack) == Slot.HDD =>
        maxQueueSize = math.max(1, queueBaseSize + (driver.tier(stack) + 1) * queueSizePerUpgrade)
      case _ => // Dafuq u doin.
    }
  }

  override protected def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    Option(Driver.driverFor(stack, getClass)) match {
      case Some(driver) if driver.slot(stack) == Slot.CPU => relayDelay = relayBaseDelay
      case Some(driver) if driver.slot(stack) == Slot.Memory => relayAmount = relayBaseAmount
      case Some(driver) if driver.slot(stack) == Slot.HDD => maxQueueSize = queueBaseSize
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //

  /** 原 `InventorySlots.switch` 的槽位表（见类注释里的 TODO）。 */
  private def providedSlot(slot: Int): (String, Int) = slot match {
    case 0 => (Slot.CPU, Tier.Three)
    case 1 => (Slot.Memory, Tier.Three)
    case 2 => (Slot.HDD, Tier.Three)
    case _ => ("", -1)
  }

  override def getSlots: Int = Switch.slotCount

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    Option(Driver.driverFor(stack, getClass)).fold(false)(driver => {
      val provided = providedSlot(slot)
      driver.slot(stack) == provided._1 && driver.tier(stack) <= provided._2
    })

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    for (slot <- items.indices) items(slot) collect {
      case stack => updateLimits(slot, stack)
    }
  }
}

object Switch {

  /** 原 `InventorySlots.switch.length`（见类注释里的 TODO）。 */
  private[tileentity] final val slotCount = 3

  /** TODO(integration.Mods): 原为 `Mods.ComputerCraft.isAvailable`；ComputerCraft 集成未移植，恒为 false。 */
  private[tileentity] final val computerCraftAvailable = false
}
