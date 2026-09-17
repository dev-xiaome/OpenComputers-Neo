package net.minecraft.data.recipes;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Recipe;

public class SpecialRecipeBuilder {
    private final Function<CraftingBookCategory, Recipe<?>> factory;

    public SpecialRecipeBuilder(Function<CraftingBookCategory, Recipe<?>> factory) {
        this.factory = factory;
    }

    public static SpecialRecipeBuilder special(Function<CraftingBookCategory, Recipe<?>> factory) {
        return new SpecialRecipeBuilder(factory);
    }

    public void save(RecipeOutput recipeOutput, String recipeId) {
        this.save(recipeOutput, ResourceLocation.parse(recipeId));
    }

    public void save(RecipeOutput recipeOutput, ResourceLocation recipeId) {
        recipeOutput.accept(recipeId, this.factory.apply(CraftingBookCategory.MISC), null);
    }
}
