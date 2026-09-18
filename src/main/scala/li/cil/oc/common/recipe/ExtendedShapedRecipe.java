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

/// 1.21.1 适配要点：
/// - 合成输入由 `CraftingContainer` 换成 `CraftingInput`（容器接口是 `size()`，不再是 `getContainerSize()`）；
/// - 反序列化上下文由 `RegistryAccess` 换成 `HolderLookup.Provider`；
/// - `RecipeSerializer` 变成 codec + streamCodec（`fromJson`/`fromNetwork`/`toNetwork` 已移除）；
/// - NeoForge 的 `IShapedRecipe` 接口被移除，宽高直接由 `ShapedRecipe#getWidth/getHeight` 提供；
/// - `Recipe#getId` 被移除（配方 id 由数据包与注册表决定）。
public class ExtendedShapedRecipe implements CraftingRecipe {
    private final ShapedRecipe wrapped;

    public ExtendedShapedRecipe(ShapedRecipe wrapped) {
        this.wrapped = ExtendedRecipe.patchRecipe(wrapped);
    }

    @Override
    public boolean matches(@NotNull CraftingInput inv, @NotNull Level level) {
        return wrapped.matches(inv, level);
    }

    @Override
    @NotNull
    public ItemStack assemble(@NotNull CraftingInput inv, @NotNull HolderLookup.Provider registries) {
        return ExtendedRecipe.addNBTToResult(this, wrapped.assemble(inv, registries), inv);
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return wrapped.canCraftInDimensions(w, h);
    }

    @Override
    public ItemStack getResultItem(@NotNull HolderLookup.Provider registries) {
        return wrapped.getResultItem(registries);
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

    public int getRecipeWidth() {
        return wrapped.getWidth();
    }

    public int getRecipeHeight() {
        return wrapped.getHeight();
    }

    public static final class Serializer implements RecipeSerializer<ExtendedShapedRecipe> {
        public static final MapCodec<ExtendedShapedRecipe> CODEC =
                RecipeSerializer.SHAPED_RECIPE.codec().xmap(ExtendedShapedRecipe::new, recipe -> recipe.wrapped);

        public static final StreamCodec<RegistryFriendlyByteBuf, ExtendedShapedRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPED_RECIPE.streamCodec().map(ExtendedShapedRecipe::new, recipe -> recipe.wrapped);

        @Override
        public MapCodec<ExtendedShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ExtendedShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
