package li.cil.oc.common.item.data

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.{CompoundTag, ListTag}
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

/**
 * `ItemStack` 的 NBT 序列化辅助（替代 1.7.10 的 `ItemStack.loadItemStackFromNBT` /
 * `stack.writeToNBT`）。
 *
 * 1.21.1 起物品栈的序列化依赖数据组件，因此必须有 [[HolderLookup.Provider]]。
 * 物品数据（组件数据、机器人组件列表等）在「无世界上下文」的场景（例如物品提示、
 * 合成表计算）也需要读写，此时由 [[li.cil.oc.util.ExtendedNBT.fallbackRegistry]] 兜底：
 * 它会依次尝试「当前服务端注册表 → 上次成功的缓存 → 客户端当前世界」。
 *
 * **不要改回 `RegistryAccess.EMPTY`。** 1.21.1 的 `ItemStack#save` 内部要用
 * `registries.getOrThrow(Registries.ITEM)` 取物品 id，空访问器上取不到，
 * 结果每个物品都被**静默写成空标签 `{"item": {}}`**，读档时全部变成空气——
 * 「机箱里的物品全没了」就是这个原因造成的。
 */
object StackSerializer {

  /** 无上下文时使用的注册表访问器（见 [[li.cil.oc.util.ExtendedNBT.fallbackRegistry]]）。 */
  def fallback: HolderLookup.Provider = li.cil.oc.util.ExtendedNBT.fallbackRegistry

  /** 等价于原 `ItemStack.loadItemStackFromNBT(tag)`。 */
  def loadItemStack(tag: CompoundTag): ItemStack = loadItemStack(tag, fallback)

  /** 带注册表访问器的重载。 */
  def loadItemStack(tag: CompoundTag, provider: HolderLookup.Provider): ItemStack = {
    if (tag == null) return ItemStack.EMPTY
    val stack = ItemStack.parseOptional(provider, tag)
    if (stack == null) ItemStack.EMPTY else stack
  }

  /** 等价于原 `stack.writeToNBT(new NBTTagCompound())`。 */
  def toTag(stack: ItemStack): CompoundTag = toTag(stack, fallback)

  /** 带注册表访问器的重载。 */
  def toTag(stack: ItemStack, provider: HolderLookup.Provider): CompoundTag = {
    val tag = new CompoundTag()
    writeTo(stack, tag, provider)
    tag
  }

  /** 等价于原 `stack.writeToNBT(nbt)`（写入到已有 tag）。 */
  def writeTo(stack: ItemStack, tag: CompoundTag): Unit = writeTo(stack, tag, fallback)

  /** 带注册表访问器的重载。 */
  def writeTo(stack: ItemStack, tag: CompoundTag, provider: HolderLookup.Provider): Unit = {
    if (stack == null || stack.isEmpty) return
    // 1.21.1 的 `ItemStack#save` 是**返回**编码结果，不是就地把内容写进 `tag`；
    // 丢掉返回值就会写出空标签（物品读档即丢失）。因此必须 merge 返回值。
    tag.merge(stack.save(provider, new CompoundTag()).asInstanceOf[CompoundTag])
  }

  /**
   * 遍历 `ListTag` 并把每个元素映射成结果（替代 1.7.10 的
   * `nbtTagList.map(f)` / `toArray[T]` 用法，避免依赖
   * [[li.cil.oc.util.ExtendedNBT]] 的隐式转换在跨包场景下被 Java 方法遮蔽）。
   */
  def mapList[T](list: ListTag, f: CompoundTag => T): Seq[T] = {
    if (list == null) return Seq.empty
    val buffer = mutable.ArrayBuffer.empty[T]
    var i = 0
    while (i < list.size()) {
      list.get(i) match {
        case compound: CompoundTag => buffer += f(compound)
        case _ =>
      }
      i += 1
    }
    buffer.toIndexedSeq
  }
}
