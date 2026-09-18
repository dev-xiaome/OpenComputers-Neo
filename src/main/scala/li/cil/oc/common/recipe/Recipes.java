package li.cil.oc.common.recipe;

import java.util.function.Supplier;

import li.cil.oc.OpenComputers;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class Recipes {
    /// 1.21.1：`net.neoforged.neoforge.common.util.Lazy` 是类而不是函数式接口，
    /// 不能再接收 `recipeType::get` 这样的方法引用，改用 [Supplier]。
    public static final class RecipeRegistration<R extends Recipe<?>> {
        private final Supplier<RecipeType<R>> recipeType;
        private final Supplier<RecipeSerializer<R>> serializer;

        public RecipeRegistration(Supplier<RecipeType<R>> recipeType, Supplier<RecipeSerializer<R>> serializer) {
            this.recipeType = recipeType;
            this.serializer = serializer;
        }

        public RecipeType<R> getRecipeType() {
            return recipeType.get();
        }

        public RecipeSerializer<R> getSerializer() {
            return serializer.get();
        }
    }

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, OpenComputers.ID());
    public static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_TYPE, OpenComputers.ID());

    private static ResourceLocation id(final String name) {
        return ResourceLocation.fromNamespaceAndPath(OpenComputers.ID(), name);
    }

    public static final RecipeRegistration<LootDiskCyclingRecipe> LOOTDISK_CYCLING = register(
            "crafting_lootdisk_cycling",
            // 1.21.1：`SimpleCraftingRecipeSerializer.Factory` 只接收 `CraftingBookCategory`，
            // 配方 id 由这里补上。
            new SimpleCraftingRecipeSerializer<>(category -> new LootDiskCyclingRecipe(id("crafting_lootdisk_cycling"), category))
    );
    public static final RecipeRegistration<ColorizeRecipe> COLORIZE = register("crafting_colorize", new ItemSpecialSerializer<>(ColorizeRecipe::new, ColorizeRecipe::targetItem));
    public static final RecipeRegistration<DecolorizeRecipe> DECOLORIZE = register("crafting_decolorize", new ItemSpecialSerializer<>(DecolorizeRecipe::new, DecolorizeRecipe::targetItem));
    public static final RecipeRegistration<ExtendedShapedRecipe> SHAPED_EXTENDED = register("crafting_shaped_extended", new ExtendedShapedRecipe.Serializer());
    public static final RecipeRegistration<ExtendedShapelessRecipe> SHAPELESS_EXTENDED = register("crafting_shapeless_extended", new ExtendedShapelessRecipe.Serializer());

    private static <R extends Recipe<?>> RecipeRegistration<R> register(String id, RecipeSerializer<R> serializer) {
        DeferredHolder<RecipeType<?>, RecipeType<R>> recipeType = RECIPES.register(id, () -> new RecipeType<>() {
            @Override
            public String toString() { return id; }
        });
        DeferredHolder<RecipeSerializer<?>, RecipeSerializer<R>> recipeSerializer = SERIALIZERS.register(id, () -> serializer);
        return new RecipeRegistration<>(
                recipeType::get,
                recipeSerializer::get
        );
    }

    public static void init(IEventBus eventBus) {
        RECIPES.register(eventBus);
        SERIALIZERS.register(eventBus);
    }
}
