package li.cil.oc.common.recipe

import net.minecraft.world.item.ItemStack

/**
 * 原 1.7.10 的「无序配方 + 合成结果 NBT 后处理」包装类。
 *
 * 1.21.1 迁移说明：它继承的 `net.minecraftforge.oredict.ShapelessOreRecipe` 已经不存在，
 * 而 1.21.1 里「无序配方 + tag/矿辞原料」由原版的数据驱动配方
 * `minecraft:crafting_shapeless` 直接覆盖，**不需要**再定义一个自定义配方类型。
 *
 * 注意：OC 里「复制软盘 / 复制 EEPROM / 更换设备里的 EEPROM」这些操作依赖
 * 「无序 + 恰好两个非空输入」这一判定，[[ExtendedRecipe.addNBTToResult]] 用
 * 显式的 `shapeless` 标志 + `CraftingInput#ingredientCount` 复刻了该判定。
 *
 * TODO(recipe): `.recipes` → JSON 转换完成后，本类可以直接删除。
 */
@deprecated("1.21.1 使用原版 data-driven 的 crafting_shapeless 配方；动态结果数据见 ExtendedRecipe", "port")
class ExtendedShapelessOreRecipe(val result: ItemStack, val ingredients: Seq[AnyRef]) {
  /** 原 `getCraftingResult(inventory)`：现请调用 [[ExtendedRecipe.addNBTToResult]]。 */
  def getCraftingResult(): ItemStack = result
}
