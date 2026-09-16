package li.cil.oc.common.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public class ExtendedShapelessRecipe implements CraftingRecipe {
    private final ShapelessRecipe wrapped;

    public ExtendedShapelessRecipe(ShapelessRecipe wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean matches(@NonNull CraftingInput inv, @NonNull Level level) {
        return wrapped.matches(inv, level);
    }

    @Override
    public ItemStack assemble(@NonNull CraftingInput inv, @NotNull HolderLookup.Provider provider) {
        return ExtendedRecipe.addNBTToResult(this, wrapped.assemble(inv, provider), inv, provider);
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return wrapped.canCraftInDimensions(w, h);
    }

    @Override
    public ItemStack getResultItem(@NotNull HolderLookup.Provider registries) {
        ItemStack result = wrapped.getResultItem(registries);
        ExtendedRecipe.initializeStaticResultData(this, result);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput inv) {
        return wrapped.getRemainingItems(inv);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return wrapped.getIngredients();
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
        private static final MapCodec<ExtendedShapelessRecipe> CODEC =
                RecipeSerializer.SHAPELESS_RECIPE.codec().xmap(ExtendedShapelessRecipe::new, recipe -> recipe.wrapped);
        private static final StreamCodec<RegistryFriendlyByteBuf, ExtendedShapelessRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(ExtendedShapelessRecipe::new, recipe -> recipe.wrapped);

        @Override
        @NotNull
        public MapCodec<ExtendedShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ExtendedShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
