package li.cil.oc.common.item.data

import net.minecraft.core.{HolderLookup, RegistryAccess}
import net.minecraft.nbt.{CompoundTag, ListTag}
import net.minecraft.world.item.ItemStack

import scala.collection.mutable

/**
 * `ItemStack` 的 NBT 序列化辅助（替代 1.7.10 的 `ItemStack.loadItemStackFromNBT` /
 * `stack.writeToNBT`）。
 *
 * 1.21.1 起物品栈的序列化依赖数据组件，因此必须有 [[HolderLookup.Provider]]。
 * 物品数据（组件数据、机器人组件列表等）在「无世界上下文」的场景（例如物品提示、
 * 合成表计算）也需要读写，此时退化为 [[RegistryAccess.EMPTY]]；
 * 这样得到的结果会丢失需要动态注册表的组件，但常见的 OC 组件都能往返。
 *
 * TODO(服务器): 若后续发现需要完整往返（例如带自定义附魔的组件），
 * 应改为从 `ServerLifecycleHooks.getCurrentServer().registryAccess()` 取 provider。
 */
object StackSerializer {

  /** 无上下文时使用的注册表访问器。 */
  def fallback: HolderLookup.Provider = RegistryAccess.EMPTY

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
    stack.save(provider, tag)
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
