package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal
import li.cil.oc.api.network.Connector
import li.cil.oc.common
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.util.Color
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 机箱（原 1.7.10 `common.tileentity.Case`）：计算机的方块形态。
 *
 * 纹理：下/上 = CaseTop，北 = CaseFront，南 = CaseBack，其它 = CaseSide；
 * 正面的两个指示灯分别由 `lastFileSystemAccess` / `lastNetworkActivity` 驱动（客户端渲染）。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 用 metadata + 构造参数 `tier` 表示等级，且会从 NBT 里**改写** tier；
 *    1.21.1 每个等级是独立方块（`case1` / `case2` / `case3` / `caseCreative`），
 *    因此 [[tier]] 是 `val`，从方块实例反查，读档时不再修改。
 *  - 构造函数只有 `(pos, state)`；不再需要 1.7.10 的 `def this()`（那时是为了「未知等级时
 *    推迟物品栏尺寸」，现在等级在构造时就已知，`isSizeInventoryReady` 保持默认的 true）。
 *  - `getSizeInventory` → `getSlots`、`isItemValidForSlot` → `isItemValid`。
 *  - `player.capabilities.isCreativeMode` → `player.isCreative`。
 *  - 删除 `@SideOnly`；`ServerPacketSender` 相关调用按 §3 降级为方块更新。
 */
class Case(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.PowerAcceptor with traits.Computer with traits.Colored with internal.Case with DeviceInfo {

  /** 原 1.7.10 由 metadata 决定；1.21.1 从方块实例读取。 */
  val tier: Int = state.getBlock match {
    case b: li.cil.oc.common.block.Case => b.tier
    case _ => 0
  }

  // Used on client side to check whether to render disk activity/network indicators.
  var lastFileSystemAccess = 0L
  var lastNetworkActivity = 0L

  color = Color.byTier(tier)

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.System,
    DeviceAttribute.Description -> "Computer",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Blocker",
    DeviceAttribute.Capacity -> getSlots.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo

  // ----------------------------------------------------------------------- //

  // 原 `@SideOnly(Side.CLIENT)`，1.21.1 删除注解。
  override protected def hasConnector(side: Direction): Boolean = side != facing

  override protected def connector(side: Direction): Option[Connector] =
    Option(if (side != facing && machine != null) machine.node.asInstanceOf[Connector] else null)

  override def energyThroughput: Double = Settings.get.caseRate(tier)

  override def getWorld = world

  def isCreative: Boolean = tier == Tier.Four

  // ----------------------------------------------------------------------- //

  override def componentSlot(address: String): Int = components.indexWhere(_.exists(env => env.node != null && env.node.address == address))

  // ----------------------------------------------------------------------- //

  override def canUpdate: Boolean = isServer

  override def tick(): Unit = {
    if (isServer && isCreative && world.getGameTime % Settings.get.tickFrequency == 0) {
      // Creative case, make it generate power.
      // TODO(server.machine): 机器层移植前 `machine` 可能为 null（见 traits.Computer），这里做空值保护。
      if (machine != null) {
        machine.node.asInstanceOf[Connector].changeBuffer(Double.PositiveInfinity)
      }
    }
    super.tick()
  }

  // ----------------------------------------------------------------------- //

  override protected def readFromNBTForServer(nbt: CompoundTag): Unit = {
    // 1.7.10 在这里从 NBT 恢复 tier；1.21.1 的等级由方块决定，不能（也不需要）改写。
    color = Color.byTier(tier)
    super.readFromNBTForServer(nbt)
    isSizeInventoryReady = true
  }

  override protected def writeToNBTForServer(nbt: CompoundTag): Unit = {
    nbt.putByte(Settings.namespace + "tier", tier.toByte)
    super.writeToNBTForServer(nbt)
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
        // TODO(server.machine): 机器层移植前 `machine` 可能为 null，这里做空值保护。
        if (machine != null) machine.stop()
      }
    }
  }

  override def getSlots: Int = if (tier < 0 || tier >= InventorySlots.computer.length) 0 else InventorySlots.computer(tier).length

  override def isUseableByPlayer(player: Player): Boolean =
    super.isUseableByPlayer(player) && (!isCreative || player.isCreative)

  override def isItemValid(slot: Int, stack: ItemStack): Boolean =
    Option(Driver.driverFor(stack, getClass)).fold(false)(driver => {
      val provided = InventorySlots.computer(tier)(slot)
      driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
    })
}
