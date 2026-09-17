package net.minecraft.data.recipes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.ItemLike;

public class SimpleCookingRecipeBuilder implements RecipeBuilder {
    private final RecipeCategory category;
    private final CookingBookCategory bookCategory;
    private final Item result;
    private final ItemStack stackResult; // Neo: add stack result support
    private final Ingredient ingredient;
    private final float experience;
    private final int cookingTime;
    private final Map<String, Criterion<?>> criteria = new LinkedHashMap<>();
    @Nullable
    private String group;
    private final AbstractCookingRecipe.Factory<?> factory;

    private SimpleCookingRecipeBuilder(
        RecipeCategory category,
        CookingBookCategory bookCategory,
        ItemLike result,
        Ingredient ingredient,
        float experience,
        int cookingTime,
        AbstractCookingRecipe.Factory<?> factory
    ) {
        this(category, bookCategory, new ItemStack(result), ingredient, experience, cookingTime, factory);
    }

    private SimpleCookingRecipeBuilder(
            RecipeCategory p_251345_,
            CookingBookCategory p_251607_,
            ItemStack result,
            Ingredient p_250362_,
            float p_251204_,
            int p_250189_,
            AbstractCookingRecipe.Factory<?> p_311960_
    ) {
        this.category = p_251345_;
        this.bookCategory = p_251607_;
        this.result = result.getItem();
        this.stackResult = result;
        this.ingredient = p_250362_;
        this.experience = p_251204_;
        this.cookingTime = p_250189_;
        this.factory = p_311960_;
    }

    public static <T extends AbstractCookingRecipe> SimpleCookingRecipeBuilder generic(
        Ingredient ingredient,
        RecipeCategory category,
        ItemLike result,
        float experience,
        int cookingTime,
        RecipeSerializer<T> cookingSerializer,
        AbstractCookingRecipe.Factory<T> factory
    ) {
        return new SimpleCookingRecipeBuilder(category, determineRecipeCategory(cookingSerializer, result), result, ingredient, experience, cookingTime, factory);
    }

    public static SimpleCookingRecipeBuilder campfireCooking(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, CookingBookCategory.FOOD, result, ingredient, experience, cookingTime, CampfireCookingRecipe::new);
    }

    public static SimpleCookingRecipeBuilder blasting(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(
            category, determineBlastingRecipeCategory(result), result, ingredient, experience, cookingTime, BlastingRecipe::new
        );
    }

    public static SimpleCookingRecipeBuilder smelting(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(
            category, determineSmeltingRecipeCategory(result), result, ingredient, experience, cookingTime, SmeltingRecipe::new
        );
    }

    public static SimpleCookingRecipeBuilder smoking(Ingredient ingredient, RecipeCategory category, ItemLike result, float experience, int cookingTime) {
        return new SimpleCookingRecipeBuilder(category, CookingBookCategory.FOOD, result, ingredient, experience, cookingTime, SmokingRecipe::new);
    }

    public static <T extends AbstractCookingRecipe> SimpleCookingRecipeBuilder generic(
            Ingredient p_250999_,
            RecipeCategory p_248815_,
            ItemStack result,
            float p_251320_,
            int p_248693_,
            RecipeSerializer<T> p_250921_,
            AbstractCookingRecipe.Factory<T> p_312657_
    ) {
        return new SimpleCookingRecipeBuilder(p_248815_, determineSmeltingRecipeCategory(result.getItem()), result, p_250999_, p_251320_, p_248693_, p_312657_);
    }

    public static SimpleCookingRecipeBuilder campfireCooking(Ingredient p_249393_, RecipeCategory p_249372_, ItemStack result, float p_252321_, int p_251916_) {
        return new SimpleCookingRecipeBuilder(p_249372_, CookingBookCategory.FOOD, result, p_249393_, p_252321_, p_251916_, CampfireCookingRecipe::new);
    }

    public static SimpleCookingRecipeBuilder blasting(Ingredient p_252115_, RecipeCategory p_249421_, ItemStack result, float p_250383_, int p_250476_) {
        return new SimpleCookingRecipeBuilder(
                p_249421_, determineBlastingRecipeCategory(result.getItem()), result, p_252115_, p_250383_, p_250476_, BlastingRecipe::new
        );
    }

    public static SimpleCookingRecipeBuilder smelting(Ingredient p_249223_, RecipeCategory p_251240_, ItemStack result, float p_249452_, int p_250496_) {
        return new SimpleCookingRecipeBuilder(
                p_251240_, determineSmeltingRecipeCategory(result.getItem()), result, p_249223_, p_249452_, p_250496_, SmeltingRecipe::new
        );
    }

    public static SimpleCookingRecipeBuilder smoking(Ingredient p_248930_, RecipeCategory p_250319_, ItemStack result, float p_252329_, int p_250482_) {
        return new SimpleCookingRecipeBuilder(p_250319_, CookingBookCategory.FOOD, result, p_248930_, p_252329_, p_250482_, SmokingRecipe::new);
    }

    public SimpleCookingRecipeBuilder unlockedBy(String name, Criterion<?> criterion) {
        this.criteria.put(name, criterion);
        return this;
    }

    public SimpleCookingRecipeBuilder group(@Nullable String groupName) {
        this.group = groupName;
        return this;
    }

    @Override
    public Item getResult() {
        return this.result;
    }

    @Override
    public void save(RecipeOutput recipeOutput, ResourceLocation id) {
        this.ensureValid(id);
        Advancement.Builder advancement$builder = recipeOutput.advancement()
            .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
            .rewards(AdvancementRewards.Builder.recipe(id))
            .requirements(AdvancementRequirements.Strategy.OR);
        this.criteria.forEach(advancement$builder::addCriterion);
        AbstractCookingRecipe abstractcookingrecipe = this.factory
            .create(
                Objects.requireNonNullElse(this.group, ""), this.bookCategory, this.ingredient, this.stackResult, this.experience, this.cookingTime
            );
        recipeOutput.accept(id, abstractcookingrecipe, advancement$builder.build(id.withPrefix("recipes/" + this.category.getFolderName() + "/")));
    }

    private static CookingBookCategory determineSmeltingRecipeCategory(ItemLike result) {
        if (result.asItem().components().has(DataComponents.FOOD)) {
            return CookingBookCategory.FOOD;
        } else {
            return result.asItem() instanceof BlockItem ? CookingBookCategory.BLOCKS : CookingBookCategory.MISC;
        }
    }

    private static CookingBookCategory determineBlastingRecipeCategory(ItemLike result) {
        return result.asItem() instanceof BlockItem ? CookingBookCategory.BLOCKS : CookingBookCategory.MISC;
    }

    private static CookingBookCategory determineRecipeCategory(RecipeSerializer<? extends AbstractCookingRecipe> serializer, ItemLike result) {
        if (serializer == RecipeSerializer.SMELTING_RECIPE) {
            return determineSmeltingRecipeCategory(result);
        } else if (serializer == RecipeSerializer.BLASTING_RECIPE) {
            return determineBlastingRecipeCategory(result);
        } else if (serializer != RecipeSerializer.SMOKING_RECIPE && serializer != RecipeSerializer.CAMPFIRE_COOKING_RECIPE) {
            throw new IllegalStateException("Unknown cooking recipe type");
        } else {
            return CookingBookCategory.FOOD;
        }
    }

    /**
     * Makes sure that this obtainable.
     */
    private void ensureValid(ResourceLocation id) {
        if (this.criteria.isEmpty()) {
            throw new IllegalStateException("No way of obtaining recipe " + id);
        }
    }
}
