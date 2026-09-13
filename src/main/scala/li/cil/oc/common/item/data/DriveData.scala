package li.cil.oc.common.item.data

import li.cil.oc.Settings
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * 磁盘（软盘 / 硬盘）数据（原 1.7.10 的 `DriveData`）。
 *
 * 1.21.1 迁移要点：
 *  - `getBoolean` 保持不变，`hasKey` → `contains`
 *  - `player.getDisplayName` → `player.getName.getString`
 *  - 原 `fs.FileSystem.removeAddress(stack)` 内部依赖 `integration.opencomputers.Item.dataTag`，
 *    这里按等价语义就地实现（清掉 `oc:data.node.address`），
 *    避免 `common/item/data` 反向依赖尚未移植的 `server/fs` 与集成层。
 */
class DriveData extends ItemData(null) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var isUnmanaged: Boolean = false
  var lockInfo: String = ""

  def isLocked: Boolean = lockInfo != null && lockInfo.nonEmpty

  private val UnmanagedKey = Settings.namespace + "unmanaged"
  private val LockKey = Settings.namespace + "lock"

  override def load(nbt: CompoundTag): Unit = {
    isUnmanaged = nbt.getBoolean(UnmanagedKey)
    lockInfo = if (nbt.contains(LockKey)) {
      nbt.getString(LockKey)
    } else ""
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.putBoolean(UnmanagedKey, isUnmanaged)
    nbt.putString(LockKey, lockInfo)
  }
}

object DriveData {

  def lock(stack: ItemStack, player: Player): Unit = {
    val key = player.getName.getString
    val data = new DriveData(stack)
    if (!data.isLocked) {
      data.lockInfo = if (key != null && key.nonEmpty) key else "notch" // notch == “未知”
      data.save(stack)
    }
  }

  def setUnmanaged(stack: ItemStack, unmanaged: Boolean): Unit = {
    val data = new DriveData(stack)
    if (data.isUnmanaged != unmanaged) {
      removeNodeAddress(stack)
      data.lockInfo = ""
    }
    data.isUnmanaged = unmanaged
    data.save(stack)
  }

  /**
   * 等价于原 `li.cil.oc.server.fs.FileSystem.removeAddress`：
   * 移除 `oc:data.node.address`，使磁盘重新获得新的文件系统地址。
   */
  private def removeNodeAddress(stack: ItemStack): Boolean = {
    if (stack == null || !stack.hasTag()) return false
    val tag = stack.getTag()
    if (!tag.contains(Settings.namespace + "data")) return false
    val data = tag.getCompound(Settings.namespace + "data")
    if (!data.contains("node")) return false
    val nodeData = data.getCompound("node")
    if (nodeData.contains("address")) {
      nodeData.remove("address")
      true
    } else false
  }
}
