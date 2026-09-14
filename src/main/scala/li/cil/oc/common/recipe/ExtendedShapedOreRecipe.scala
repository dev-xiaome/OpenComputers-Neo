package li.cil.oc.common.recipe

import net.minecraft.world.item.ItemStack

/**
 * 原 1.7.10 的「有序配方 + 合成结果 NBT 后处理」包装类。
 *
 * 1.21.1 迁移说明：它继承的 `net.minecraftforge.oredict.ShapedOreRecipe` 已经不存在，
 * 而 1.21.1 里「有序配方 + tag/矿辞原料」由原版的数据驱动配方
 * `minecraft:crafting_shaped` 直接覆盖（`Ingredient` 自带 tag 形式），
 * **不需要**再定义一个自定义配方类型。
 *
 * 因此本类保留类名与「后处理」这一职责说明，但**不再作为配方注册**：
 *  - 配方本体：请写 `data/opencomputers_neo/recipe/` 下的 json 配方（`type: minecraft:crafting_shaped`）；
 *  - 结果上的动态数据（软盘颜色拷贝、EEPROM 复制、导航升级的地图、打印机的信标/亮度等）：
 *    改由 [[ExtendedRecipe.addNBTToResult]] 提供，由 [[ColorizeRecipe]] 这类
 *    `CustomRecipe` 或 `ItemCraftedEvent` 处理器在产出结果时调用。
 *
 * TODO(recipe): `.recipes` → JSON 转换完成后，本类可以直接删除
 * （`common/recipe/Recipes.scala` 里已不再引用它）。
 */
@deprecated("1.21.1 使用原版 data-driven 的 crafting_shaped 配方；动态结果数据见 ExtendedRecipe", "port")
class ExtendedShapedOreRecipe(val result: ItemStack, val ingredients: Seq[AnyRef]) {
  /** 原 `getCraftingResult(inventory)`：现请调用 [[ExtendedRecipe.addNBTToResult]]。 */
  def getCraftingResult(): ItemStack = result
}
