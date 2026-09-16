package li.cil.oc.integration.vanilla

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtIo
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.{Enchantment, EnchantmentHelper}

import java.io.ByteArrayOutputStream
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * `ItemStack` → Lua 表的转换器。
 *
 * 1.21.1 迁移要点：
 *  - `Item.getIdFromItem` → `BuiltInRegistries.ITEM.getId`
 *  - `OreDictionary.getOreIDs/getOreName` → 1.21.1 用物品 tag 取代矿辞，
 *    这里用 `ItemStack#getTags` 的 tag 全名填充 `oreNames`（键名保持不变，避免破坏 Lua 侧 API）
 *  - `stack.getItemDamage` → `stack.getDamageValue`；`stack.stackSize` → `stack.getCount`
 *  - `Item.itemRegistry.getNameForObject` → `BuiltInRegistries.ITEM.getKey`
 *  - `stack.getDisplayName` → `stack.getHoverName.getString`
 *  - `display.Lore`（NBT）→ `DataComponents.LORE` 组件
 *  - `EnchantmentHelper.getEnchantments` + `Enchantment.enchantmentsList`（数字 id）
 *    → `EnchantmentHelper.getEnchantmentsForCrafting` 返回的 `ItemEnchantments`
 *  - `CompressedStreamTools.compress(nbt)` → `NbtIo.writeCompressed(nbt, stream)`
 */
object ConverterItemStack extends api.driver.Converter {
  override def convert(value: AnyRef, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: ItemStack if stack != null && !stack.isEmpty =>
      if (Settings.get.insertIdsInConverters) {
        output.put("id", Int.box(BuiltInRegistries.ITEM.getId(stack.getItem)))
        // TODO(port): 1.21.1 没有 OreDictionary，最接近的等价物是物品 tag；
        // 这里仍然输出到 `oreNames` 键下以保持 Lua API 兼容。
        output.put("oreNames", stack.getTags.iterator().asScala.map(tag => tag.location().toString).toArray)
      }
      output.put("damage", Int.box(stack.getDamageValue))
      output.put("maxDamage", Int.box(stack.getMaxDamage))
      output.put("size", Int.box(stack.getCount))
      output.put("maxSize", Int.box(stack.getMaxStackSize))
      output.put("hasTag", Boolean.box(stack.hasTag()))
      output.put("name", BuiltInRegistries.ITEM.getKey(stack.getItem).toString)
      output.put("label", stack.getHoverName.getString)

      val lore = stack.get(DataComponents.LORE)
      if (lore != null) {
        output.put("lore", lore.lines().asScala.map(_.getString).mkString("\n"))
      }

      val enchantments = mutable.ArrayBuffer.empty[AnyRef]
      val entries = EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().iterator()
      while (entries.hasNext) {
        val entry = entries.next()
        val enchantment = entry.getKey
        val level = entry.getIntValue
        val map = mutable.Map[String, Any](
          "name" -> BuiltInRegistries.ENCHANTMENT.getKey(enchantment.value()).toString,
          "label" -> Enchantment.getFullname(enchantment, level).getString,
          "level" -> level
        )
        if (Settings.get.insertIdsInConverters) {
          map += "id" -> BuiltInRegistries.ENCHANTMENT.getId(enchantment.value())
        }
        enchantments += map
      }
      if (enchantments.nonEmpty) {
        output.put("enchantments", enchantments.toArray)
      }

      val tag = stack.getTag()
      if (tag != null && Settings.get.allowItemStackNBTTags) {
        // TODO(port): 1.7.10 输出的是整份物品 NBT；1.21.1 只能拿到 OC 自定义的
        // `opencomputers_neo:nbt` 组件内容（其余数据已改为数据组件）。
        val buffer = new ByteArrayOutputStream()
        NbtIo.writeCompressed(tag, buffer)
        output.put("tag", buffer.toByteArray)
      }
    case _ => // 忽略其它类型与空堆叠。
  }
}
