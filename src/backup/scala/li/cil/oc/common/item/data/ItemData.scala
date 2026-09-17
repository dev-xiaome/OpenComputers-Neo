package li.cil.oc.common.item.data

import li.cil.oc.api
import li.cil.oc.api.Persistable
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 物品数据基类（原 1.7.10 的 `ItemData`）。
 *
 * 1.21.1 迁移要点：
 *  - `stack.hasTagCompound` / `getTagCompound` / `setTagCompound` → 隐式扩展
 *    `hasTag()` / `getTag()` / `setTag()`（底层是自定义数据组件 `opencomputers_neo:nbt`）
 *  - `CompoundTag#copy()` 仍存在，但泛型返回 `CompoundTag`，不再需要 `asInstanceOf`
 *
 * @param itemName 对应的 [[li.cil.oc.Constants.ItemName]] / `BlockName` 常量；
 *                 为 `null` 表示该数据类不负责创建物品栈（例如 `DriveData`）。
 */
abstract class ItemData(val itemName: String) extends Persistable {

  /** 从物品栈读取数据；物品没有数据组件时不做任何事。 */
  def load(stack: ItemStack): Unit = {
    if (stack != null && stack.hasTag()) {
      // 原版 `ItemStack` 的读取不会复制 CompoundTag，而是直接持有，
      // 会导致非常难查的共享数据 bug，因此这里显式复制。
      load(stack.getTag().copy())
    }
  }

  /** 把数据写回物品栈；物品没有数据组件时先创建一个空的。 */
  def save(stack: ItemStack): Unit = {
    if (stack == null) return
    if (!stack.hasTag()) {
      stack.setTag(new CompoundTag())
    }
    save(stack.getTag())
  }

  /** 按 [[itemName]] 创建并填充一个物品栈；[[itemName]] 为 `null` 时返回 `null`。 */
  def createItemStack(): ItemStack = {
    if (itemName == null) null
    else {
      val stack = api.Items.get(itemName).createItemStack(1)
      save(stack)
      stack
    }
  }
}
