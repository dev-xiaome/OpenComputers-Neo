package li.cil.oc.common.recipe

import java.util.function.Function

import com.mojang.serialization.MapCodec
import li.cil.oc.common.init.Registry
import li.cil.oc.util.ItemColorizer
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.crafting.{CraftingBookCategory, CraftingInput, CustomRecipe, Ingredient, RecipeSerializer}
import net.minecraft.world.item.{ItemStack, Items}
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredHolder

import scala.jdk.CollectionConverters._

/**
 * 「用水桶给物品褪色」的自定义合成配方（原 1.7.10 的 `DecolorizeRecipe`，作者 Vexatos）。
 *
 * 1.21.1 迁移要点同 [[ColorizeRecipe]]：`IRecipe` → `CustomRecipe`、
 * `getCraftingResult` → `assemble`，目标由构造参数改为配方字段 `Ingredient`。
 * 配方 JSON 形如：
 * {{{
 *   { "type": "opencomputers_neo:decolorizer", "target": { "item": "opencomputers_neo:cable" } }
 * }}}
 *
 * 注意：1.7.10 的实现同样不处理水桶的返还（`getRemainingItems` 未覆写），
 * 1.21.1 沿用该行为；若要返还空桶，需要覆写 `getRemainingItems`。
 */
class DecolorizeRecipe(val target: Ingredient) extends CustomRecipe(CraftingBookCategory.MISC) {
  override def matches(input: CraftingInput, level: Level): Boolean = {
    val stacks = DecolorizeRecipe.nonEmpty(input)
    val targets = stacks.filter(stack => target.test(stack))
    val other = stacks.filterNot(targets.contains)
    targets.size == 1 && other.size == 1 && other.forall(_.is(Items.WATER_BUCKET))
  }

  override def assemble(input: CraftingInput, registries: HolderLookup.Provider): ItemStack = {
    var targetStack: ItemStack = null

    for (stack <- DecolorizeRecipe.nonEmpty(input)) {
      if (target.test(stack)) {
        targetStack = stack.copy()
        targetStack.setCount(1)
      } else if (!stack.is(Items.WATER_BUCKET)) {
        return ItemStack.EMPTY
      }
    }

    if (targetStack == null) {
      return ItemStack.EMPTY
    }

    ItemColorizer.removeColor(targetStack)
    targetStack
  }

  override def canCraftInDimensions(width: Int, height: Int): Boolean = width * height >= 2

  override def getSerializer: RecipeSerializer[_] = DecolorizeRecipe.SERIALIZER.get()
}

object DecolorizeRecipe {
  /** 配方序列化器（JSON 的 `type` 字段指向它；配方类型仍是 `RecipeType.CRAFTING`）。 */
  val SERIALIZER: DeferredHolder[RecipeSerializer[_], RecipeSerializer[_]] =
    Registry.registerRecipeSerializer[DecolorizeRecipe]("decolorizer", () => new Serializer)

  private def nonEmpty(input: CraftingInput): Seq[ItemStack] =
    input.items().asScala.filter(stack => stack != null && !stack.isEmpty).toSeq

  class Serializer extends RecipeSerializer[DecolorizeRecipe] {
    override def codec(): MapCodec[DecolorizeRecipe] =
      Ingredient.CODEC_NONEMPTY.fieldOf("target").xmap(
        new Function[Ingredient, DecolorizeRecipe] {
          override def apply(target: Ingredient): DecolorizeRecipe = new DecolorizeRecipe(target)
        },
        new Function[DecolorizeRecipe, Ingredient] {
          override def apply(recipe: DecolorizeRecipe): Ingredient = recipe.target
        })

    override def streamCodec(): StreamCodec[RegistryFriendlyByteBuf, DecolorizeRecipe] =
      Ingredient.CONTENTS_STREAM_CODEC.map(
        new Function[Ingredient, DecolorizeRecipe] {
          override def apply(target: Ingredient): DecolorizeRecipe = new DecolorizeRecipe(target)
        },
        new Function[DecolorizeRecipe, Ingredient] {
          override def apply(recipe: DecolorizeRecipe): Ingredient = recipe.target
        })
  }

}
