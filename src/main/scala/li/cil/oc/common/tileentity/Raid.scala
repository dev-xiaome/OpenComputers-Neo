package li.cil.oc.common.tileentity

import java.util.UUID

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.{Analyzable, Component, ManagedEnvironment, Node, Visibility}
import li.cil.oc.common.Slot
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

/**
 * RAID（原 1.7.10 `common.tileentity.Raid`）：把三个硬盘聚合成一个文件系统组件。
 *
 * 纹理：下/上 = RaidTop，北 = RaidFront，其它 = RaidSide。
 *
 * 1.21.1 迁移要点：
 *  - 构造函数改为 `(pos, state)`，方块实体类型由方块反查（见 [[BlockEntityBase.typeOf]]）。
 *  - `getSizeInventory` → `getSlots`、`getInventoryStackLimit` → `getSlotLimit`、
 *    `isItemValidForSlot` → `isItemValid`。
 *  - `nbt.hasKey` → `contains`；`NBT.TAG_COMPOUND` → [[net.minecraft.nbt.Tag.TAG_COMPOUND]]。
 *  - `nbt.setTag("presence", items.map(_.isDefined))` 在 1.7.10 写的是字节列表、读的却是
 *    `getByteArray`（不一致）；1.21.1 统一改用 [[li.cil.oc.util.ExtendedNBT]] 提供的
 *    `setBooleanArray` / `getBooleanArray`。
 *  - 删除 `@SideOnly`（NeoForge 会因此抛异常）。
 *
 * 降级清单（详见各处的 TODO）：
 *  - `server.component.FileSystem` 未移植 → 改用 [[li.cil.oc.api.fs.FileSystem]] +
 *    [[li.cil.oc.api.network.ManagedEnvironment]] 两个字段承载原「组件 + 底层存储」的语义，
 *    见 [[fileSystem]] / [[fileSystemEnvironment]]。
 *  - `ServerPacketSender.sendRaidChange(this)` → [[markBlockForUpdate]] + TODO。
 */
