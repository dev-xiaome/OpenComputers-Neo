package li.cil.oc.common.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.IShapedRecipe;
import org.jetbrains.annotations.NotNull;

public class ExtendedShapedRecipe implements CraftingRecipe, IShapedRecipe<CraftingContainer> {
    private ShapedRecipe wrapped;

    public ExtendedShapedRecipe(ShapedRecipe wrapped) {
        this.wrapped = ExtendedRecipe.patchRecipe(wrapped);
    }

    @Override
    public boolean matches(@NotNull CraftingContainer inv, @NotNull Level level) {
        return wrapped.matches(inv, level);
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull CraftingContainer inv, @NotNull RegistryAccess registryAccess) {
        return ExtendedRecipe.addNBTToResult(this, wrapped.assemble(inv, registryAccess), inv);
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return wrapped.canCraftInDimensions(w, h);
    }

    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess registryAccess) {
        return wrapped.getResultItem(registryAccess);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        return wrapped.getRemainingItems(inv);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return wrapped.getIngredients();
    }

    @Override
    public ResourceLocation getId() {
        return wrapped.getId();
    }

    @Override
    @NotNull
    public RecipeSerializer<?> getSerializer() {
        return Recipes.SHAPED_EXTENDED.getSerializer();
    }

    @Override
    @NotNull
    public RecipeType<?> getType() {
        return RecipeType.CRAFTING;
    }

    @Override
    @NotNull
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public String getGroup() {
        return wrapped.getGroup();
    }

    @Override
    public int getRecipeWidth() {
        return wrapped.getRecipeWidth();
    }

    @Override
    public int getRecipeHeight() {
        return wrapped.getRecipeHeight();
    }

    public static final class Serializer implements RecipeSerializer<ExtendedShapedRecipe> {

        @Override
        public ExtendedShapedRecipe fromJson(ResourceLocation recipeId, JsonObject json) {
            ShapedRecipe wrapped = RecipeSerializer.SHAPED_RECIPE.fromJson(recipeId, json);
            return new ExtendedShapedRecipe(wrapped);
        }

        @Override
        public ExtendedShapedRecipe fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buff) {
            ShapedRecipe wrapped = RecipeSerializer.SHAPED_RECIPE.fromNetwork(recipeId, buff);
            return new ExtendedShapedRecipe(wrapped);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buff, ExtendedShapedRecipe recipe) {
            RecipeSerializer<ShapedRecipe> serializer =
                (RecipeSerializer<ShapedRecipe>) recipe.wrapped.getSerializer();
            serializer.toNetwork(buff, recipe.wrapped);
        }
    }
}
