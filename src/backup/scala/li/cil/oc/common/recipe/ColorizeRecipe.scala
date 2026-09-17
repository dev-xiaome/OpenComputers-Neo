package li.cil.oc.common.recipe

import java.util.function.Function

import com.mojang.serialization.MapCodec
import li.cil.oc.common.init.Registry
import li.cil.oc.util.{Color, ItemColorizer}
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.{CraftingBookCategory, CraftingInput, CustomRecipe, Ingredient, RecipeSerializer}
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredHolder

import scala.jdk.CollectionConverters._

/**
 * 「给物品染色」的自定义合成配方（原 1.7.10 的 `ColorizeRecipe`，作者 asie / Vexatos）。
 *
 * 1.21.1 迁移要点：
 *  - `IRecipe` → [[net.minecraft.world.item.crafting.CustomRecipe]]
 *    （它实现了 `CraftingRecipe`，于是 `getType()` 默认返回 `RecipeType.CRAFTING`——
 *    **这是必须的**：1.21.1 的合成台只按 `RecipeType.CRAFTING` 查表，
 *    自定义配方类型在合成台里永远不会被匹配到）；
 *  - `getCraftingResult(InventoryCrafting)` → `assemble(CraftingInput, HolderLookup.Provider)`；
 *  - `getRecipeOutput` → `getResultItem`（`CustomRecipe` 已固定返回 `ItemStack.EMPTY`，
 *    因为结果颜色是运行期算出来的），并置 `isSpecial = true` 以免进配方书；
 *  - 构造函数由「目标 `Item`/`Block`（可带一组 source 物品）」改为**配方字段 `Ingredient`**：
 *    1.21.1 的配方是数据驱动 JSON，目标物品必须写在 JSON 里（`target` 字段）。
 *    1.7.10 的 `source` 数组默认就是目标自身，OC 自己的两处调用点（悬停靴、线缆）
 *    都只用默认值，因此这里不再保留 `source`；
 *  - 染料 RGB：1.7.10 用原版 `EntitySheep.fleeceColorTable`，1.21.1 该表已移除，
 *    改用 OC 自己的 16 色板 [[li.cil.oc.util.Color]]（同样是「染料 → RGB」，
 *    且正是 OC 渲染线缆/物品时使用的颜色）；
 *  - 配方 JSON 形如：
 *    {{{
 *      { "type": "opencomputers_neo:colorizer", "target": { "item": "opencomputers_neo:hoverboots" } }
 *    }}}
 *    （`.recipes` → JSON 的批量转换不在本次范围内。）
 */
class ColorizeRecipe(val target: Ingredient) extends CustomRecipe(CraftingBookCategory.MISC) {
  override def matches(input: CraftingInput, level: Level): Boolean = {
    val stacks = ColorizeRecipe.nonEmpty(input)
    val targets = stacks.filter(stack => target.test(stack))
    val other = stacks.filterNot(targets.contains)
    targets.size == 1 && other.nonEmpty && other.forall(Color.isDye)
  }

  override def assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack = {
    var targetStack: ItemStack = null
    val color = Array[Int](0, 0, 0)
    var colorCount = 0
    var maximum = 0

    for (stack <- ColorizeRecipe.nonEmpty(input)) {
      if (target.test(stack)) {
        targetStack = stack.copy()
        targetStack.setCount(1)
      } else {
        val dye = Color.findDye(stack)
        if (dye.isEmpty) {
          return ItemStack.EMPTY
        }

        val itemColor = Color.byOreName(dye.get)
        val red = (itemColor >> 16) & 255
        val green = (itemColor >> 8) & 255
        val blue = itemColor & 255
        maximum += math.max(red, math.max(green, blue))
        color(0) += red
        color(1) += green
        color(2) += blue
        colorCount += 1
      }
    }

    if (targetStack == null) {
      return ItemStack.EMPTY
    }

    // 已经是染过色的目标物品时，把它原有的颜色也平均进去（等价于原版盔甲染色公式）。
    if (ItemColorizer.hasColor(targetStack)) {
      val itemColor = ItemColorizer.getColor(targetStack)
      val red = (itemColor >> 16 & 255).toFloat / 255.0F
      val green = (itemColor >> 8 & 255).toFloat / 255.0F
      val blue = (itemColor & 255).toFloat / 255.0F
      maximum = (maximum.toFloat + math.max(red, math.max(green, blue)) * 255.0F).toInt
      color(0) = (color(0).toFloat + red * 255.0F).toInt
      color(1) = (color(1).toFloat + green * 255.0F).toInt
      color(2) = (color(2).toFloat + blue * 255.0F).toInt
      colorCount = colorCount + 1
    }

    var red = color(0) / colorCount
    var green = color(1) / colorCount
    var blue = color(2) / colorCount
    val max = maximum.toFloat / colorCount.toFloat
    val div = math.max(red, math.max(green, blue)).toFloat
    red = (red.toFloat * max / div).toInt
    green = (green.toFloat * max / div).toInt
    blue = (blue.toFloat * max / div).toInt
    ItemColorizer.setColor(targetStack, (red << 16) | (green << 8) | blue)
    targetStack
  }

  override def canCraftInDimensions(width: Int, height: Int): Boolean = width * height >= 2

  override def getSerializer: RecipeSerializer[_] = ColorizeRecipe.SERIALIZER.get()
}

object ColorizeRecipe {
  /** 配方序列化器（JSON 的 `type` 字段指向它；配方类型仍是 `RecipeType.CRAFTING`）。 */
  val SERIALIZER: DeferredHolder[RecipeSerializer[_], RecipeSerializer[_]] =
    Registry.registerRecipeSerializer[ColorizeRecipe]("colorizer", () => new Serializer)

  private def nonEmpty(input: CraftingInput): Seq[ItemStack] =
    input.items().asScala.filter(stack => stack != null && !stack.isEmpty).toSeq

  class Serializer extends RecipeSerializer[ColorizeRecipe] {
    override def codec(): MapCodec[ColorizeRecipe] =
      Ingredient.CODEC_NONEMPTY.fieldOf("target").xmap(
        new Function[Ingredient, ColorizeRecipe] {
          override def apply(target: Ingredient): ColorizeRecipe = new ColorizeRecipe(target)
        },
        new Function[ColorizeRecipe, Ingredient] {
          override def apply(recipe: ColorizeRecipe): Ingredient = recipe.target
        })

    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, ColorizeRecipe] =
      Ingredient.CONTENTS_STREAM_CODEC.map(
        new Function[Ingredient, ColorizeRecipe] {
          override def apply(target: Ingredient): ColorizeRecipe = new ColorizeRecipe(target)
        },
        new Function[ColorizeRecipe, Ingredient] {
          override def apply(recipe: ColorizeRecipe): Ingredient = recipe.target
        })
  }

}
