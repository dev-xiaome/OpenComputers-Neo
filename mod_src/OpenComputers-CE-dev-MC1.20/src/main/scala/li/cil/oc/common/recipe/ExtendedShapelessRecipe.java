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
import org.jetbrains.annotations.NotNull;

public class ExtendedShapelessRecipe implements CraftingRecipe {
    private ShapelessRecipe wrapped;

    public ExtendedShapelessRecipe(ShapelessRecipe wrapped) {
        this.wrapped = ExtendedRecipe.patchRecipe(wrapped);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return wrapped.matches(inv, level);
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, @NotNull RegistryAccess registryAccess) {
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
        return Recipes.SHAPELESS_EXTENDED.getSerializer();
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

    public static final class Serializer implements RecipeSerializer<ExtendedShapelessRecipe> {

        @Override
        @NotNull
        public ExtendedShapelessRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
            ShapelessRecipe wrapped = RecipeSerializer.SHAPELESS_RECIPE.fromJson(recipeId, json);
            return new ExtendedShapelessRecipe(wrapped);
        }

        @Override
        public ExtendedShapelessRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buff) {
            ShapelessRecipe wrapped = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(recipeId, buff);
            return new ExtendedShapelessRecipe(wrapped);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buff, ExtendedShapelessRecipe recipe) {
            RecipeSerializer<ShapelessRecipe> serializer = (RecipeSerializer<ShapelessRecipe>) recipe.wrapped.getSerializer();
            serializer.toNetwork(buff, recipe.wrapped);
        }
    }
}
