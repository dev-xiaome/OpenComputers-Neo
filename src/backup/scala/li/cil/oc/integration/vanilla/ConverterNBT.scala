package li.cil.oc.integration.vanilla

import li.cil.oc.api
import net.minecraft.nbt._

import java.util

/**
 * `CompoundTag` → Lua 表的转换器（`oc:flatten`）。
 *
 * 1.21.1 迁移要点：
 *  - `NBTTagCompound` → `CompoundTag`，`NBTBase` → `Tag`
 *  - 各 `func_1502xx_x` 取值方法改为 `getAsXxx`
 *  - `NBTTagCompound#func_150296_c`（键集合）→ `CompoundTag#getAllKeys`
 *  - `NBTTagList#tagCount` / `removeTag(i)` → `ListTag#size` / `remove(i)`
 */
object ConverterNBT extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case nbt: CompoundTag => output.put("oc:flatten", flatten(nbt))
    case _ => // 忽略其它类型。
  }

  private def flatten(nbt: Tag): AnyRef = nbt match {
    case tag: ByteTag => Byte.box(tag.getAsByte)
    case tag: ShortTag => Short.box(tag.getAsShort)
    case tag: IntTag => Int.box(tag.getAsInt)
    case tag: LongTag => Long.box(tag.getAsLong)
    case tag: FloatTag => Float.box(tag.getAsFloat)
    case tag: DoubleTag => Double.box(tag.getAsDouble)
    case tag: ByteArrayTag => tag.getAsByteArray
    case tag: StringTag => tag.getAsString
    case tag: ListTag =>
      val copy = tag.copy()
      (0 until copy.size).map(_ => flatten(copy.remove(0))).toArray
    case tag: CompoundTag =>
      tag.getAllKeys.toArray(new Array[String](0)).map(key => key -> flatten(tag.get(key))).toMap
    case tag: IntArrayTag => tag.getAsIntArray
    case _ => null
  }
}