class Raid(pos: BlockPos, state: BlockState)
  extends BlockEntityBase(BlockEntityBase.typeOf(state.getBlock), pos, state)
    with traits.Environment with traits.Inventory with traits.Rotatable with Analyzable {

  val node: Node = api.Network.newNode(this, Visibility.None).create()

  /**
   * 底层文件系统（原 `server.component.FileSystem#fileSystem`）。
   *
   * TODO(server.component.FileSystem): 服务器组件层未移植。原实现持有
   * `Option[server.component.FileSystem]`，它既是 `ManagedEnvironment`（提供 `node` / `load` /
   * `save`），又暴露内部存储 `fileSystem`（用于 `close` / `list` / `delete` / `spaceTotal`）。
   * 这里拆成 [[fileSystem]] 与 [[fileSystemEnvironment]] 两部分，
   * 组件层移植后合并回一个 `server.component.FileSystem`（调用点语义不变）。
   */
  var fileSystem: Option[api.fs.FileSystem] = None

  /** 由 `api.FileSystem.asManagedEnvironment` 创建的托管环境（原组件本身）。 */
  private var fileSystemEnvironment: Option[ManagedEnvironment] = None

  val label = new RaidLabel()

  // Used on client side to check whether to render disk activity indicators.
  var lastAccess = 0L

  // For client side rendering.
  val presence = Array.fill(getSlots)(false)

  // ----------------------------------------------------------------------- //

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] =
    Array(fileSystemEnvironment.flatMap(env => Option(env.node)).orNull)

  override def canUpdate: Boolean = false

  // ----------------------------------------------------------------------- //

  override def getSlots: Int = 3

  override def getSlotLimit(slot: Int): Int = 1

  override def isItemValid(slot: Int, stack: ItemStack): Boolean = Option(Driver.driverFor(stack, getClass)) match {
    case Some(driver) => driver.slot(stack) == Slot.HDD
    case _ => false
  }

  override def onItemAdded(slot: Int, stack: ItemStack): Unit = {
    super.onItemAdded(slot, stack)
    if (isServer) this.synchronized {
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendRaidChange(this)
      // （只同步 presence / 磁盘变化）。
      markBlockForUpdate()
      tryCreateRaid(UUID.randomUUID().toString)
    }
  }

  override def markDirty(): Unit = {
    super.markDirty()
    // Makes the implementation of the comparator output easier.
    items.map(_.isDefined).copyToArray(presence)
  }

  override def onItemRemoved(slot: Int, stack: ItemStack): Unit = {
    super.onItemRemoved(slot, stack)
    if (isServer) this.synchronized {
      // TODO(server.PacketSender): 原为 ServerPacketSender.sendRaidChange(this)。
      markBlockForUpdate()
      fileSystem.foreach(fs => {
        fs.close()
        val entries = fs.list("/")
        if (entries != null) entries.foreach(fs.delete)
        fs.save(new CompoundTag()) // Flush buffered fs.
      })
      fileSystemEnvironment.flatMap(env => Option(env.node)).foreach(_.remove())
      fileSystem = None
      fileSystemEnvironment = None
    }
  }

  def tryCreateRaid(id: String): Unit = {
    if (items.count(_.isDefined) == items.length &&
      fileSystemEnvironment.fold(true)(env => env.node == null || env.node.address != id)) {
      fileSystemEnvironment.flatMap(env => Option(env.node)).foreach(_.remove())
      items.foreach(fs => fs match {
        case Some(fsStack) =>
          val drive = new DriveData(fsStack)
          drive.lockInfo = ""
          drive.isUnmanaged = false
          drive.save(fsStack)
        case _ => // should not happen but is safe to ignore
      })
      val rawFileSystem = api.FileSystem.fromSaveDirectory(id, wipeDisksAndComputeSpace, Settings.get.bufferChanges)
      val environment = api.FileSystem.asManagedEnvironment(
        rawFileSystem,
        label, this, Settings.resourceDomain + ":hdd_access", 6)
      val nbtToSetAddress = new CompoundTag()
      nbtToSetAddress.putString("address", id)
      if (environment != null && environment.node != null) {
        val environmentNode = environment.node
        environmentNode.load(nbtToSetAddress)
        environmentNode match {
          case component: Component => component.setVisibility(Visibility.Network)
          case _ =>
        }
        // Ensure we're in a network before connecting the raid fs.
        api.Network.joinNewNetwork(node)
        node.connect(environmentNode)
      }
      fileSystem = Option(rawFileSystem)
      fileSystemEnvironment = Option(environment)
    }
  }

  /**
   * 计算 RAID 的总容量，并（在原实现里）清空参与组建的各硬盘。
   *
   * TODO(server.component.FileSystem): 原实现通过 `driver.createEnvironment(hdd, this)` 取得
   * 硬盘的 `server.component.FileSystem`，再访问其内部 `fileSystem` 以
   * close / list / delete / 读取 `spaceTotal`。服务器组件层未移植，这里退化为按
   * [[li.cil.oc.Settings.hddSizes]] 的规格容量求和，并且**不清空**磁盘内容；
   * 组件层移植后请恢复「清盘 + 实际容量」的语义。
   */
  private def wipeDisksAndComputeSpace: Long = items.foldLeft(0L) {
    case (acc, Some(hdd)) if hdd != null && !hdd.isEmpty => acc + (Option(api.Driver.driverFor(hdd)) match {
      case Some(driver) =>
        val tier = driver.tier(hdd)
        val sizes = Settings.get.hddSizes
        if (tier >= 0 && tier < sizes.length) sizes(tier).toLong else 0L
      case _ => 0L
    })
    case (acc, _) => acc
  }

  // ----------------------------------------------------------------------- //

  override def readFromNBTForServer(nbt: CompoundTag): Unit = {
    super.readFromNBTForServer(nbt)
    if (nbt.contains(Settings.namespace + "fs")) {
      val tag = nbt.getCompound(Settings.namespace + "fs")
      tryCreateRaid(tag.getCompound("node").getString("address"))
      fileSystemEnvironment.foreach(_.load(tag))
    }
    label.load(nbt)
  }

  override def writeToNBTForServer(nbt: CompoundTag): Unit = {
    super.writeToNBTForServer(nbt)
    fileSystemEnvironment.foreach(env => nbt.setNewCompoundTag(Settings.namespace + "fs", env.save))
    label.save(nbt)
  }

  // 原 `@SideOnly(Side.CLIENT)`；1.21.1 删除注解。
  override def readFromNBTForClient(nbt: CompoundTag): Unit = {
    super.readFromNBTForClient(nbt)
    nbt.getBooleanArray("presence").
      copyToArray(presence)
    label.setLabel(nbt.getString("label"))
  }

  override def writeToNBTForClient(nbt: CompoundTag): Unit = {
    super.writeToNBTForClient(nbt)
    nbt.setBooleanArray("presence", presence)
    if (label.getLabel != null)
      nbt.putString("label", label.getLabel)
  }

  // ----------------------------------------------------------------------- //

  class RaidLabel extends Label {
    var label = "raid"

    override def getLabel: String = label

    override def setLabel(value: String): Unit = label = Option(value).map(_.take(16)).orNull

    override def load(nbt: CompoundTag): Unit = {
      if (nbt.contains(Settings.namespace + "label")) {
        label = nbt.getString(Settings.namespace + "label")
      }
    }

    override def save(nbt: CompoundTag): Unit = {
      nbt.putString(Settings.namespace + "label", label)
    }
  }
}
