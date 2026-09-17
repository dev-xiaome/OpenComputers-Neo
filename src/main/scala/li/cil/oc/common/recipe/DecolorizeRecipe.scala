package li.cil.oc.common.recipe

import li.cil.oc.util.ItemColorizer
import li.cil.oc.util.StackOption
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.ResourceLocation
// 1.21.1：`CraftingRecipe extends Recipe[CraftingInput]`，配方输入不再是 `CraftingContainer`。
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.{CraftingBookCategory, CustomRecipe}
import net.minecraft.world.level.{ItemLike, Level}

/**
  * @author Vexatos
  */
// 1.21.1：`CustomRecipe` 构造器不再接收配方 id，只剩 `CraftingBookCategory`。
// `id` 参数保留仅为兼容调用点。
class DecolorizeRecipe(id: ResourceLocation, target: ItemLike) extends CustomRecipe(CraftingBookCategory.MISC) {
  val targetItem: Item = target.asItem()

  override def matches(crafting: CraftingInput, level: Level): Boolean = {
    val stacks = (0 until crafting.size()).flatMap(i => StackOption(crafting.getItem(i)))
    val targets = stacks.filter(stack => stack.getItem == targetItem)
    val other = stacks.filterNot(targets.contains)
    targets.size == 1 && other.size == 1 && other.forall(_.getItem == Items.WATER_BUCKET)
  }

  override def assemble(crafting: CraftingInput, registries: HolderLookup.Provider): ItemStack = {
    var targetStack: ItemStack = ItemStack.EMPTY

    (0 until crafting.size()).flatMap(i => StackOption(crafting.getItem(i))).foreach { stack =>
      if (stack.getItem == targetItem) {
        targetStack = stack.copy()
        targetStack.setCount(1)
      } else if (stack.getItem != Items.WATER_BUCKET) {
        return ItemStack.EMPTY
      }
    }

    if (targetStack.isEmpty) return ItemStack.EMPTY

    ItemColorizer.removeColor(targetStack)
    targetStack
  }

  override def canCraftInDimensions(width: Int, height: Int): Boolean = width * height >= 2

  override def getSerializer = Recipes.DECOLORIZE.getSerializer
}
