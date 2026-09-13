package li.cil.oc.common.recipe

import net.minecraft.inventory.InventoryCrafting
import net.minecraft.world.item.ItemStack
import net.minecraftforge.oredict.ShapedOreRecipe

class ExtendedShapedOreRecipe(result: ItemStack, ingredients: AnyRef*) extends ShapedOreRecipe(result, ingredients.toSeq: _*) {
  override def getCraftingResult(inventory: InventoryCrafting) =
    ExtendedRecipe.addNBTToResult(this, super.getCraftingResult(inventory), inventory)
}
