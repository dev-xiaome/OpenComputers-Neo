package li.cil.oc.util

import li.cil.oc.common.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 为 `ItemStack` 补回 1.7.10 / Forge 1.20 时代的 NBT 访问接口。
 *
 * OCCE(1.20.1 Forge) 的代码里到处在写 `stack.hasTag()` / `stack.getTag()` /
 * `stack.getOrCreateTag()` / `stack.setTag(tag)`；NeoForge 1.21.1 把物品数据改成了
 * 数据组件，这些方法全部不存在了。为了**不改动 OCCE 的业务代码**，这里用隐式类把它们
 * 补回来，底层走自定义组件 [[li.cil.oc.common.DataComponents.NBT]]。
 *
 * 注意这里刻意不用原版的 `DataComponents.CUSTOM_DATA`：它取出来的是副本，写回必须整份
 * `set` 回去，而 OC 的组件数据模型（`api.driver.Item#dataTag`）依赖一棵可就地修改的 NBT 树，
 * 用副本会**静默丢写入**。自定义组件里放的是同一个 `CompoundTag` 实例。
 *
 * 另外 1.21.1 的空堆叠是 `ItemStack.EMPTY`（不是 1.7.10 的 `null`），这里统一按
 * `isEmpty` 判断，避免把 EMPTY 当成有效物品去挂数据。
 */
trait ExtendedItemStack {

  implicit class ItemStackNBT(private val stack: ItemStack) {
    /** 等价于 Forge 1.20 的 `ItemStack#getTag`；不存在时返回 `null`。 */
    def getTag(): CompoundTag =
      if (stack == null || stack.isEmpty) null else stack.get(DataComponents.NBT.get())

    /** 等价于 Forge 1.20 的 `ItemStack#hasTag`。 */
    def hasTag(): Boolean = getTag() != null

    /** 等价于 Forge 1.20 的 `ItemStack#setTag`。 */
    def setTag(tag: CompoundTag): Unit =
      if (stack != null && !stack.isEmpty) stack.set(DataComponents.NBT.get(), tag)

    /**
     * 等价于 Forge 1.20 的 `ItemStack#getOrCreateTag`。
     *
     * 返回的是**活的可变实例**：调用方拿到后直接 `put` 就会写进物品数据。
     */
    def getOrCreateTag(): CompoundTag = {
      var tag = getTag()
      if (tag == null) {
        tag = new CompoundTag()
        setTag(tag)
      }
      tag
    }

    /**
     * 等价于 Forge 1.20 的 `ItemStack#getOrCreateTagElement`。
     *
     * 取 `key` 下的子复合标签；不存在时新建一个并挂到根标签上，返回的同样是**活的可变实例**。
     */
    def getOrCreateTagElement(key: String): CompoundTag = {
      val tag = getOrCreateTag()
      if (tag.contains(key, CompoundTag.TAG_COMPOUND)) tag.getCompound(key)
      else {
        val child = new CompoundTag()
        tag.put(key, child)
        child
      }
    }

    /** 等价于 Forge 1.20 的 `ItemStack#removeTagKey`；根标签不存在时什么也不做。 */
    def removeTagKey(key: String): Unit = {
      val tag = getTag()
      if (tag != null) tag.remove(key)
    }
  }
}

/**
 * 显式引入入口。
 *
 * Scala 2.13 不会把包对象/trait 里的隐式类自动带到别的文件，`import ExtendedItemStack._`
 * 对普通 trait 也不成立（没有伴生对象），因此用一个继承它的 object 作为入口：
 * 需要的地方写 `import li.cil.oc.util.ItemStackNBTExtensions._` 即可。
 */
object ItemStackNBTExtensions extends ExtendedItemStack
