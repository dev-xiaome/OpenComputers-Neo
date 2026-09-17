package li.cil.oc.common.item.data

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.{CompoundTag, Tag}
import net.minecraft.world.item.ItemStack

/**
 * RAID 数据（原 1.7.10 的 `RaidData`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBT.TAG_COMPOUND` → [[Tag.TAG_COMPOUND]]
 *  - `ItemStack.loadItemStackFromNBT` → [[StackSerializer.loadItemStack]]
 *  - `nbt.setNewTagList(name, disks.toIterable)` 需要 `Iterable[Tag]`，
 *    因此显式 `map(StackSerializer.toTag)`
 */
class RaidData extends ItemData(Constants.BlockName.Raid) {
  def this(stack: ItemStack) = {
    this()
    load(stack)
  }

  var disks: Array[ItemStack] = Array.empty[ItemStack]

  var filesystem: CompoundTag = new CompoundTag()

  var label: Option[String] = None

  override def load(nbt: CompoundTag): Unit = {
    disks = StackSerializer.mapList(
      nbt.getList(Settings.namespace + "disks", Tag.TAG_COMPOUND),
      StackSerializer.loadItemStack).toArray
    filesystem = nbt.getCompound(Settings.namespace + "filesystem")
    if (nbt.contains(Settings.namespace + "label")) {
      label = Option(nbt.getString(Settings.namespace + "label"))
    }
  }

  override def save(nbt: CompoundTag): Unit = {
    nbt.setNewTagList(Settings.namespace + "disks",
      disks.filter(stack => stack != null && !stack.isEmpty).map(StackSerializer.toTag).toIterable)
    nbt.put(Settings.namespace + "filesystem", filesystem)
    label.foreach(nbt.putString(Settings.namespace + "label", _))
  }
}
