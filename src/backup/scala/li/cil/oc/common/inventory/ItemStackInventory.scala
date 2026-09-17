package li.cil.oc.common.inventory

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

// `getTag()` / `hasTag()` / `setTag()` 是补回 1.7.10 `ItemStack` NBT 访问的隐式扩展。
// Scala 2.13 不会把 `li.cil.oc` 包对象里的隐式类暴露给子包（见 `util.ExtendedItemStack`
// 的说明），因此这里必须显式引入。
import li.cil.oc.util.ItemStackNBTExtensions._

/**
 * 把物品栏的数据存放在「提供这个物品栏的物品堆叠」的 NBT 里
 * （对应 1.7.10 的 `common.inventory.ItemStackInventory`）。
 *
 * 1.21.1 迁移要点：`NBTTagCompound` → `CompoundTag`；`getTagCompound` / `hasTagCompound` /
 * `setTagCompound` 由 `li.cil.oc` 包对象里的 `ItemStackNBT` 隐式类提供
 * （为 `getTag()` / `hasTag()` / `setTag()`）。
 */
trait ItemStackInventory extends Inventory {
  // The item stack that provides the inventory.
  def container: ItemStack

  private lazy val inventory = Array.fill[Option[ItemStack]](getSlots)(None)

  override def items: Array[Option[ItemStack]] = inventory

  // Initialize the list automatically if we have a container.
  if (container != null) {
    reinitialize()
  }

  // Load items from tag.
  def reinitialize(): Unit = {
    for (i <- items.indices) {
      updateItems(i, null)
    }
    load(containerTag)
  }

  // Write items back to tag.
  override def markDirty(): Unit = save(containerTag)

  /**
   * 容器堆叠上的标签；没有就新建一个。
   *
   * 1.7.10 的 `setTagCompound` 对空堆叠也生效，1.21.1 的 `ItemStack#set` 对空堆叠是空操作，
   * 因此这里额外兜底：拿不到标签时返回一个临时标签，避免空堆叠导致 NPE。
   */
  private def containerTag: CompoundTag = {
    if (!container.hasTag()) {
      container.setTag(new CompoundTag())
    }
    val tag = container.getTag()
    if (tag != null) tag else new CompoundTag()
  }
}
