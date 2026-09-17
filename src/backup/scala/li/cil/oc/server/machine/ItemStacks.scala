package li.cil.oc.server.machine

import com.google.common.base.Charsets
import li.cil.oc.util.ItemNBT
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.{CompoundTag, NbtAccounter, NbtIo}
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

import java.io.ByteArrayInputStream

/**
 * 物品栈的构造 / 解析辅助，语义完全对齐 OCCE（1.20）里散落在
 * `server/machine/ArgumentsImpl.scala` 与 `util/ItemUtils.scala` 的实现。
 *
 * 对应关系：
 *  - [[makeStack]] ← OCCE `ArgumentsImpl.makeStack`
 *  - [[readTagCompound]] ← OCCE `ItemUtils.loadTag`
 *
 * 1.21.1 适配点：
 *  - 物品注册表 `ForgeRegistries.ITEMS` → [[net.minecraft.core.registries.BuiltInRegistries]]。
 *  - `ItemStack#setDamageValue` 仍然存在，直接沿用（它内部会 clamp 到
 *    `[0, maxDamage]`，与 CE-1.20 完全一致；不要改成裸的数据组件写入，那样会丢掉 clamp）。
 *  - `ItemStack#setTag` 已删除，改用自定义数据组件助手 [[li.cil.oc.util.ItemNBT]]。
 *  - `NbtIo.readCompressed(InputStream)` 在 1.21.1 需要显式给出 `NbtAccounter`；
 *    这里用 `unlimitedHeap()`，与 1.20 的默认行为一致。
 */
private[machine] object ItemStacks {

  /** 把 Lua 表格里的物品描述（name / damage / tag）构造成物品栈。 */
  def makeStack(name: String, damage: Int, tag: Option[CompoundTag]): ItemStack = {
    val id = ResourceLocation.tryParse(name)
    if (id == null) throw new IllegalArgumentException("invalid item stack")
    val item = BuiltInRegistries.ITEM.get(id)
    // 1.21.1 的注册表查询查不到时返回默认值 `Items.AIR`（Forge 的 `getValue` 返回 null），
    // 因此这里的判定等价于 CE-1.20 的 `case _ => throw ...`。
    if (item == null || (item eq net.minecraft.world.item.Items.AIR)) {
      throw new IllegalArgumentException("invalid item stack")
    }
    val stack = new ItemStack(item)
    stack.setDamageValue(damage)
    // 不依赖包对象里的隐式类（在 `li.cil.oc` 之外的辅助对象里不总是可见），
    // 直接用 Java 侧的 ItemNBT 助手读写数据组件。
    tag.foreach(t => ItemNBT.set(stack, t))
    stack
  }

  /**
   * 把一段 NBT 二进制数据解析成 `CompoundTag`，失败时返回 `None`。
   *
   * 必须是**压缩**读取：写出方 `integration/vanilla/ConverterItemStack.scala` 用的是
   * `NbtIo.writeCompressed`（对应 OCCE 的 `ItemUtils.saveTag`），CE-1.20 在
   * `ItemUtils.loadTag` 里同样用 `NbtIo.readCompressed` 对称读回。此前这里误用了未压缩的
   * `NbtIo.read`，导致 Lua 与组件之间往返物品栈时 tag 全部解析失败。
   */
  def readTagCompound(data: Array[Byte]): Option[CompoundTag] =
    try {
      val stream = new ByteArrayInputStream(data)
      try Option(NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()))
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
   * （对应 CE-1.20 `ArgumentsImpl.checkItemStack` 里 `map.get("tag")` 的匹配）。
   */
  def tagOf(value: Any): Option[CompoundTag] = value match {
    case bytes: Array[Byte] => readTagCompound(bytes)
    case s: String => readTagCompound(s)
    case tag: CompoundTag => Some(tag)
    case _ => None
  }
}
