package li.cil.oc.server.machine

import com.google.common.base.Charsets
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.ItemNBT
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.{CompoundTag, NbtAccounter, NbtIo, Tag}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

import java.io.{ByteArrayInputStream, DataInputStream}

/**
 * 物品栈的构造/解析辅助。
 *
 * 原 1.7.10 的实现直接使用 `Item.itemRegistry.getObject(name)` 与
 * `ItemStack(item, 1, damage)` + `stack.setTagCompound(tag)`。
 * 1.21.1 中：
 *  - 物品注册表改为 [[net.minecraft.core.registries.BuiltInRegistries]]
 *  - `damage` 改为数据组件 [[net.minecraft.core.component.DataComponents.DAMAGE]]
 *  - NBT 反序列化需要 `HolderLookup.Provider`
 */
private[machine] object ItemStacks {

  /** 把 Lua 表格里的物品描述（name / damage / tag）构造成物品栈。 */
  def makeStack(name: String, damage: Int, tag: Option[CompoundTag]): ItemStack = {
    val id = ResourceLocation.tryParse(name)
    if (id == null) throw new IllegalArgumentException("invalid item stack")
    val item = BuiltInRegistries.ITEM.get(id)
    // TODO(1.21.1): 原版用 `Items.AIR` 表示“查不到”，这里等价于原来的 `case _ =>`。
    if (item == null || (item eq net.minecraft.world.item.Items.AIR)) {
      throw new IllegalArgumentException("invalid item stack")
    }
    val stack = new ItemStack(item)
    if (damage != 0) stack.set(DataComponents.DAMAGE, Integer.valueOf(damage))
    // 不依赖包对象里的隐式类（在 `li.cil.oc` 之外的辅助对象里不总是可见），
    // 直接用 Java 侧的 ItemNBT 助手读写数据组件。
    tag.foreach(t => ItemNBT.set(stack, t))
    stack
  }

  /** 把一段 NBT 二进制数据解析成 `CompoundTag`，失败时返回 `None`。 */
  def readTagCompound(data: Array[Byte]): Option[CompoundTag] =
    try {
      // TODO(1.21.1): 原 `NbtIo.func_152457_a`（读未压缩 NBT）；新 API 需要显式给
      // 出 NbtAccounter，这里沿用默认的“无限”累加器以保持旧行为。
      val stream = new DataInputStream(new ByteArrayInputStream(data))
      try Option(NbtIo.read(stream, NbtAccounter.unlimitedHeap()))
      finally stream.close()
    }
    catch {
      case _: Throwable => None
    }

  /** 字符串形式的 NBT（UTF-8 编码）→ `CompoundTag`。 */
  def readTagCompound(data: String): Option[CompoundTag] =
    readTagCompound(data.getBytes(Charsets.UTF_8))

  /**
   * 从物品栈描述表格中取出 NBT 字段。存在返回 `Some`，不存在返回 `None`
   * （对应原实现的 `case _ => None`）。
   */
  def tagOf(value: Any): Option[CompoundTag] = value match {
    case bytes: Array[Byte] => readTagCompound(bytes)
    case s: String => readTagCompound(s)
    case tag: CompoundTag => Some(tag)
    case _ => None
  }

  /** 判断一个 `Tag` 是否是本模块认识的“信号参数”类型。 */
  def isSignalArgTag(tag: Tag): Boolean = tag != null && tag.getId != Tag.TAG_END
}
