package net.minecraft.data.recipes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.EnterBlockTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.BlockFamilies;
import net.minecraft.data.BlockFamily;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public abstract class RecipeProvider implements DataProvider {
    protected final PackOutput.PathProvider recipePathProvider;
    protected final PackOutput.PathProvider advancementPathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;
    private static final Map<BlockFamily.Variant, BiFunction<ItemLike, ItemLike, RecipeBuilder>> SHAPE_BUILDERS = ImmutableMap.<BlockFamily.Variant, BiFunction<ItemLike, ItemLike, RecipeBuilder>>builder()
        .put(BlockFamily.Variant.BUTTON, (p_176733_, p_176734_) -> buttonBuilder(p_176733_, Ingredient.of(p_176734_)))
        .put(BlockFamily.Variant.CHISELED, (p_248037_, p_248038_) -> chiseledBuilder(RecipeCategory.BUILDING_BLOCKS, p_248037_, Ingredient.of(p_248038_)))
        .put(BlockFamily.Variant.CUT, (p_248026_, p_248027_) -> cutBuilder(RecipeCategory.BUILDING_BLOCKS, p_248026_, Ingredient.of(p_248027_)))
        .put(BlockFamily.Variant.DOOR, (p_176714_, p_176715_) -> doorBuilder(p_176714_, Ingredient.of(p_176715_)))
        .put(BlockFamily.Variant.CUSTOM_FENCE, (p_176708_, p_176709_) -> fenceBuilder(p_176708_, Ingredient.of(p_176709_)))
        .put(BlockFamily.Variant.FENCE, (p_248031_, p_248032_) -> fenceBuilder(p_248031_, Ingredient.of(p_248032_)))
        .put(BlockFamily.Variant.CUSTOM_FENCE_GATE, (p_176698_, p_176699_) -> fenceGateBuilder(p_176698_, Ingredient.of(p_176699_)))
        .put(BlockFamily.Variant.FENCE_GATE, (p_248035_, p_248036_) -> fenceGateBuilder(p_248035_, Ingredient.of(p_248036_)))
        .put(BlockFamily.Variant.SIGN, (p_176688_, p_176689_) -> signBuilder(p_176688_, Ingredient.of(p_176689_)))
        .put(BlockFamily.Variant.SLAB, (p_248017_, p_248018_) -> slabBuilder(RecipeCategory.BUILDING_BLOCKS, p_248017_, Ingredient.of(p_248018_)))
        .put(BlockFamily.Variant.STAIRS, (p_176674_, p_176675_) -> stairBuilder(p_176674_, Ingredient.of(p_176675_)))
        .put(BlockFamily.Variant.PRESSURE_PLATE, (p_248039_, p_248040_) -> pressurePlateBuilder(RecipeCategory.REDSTONE, p_248039_, Ingredient.of(p_248040_)))
        .put(BlockFamily.Variant.POLISHED, (p_248019_, p_248020_) -> polishedBuilder(RecipeCategory.BUILDING_BLOCKS, p_248019_, Ingredient.of(p_248020_)))
        .put(BlockFamily.Variant.TRAPDOOR, (p_176638_, p_176639_) -> trapdoorBuilder(p_176638_, Ingredient.of(p_176639_)))
        .put(BlockFamily.Variant.WALL, (p_248024_, p_248025_) -> wallBuilder(RecipeCategory.DECORATIONS, p_248024_, Ingredient.of(p_248025_)))
        .build();

    public RecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        this.recipePathProvider = output.createRegistryElementsPathProvider(Registries.RECIPE);
        this.advancementPathProvider = output.createRegistryElementsPathProvider(Registries.ADVANCEMENT);
        this.registries = registries;
    }

    @Override
    public final CompletableFuture<?> run(CachedOutput output) {
        return this.registries.thenCompose(p_323133_ -> this.run(output, p_323133_));
    }

    protected CompletableFuture<?> run(final CachedOutput output, final HolderLookup.Provider registries) {
        final Set<ResourceLocation> set = Sets.newHashSet();
        final List<CompletableFuture<?>> list = new ArrayList<>();
        this.buildRecipes(
            new RecipeOutput() {
                @Override
                public void accept(ResourceLocation p_312039_, Recipe<?> p_312254_, @Nullable AdvancementHolder p_311794_, net.neoforged.neoforge.common.conditions.ICondition... conditions) {
                    if (!set.add(p_312039_)) {
                        throw new IllegalStateException("Duplicate recipe " + p_312039_);
                    } else {
                        list.add(DataProvider.saveStable(output, registries, Recipe.CONDITIONAL_CODEC, Optional.of(new net.neoforged.neoforge.common.conditions.WithConditions<>(p_312254_, conditions)), RecipeProvider.this.recipePathProvider.json(p_312039_)));
                        if (p_311794_ != null) {
                            list.add(
                                DataProvider.saveStable(
                                    output,
                                    registries,
                                    Advancement.CONDITIONAL_CODEC,
                                    Optional.of(new net.neoforged.neoforge.common.conditions.WithConditions<>(p_311794_.value(), conditions)),
                                    RecipeProvider.this.advancementPathProvider.json(p_311794_.id())
                                )
                            );
                        }
                    }
                }

                @Override
                public Advancement.Builder advancement() {
                    return Advancement.Builder.recipeAdvancement().parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
                }
            }, registries
        );
        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
    }

    protected CompletableFuture<?> buildAdvancement(CachedOutput output, HolderLookup.Provider registries, AdvancementHolder advancement) {
        return buildAdvancement(output, registries, advancement, new net.neoforged.neoforge.common.conditions.ICondition[0]);
    }

    protected CompletableFuture<?> buildAdvancement(CachedOutput output, HolderLookup.Provider registries, AdvancementHolder advancement, net.neoforged.neoforge.common.conditions.ICondition... conditions) {
        return DataProvider.saveStable(output, registries, Advancement.CONDITIONAL_CODEC, Optional.of(new net.neoforged.neoforge.common.conditions.WithConditions<>(advancement.value(), conditions)), this.advancementPathProvider.json(advancement.id()));
    }

    protected void buildRecipes(RecipeOutput p_recipeOutput, HolderLookup.Provider holderLookup) {
        buildRecipes(p_recipeOutput);
    }

    protected void buildRecipes(RecipeOutput recipeOutput) {}

    protected void generateForEnabledBlockFamilies(RecipeOutput enabledFeatures, FeatureFlagSet p_251836_) {
        BlockFamilies.getAllFamilies().filter(BlockFamily::shouldGenerateRecipe).forEach(p_313461_ -> generateRecipes(enabledFeatures, p_313461_, p_251836_));
    }

    protected static void oneToOneConversionRecipe(RecipeOutput recipeOutput, ItemLike result, ItemLike ingredient, @Nullable String group) {
        oneToOneConversionRecipe(recipeOutput, result, ingredient, group, 1);
    }

    protected static void oneToOneConversionRecipe(RecipeOutput recipeOutput, ItemLike result, ItemLike ingredient, @Nullable String group, int resultCount) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, result, resultCount)
            .requires(ingredient)
            .group(group)
            .unlockedBy(getHasName(ingredient), has(ingredient))
            .save(recipeOutput, getConversionRecipeName(result, ingredient));
    }

    protected static void oreSmelting(
        RecipeOutput recipeOutput, List<ItemLike> ingredients, RecipeCategory category, ItemLike result, float experience, int cookingTime, String group
    ) {
        oreCooking(
            recipeOutput,
            RecipeSerializer.SMELTING_RECIPE,
            SmeltingRecipe::new,
            ingredients,
            category,
            result,
            experience,
            cookingTime,
            group,
            "_from_smelting"
        );
    }

    protected static void oreBlasting(
        RecipeOutput recipeOutput, List<ItemLike> ingredients, RecipeCategory category, ItemLike result, float experience, int cookingTime, String group
    ) {
        oreCooking(
            recipeOutput,
            RecipeSerializer.BLASTING_RECIPE,
            BlastingRecipe::new,
            ingredients,
            category,
            result,
            experience,
            cookingTime,
            group,
            "_from_blasting"
        );
    }

    protected static <T extends AbstractCookingRecipe> void oreCooking(
        RecipeOutput recipeOutput,
        RecipeSerializer<T> serializer,
        AbstractCookingRecipe.Factory<T> recipeFactory,
        List<ItemLike> ingredients,
        RecipeCategory category,
        ItemLike result,
        float experience,
        int cookingTime,
        String group,
        String suffix
    ) {
        for (ItemLike itemlike : ingredients) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemlike), category, result, experience, cookingTime, serializer, recipeFactory)
                .group(group)
                .unlockedBy(getHasName(itemlike), has(itemlike))
                .save(recipeOutput, getItemName(result) + suffix + "_" + getItemName(itemlike));
        }
    }

    protected static void netheriteSmithing(RecipeOutput recipeOutput, Item ingredientItem, RecipeCategory category, Item resultItem) {
        SmithingTransformRecipeBuilder.smithing(
                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), Ingredient.of(ingredientItem), Ingredient.of(Items.NETHERITE_INGOT), category, resultItem
            )
            .unlocks("has_netherite_ingot", has(Items.NETHERITE_INGOT))
            .save(recipeOutput, getItemName(resultItem) + "_smithing");
    }

    protected static void trimSmithing(RecipeOutput recipeOutput, Item ingredientItem, ResourceLocation location) {
        SmithingTrimRecipeBuilder.smithingTrim(
                Ingredient.of(ingredientItem), Ingredient.of(ItemTags.TRIMMABLE_ARMOR), Ingredient.of(ItemTags.TRIM_MATERIALS), RecipeCategory.MISC
            )
            .unlocks("has_smithing_trim_template", has(ingredientItem))
            .save(recipeOutput, location);
    }

    protected static void twoByTwoPacker(RecipeOutput recipeOutput, RecipeCategory category, ItemLike packed, ItemLike unpacked) {
        ShapedRecipeBuilder.shaped(category, packed, 1)
            .define('#', unpacked)
            .pattern("##")
            .pattern("##")
            .unlockedBy(getHasName(unpacked), has(unpacked))
            .save(recipeOutput);
    }

    protected static void threeByThreePacker(RecipeOutput recipeOutput, RecipeCategory category, ItemLike packed, ItemLike unpacked, String criterionName) {
        ShapelessRecipeBuilder.shapeless(category, packed).requires(unpacked, 9).unlockedBy(criterionName, has(unpacked)).save(recipeOutput);
    }

    protected static void threeByThreePacker(RecipeOutput recipeOutput, RecipeCategory category, ItemLike packed, ItemLike unpacked) {
        threeByThreePacker(recipeOutput, category, packed, unpacked, getHasName(unpacked));
    }

    protected static void planksFromLog(RecipeOutput recipeOutput, ItemLike planks, TagKey<Item> logs, int resultCount) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, planks, resultCount)
            .requires(logs)
            .group("planks")
            .unlockedBy("has_log", has(logs))
            .save(recipeOutput);
    }

    protected static void planksFromLogs(RecipeOutput recipeOutput, ItemLike planks, TagKey<Item> logs, int result) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, planks, result)
            .requires(logs)
            .group("planks")
            .unlockedBy("has_logs", has(logs))
            .save(recipeOutput);
    }

    protected static void woodFromLogs(RecipeOutput recipeOutput, ItemLike wood, ItemLike log) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, wood, 3)
            .define('#', log)
            .pattern("##")
            .pattern("##")
            .group("bark")
            .unlockedBy("has_log", has(log))
            .save(recipeOutput);
    }

    protected static void woodenBoat(RecipeOutput recipeOutput, ItemLike boat, ItemLike material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, boat)
            .define('#', material)
            .pattern("# #")
            .pattern("###")
            .group("boat")
            .unlockedBy("in_water", insideOf(Blocks.WATER))
            .save(recipeOutput);
    }

    protected static void chestBoat(RecipeOutput recipeOutput, ItemLike boat, ItemLike material) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TRANSPORTATION, boat)
            .requires(Blocks.CHEST)
            .requires(material)
            .group("chest_boat")
            .unlockedBy("has_boat", has(ItemTags.BOATS))
            .save(recipeOutput);
    }

    protected static RecipeBuilder buttonBuilder(ItemLike button, Ingredient material) {
        return ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, button).requires(material);
    }

    protected static RecipeBuilder doorBuilder(ItemLike door, Ingredient material) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, door, 3).define('#', material).pattern("##").pattern("##").pattern("##");
    }

    protected static RecipeBuilder fenceBuilder(ItemLike fence, Ingredient material) {
        int i = fence == Blocks.NETHER_BRICK_FENCE ? 6 : 3;
        Item item = fence == Blocks.NETHER_BRICK_FENCE ? Items.NETHER_BRICK : Items.STICK;
        return ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, fence, i).define('W', material).define('#', item).pattern("W#W").pattern("W#W");
    }

    protected static RecipeBuilder fenceGateBuilder(ItemLike fenceGate, Ingredient material) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, fenceGate).define('#', Items.STICK).define('W', material).pattern("#W#").pattern("#W#");
    }

    protected static void pressurePlate(RecipeOutput recipeOutput, ItemLike pressurePlate, ItemLike material) {
        pressurePlateBuilder(RecipeCategory.REDSTONE, pressurePlate, Ingredient.of(material)).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }

    protected static RecipeBuilder pressurePlateBuilder(RecipeCategory category, ItemLike pressurePlate, Ingredient material) {
        return ShapedRecipeBuilder.shaped(category, pressurePlate).define('#', material).pattern("##");
    }

    protected static void slab(RecipeOutput recipeOutput, RecipeCategory category, ItemLike slab, ItemLike material) {
        slabBuilder(category, slab, Ingredient.of(material)).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }

    protected static RecipeBuilder slabBuilder(RecipeCategory category, ItemLike slab, Ingredient material) {
        return ShapedRecipeBuilder.shaped(category, slab, 6).define('#', material).pattern("###");
    }

    protected static RecipeBuilder stairBuilder(ItemLike stairs, Ingredient material) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, stairs, 4).define('#', material).pattern("#  ").pattern("## ").pattern("###");
    }

    protected static RecipeBuilder trapdoorBuilder(ItemLike trapdoor, Ingredient material) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, trapdoor, 2).define('#', material).pattern("###").pattern("###");
    }

    protected static RecipeBuilder signBuilder(ItemLike sign, Ingredient material) {
        return ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, sign, 3)
            .group("sign")
            .define('#', material)
            .define('X', Items.STICK)
            .pattern("###")
            .pattern("###")
            .pattern(" X ");
    }

    protected static void hangingSign(RecipeOutput recipeOutput, ItemLike sign, ItemLike material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, sign, 6)
            .group("hanging_sign")
            .define('#', material)
            .define('X', Items.CHAIN)
            .pattern("X X")
            .pattern("###")
            .pattern("###")
            .unlockedBy("has_stripped_logs", has(material))
            .save(recipeOutput);
    }

    protected static void colorBlockWithDye(RecipeOutput recipeOutput, List<Item> dyes, List<Item> dyeableItems, String group) {
        for (int i = 0; i < dyes.size(); i++) {
            Item item = dyes.get(i);
            Item item1 = dyeableItems.get(i);
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, item1)
                .requires(item)
                .requires(Ingredient.of(dyeableItems.stream().filter(p_288265_ -> !p_288265_.equals(item1)).map(ItemStack::new)))
                .group(group)
                .unlockedBy("has_needed_dye", has(item))
                .save(recipeOutput, "dye_" + getItemName(item1));
        }
    }

    protected static void carpet(RecipeOutput recipeOutput, ItemLike carpet, ItemLike material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, carpet, 3)
            .define('#', material)
            .pattern("##")
            .group("carpet")
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput);
    }

    protected static void bedFromPlanksAndWool(RecipeOutput recipeOutput, ItemLike bed, ItemLike wool) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, bed)
            .define('#', wool)
            .define('X', ItemTags.PLANKS)
            .pattern("###")
            .pattern("XXX")
            .group("bed")
            .unlockedBy(getHasName(wool), has(wool))
            .save(recipeOutput);
    }

    protected static void banner(RecipeOutput recipeOutput, ItemLike banner, ItemLike material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, banner)
            .define('#', material)
            .define('|', Items.STICK)
            .pattern("###")
            .pattern("###")
            .pattern(" | ")
            .group("banner")
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput);
    }

    protected static void stainedGlassFromGlassAndDye(RecipeOutput recipeOutput, ItemLike stainedGlass, ItemLike dye) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, stainedGlass, 8)
            .define('#', Blocks.GLASS)
            .define('X', dye)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("stained_glass")
            .unlockedBy("has_glass", has(Blocks.GLASS))
            .save(recipeOutput);
    }

    protected static void stainedGlassPaneFromStainedGlass(RecipeOutput recipeOutput, ItemLike stainedGlassPane, ItemLike stainedGlass) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, stainedGlassPane, 16)
            .define('#', stainedGlass)
            .pattern("###")
            .pattern("###")
            .group("stained_glass_pane")
            .unlockedBy("has_glass", has(stainedGlass))
            .save(recipeOutput);
    }

    protected static void stainedGlassPaneFromGlassPaneAndDye(RecipeOutput recipeOutput, ItemLike stainedGlassPane, ItemLike dye) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, stainedGlassPane, 8)
            .define('#', Blocks.GLASS_PANE)
            .define('$', dye)
            .pattern("###")
            .pattern("#$#")
            .pattern("###")
            .group("stained_glass_pane")
            .unlockedBy("has_glass_pane", has(Blocks.GLASS_PANE))
            .unlockedBy(getHasName(dye), has(dye))
            .save(recipeOutput, getConversionRecipeName(stainedGlassPane, Blocks.GLASS_PANE));
    }

    protected static void coloredTerracottaFromTerracottaAndDye(RecipeOutput recipeOutput, ItemLike terracotta, ItemLike dye) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, terracotta, 8)
            .define('#', Blocks.TERRACOTTA)
            .define('X', dye)
            .pattern("###")
            .pattern("#X#")
            .pattern("###")
            .group("stained_terracotta")
            .unlockedBy("has_terracotta", has(Blocks.TERRACOTTA))
            .save(recipeOutput);
    }

    protected static void concretePowder(RecipeOutput recipeOutput, ItemLike concretePowder, ItemLike dye) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, concretePowder, 8)
            .requires(dye)
            .requires(Blocks.SAND, 4)
            .requires(Blocks.GRAVEL, 4)
            .group("concrete_powder")
            .unlockedBy("has_sand", has(Blocks.SAND))
            .unlockedBy("has_gravel", has(Blocks.GRAVEL))
            .save(recipeOutput);
    }

    protected static void candle(RecipeOutput recipeOutput, ItemLike candle, ItemLike dye) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, candle)
            .requires(Blocks.CANDLE)
            .requires(dye)
            .group("dyed_candle")
            .unlockedBy(getHasName(dye), has(dye))
            .save(recipeOutput);
    }

    protected static void wall(RecipeOutput recipeOutput, RecipeCategory category, ItemLike wall, ItemLike material) {
        wallBuilder(category, wall, Ingredient.of(material)).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }

    protected static RecipeBuilder wallBuilder(RecipeCategory category, ItemLike wall, Ingredient material) {
        return ShapedRecipeBuilder.shaped(category, wall, 6).define('#', material).pattern("###").pattern("###");
    }

    protected static void polished(RecipeOutput recipeOutput, RecipeCategory category, ItemLike result, ItemLike material) {
        polishedBuilder(category, result, Ingredient.of(material)).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }

    protected static RecipeBuilder polishedBuilder(RecipeCategory category, ItemLike result, Ingredient material) {
        return ShapedRecipeBuilder.shaped(category, result, 4).define('S', material).pattern("SS").pattern("SS");
    }

    protected static void cut(RecipeOutput recipeOutput, RecipeCategory category, ItemLike cutResult, ItemLike material) {
        cutBuilder(category, cutResult, Ingredient.of(material)).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }

    protected static ShapedRecipeBuilder cutBuilder(RecipeCategory category, ItemLike cutResult, Ingredient material) {
        return ShapedRecipeBuilder.shaped(category, cutResult, 4).define('#', material).pattern("##").pattern("##");
    }

    protected static void chiseled(RecipeOutput recipeOutput, RecipeCategory category, ItemLike chiseledResult, ItemLike material) {
        chiseledBuilder(category, chiseledResult, Ingredient.of(material)).unlockedBy(getHasName(material), has(material)).save(recipeOutput);
    }

    protected static void mosaicBuilder(RecipeOutput recipeOutput, RecipeCategory category, ItemLike result, ItemLike material) {
        ShapedRecipeBuilder.shaped(category, result)
            .define('#', material)
            .pattern("#")
            .pattern("#")
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput);
    }

    protected static ShapedRecipeBuilder chiseledBuilder(RecipeCategory category, ItemLike chiseledResult, Ingredient material) {
        return ShapedRecipeBuilder.shaped(category, chiseledResult).define('#', material).pattern("#").pattern("#");
    }

    protected static void stonecutterResultFromBase(RecipeOutput recipeOutput, RecipeCategory category, ItemLike result, ItemLike material) {
        stonecutterResultFromBase(recipeOutput, category, result, material, 1);
    }

    protected static void stonecutterResultFromBase(RecipeOutput recipeOutput, RecipeCategory category, ItemLike result, ItemLike material, int resultCount) {
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(material), category, result, resultCount)
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput, getConversionRecipeName(result, material) + "_stonecutting");
    }

    protected static void smeltingResultFromBase(RecipeOutput recipeOutput, ItemLike result, ItemLike ingredient) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(ingredient), RecipeCategory.BUILDING_BLOCKS, result, 0.1F, 200)
            .unlockedBy(getHasName(ingredient), has(ingredient))
            .save(recipeOutput);
    }

    protected static void nineBlockStorageRecipes(
        RecipeOutput recipeOutput, RecipeCategory unpackedCategory, ItemLike unpacked, RecipeCategory packedCategory, ItemLike packed
    ) {
        nineBlockStorageRecipes(
            recipeOutput, unpackedCategory, unpacked, packedCategory, packed, getSimpleRecipeName(packed), null, getSimpleRecipeName(unpacked), null
        );
    }

    protected static void nineBlockStorageRecipesWithCustomPacking(
        RecipeOutput recipeOutput, RecipeCategory unpackedCategory, ItemLike unpacked, RecipeCategory packedCategory, ItemLike packed, String packedName, String packedGroup
    ) {
        nineBlockStorageRecipes(recipeOutput, unpackedCategory, unpacked, packedCategory, packed, packedName, packedGroup, getSimpleRecipeName(unpacked), null);
    }

    protected static void nineBlockStorageRecipesRecipesWithCustomUnpacking(
        RecipeOutput recipeOutput, RecipeCategory unpackedCategory, ItemLike unpacked, RecipeCategory packedCategory, ItemLike packed, String unpackedName, String unpackedGroup
    ) {
        nineBlockStorageRecipes(recipeOutput, unpackedCategory, unpacked, packedCategory, packed, getSimpleRecipeName(packed), null, unpackedName, unpackedGroup);
    }

    protected static void nineBlockStorageRecipes(
        RecipeOutput recipeOutput,
        RecipeCategory unpackedCategory,
        ItemLike unpacked,
        RecipeCategory packedCategory,
        ItemLike packed,
        String packedName,
        @Nullable String packedGroup,
        String unpackedName,
        @Nullable String unpackedGroup
    ) {
        ShapelessRecipeBuilder.shapeless(unpackedCategory, unpacked, 9)
            .requires(packed)
            .group(unpackedGroup)
            .unlockedBy(getHasName(packed), has(packed))
            .save(recipeOutput, ResourceLocation.parse(unpackedName));
        ShapedRecipeBuilder.shaped(packedCategory, packed)
            .define('#', unpacked)
            .pattern("###")
            .pattern("###")
            .pattern("###")
            .group(packedGroup)
            .unlockedBy(getHasName(unpacked), has(unpacked))
            .save(recipeOutput, ResourceLocation.parse(packedName));
    }

    protected static void copySmithingTemplate(RecipeOutput recipeOutput, ItemLike template, TagKey<Item> baseMaterial) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', baseMaterial)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), has(template))
            .save(recipeOutput);
    }

    protected static void copySmithingTemplate(RecipeOutput recipeOutput, ItemLike template, ItemLike baseItem) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', baseItem)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), has(template))
            .save(recipeOutput);
    }

    protected static void copySmithingTemplate(RecipeOutput recipeOutput, ItemLike template, Ingredient baseItem) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, template, 2)
            .define('#', Items.DIAMOND)
            .define('C', baseItem)
            .define('S', template)
            .pattern("#S#")
            .pattern("#C#")
            .pattern("###")
            .unlockedBy(getHasName(template), has(template))
            .save(recipeOutput);
    }

    protected static <T extends AbstractCookingRecipe> void cookRecipes(
        RecipeOutput recipeOutput, String cookingMethod, RecipeSerializer<T> cookingSerializer, AbstractCookingRecipe.Factory<T> recipeFactory, int cookingTime
    ) {
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.BEEF, Items.COOKED_BEEF, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.CHICKEN, Items.COOKED_CHICKEN, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.COD, Items.COOKED_COD, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.KELP, Items.DRIED_KELP, 0.1F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.SALMON, Items.COOKED_SALMON, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.MUTTON, Items.COOKED_MUTTON, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.PORKCHOP, Items.COOKED_PORKCHOP, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.POTATO, Items.BAKED_POTATO, 0.35F);
        simpleCookingRecipe(recipeOutput, cookingMethod, cookingSerializer, recipeFactory, cookingTime, Items.RABBIT, Items.COOKED_RABBIT, 0.35F);
    }

    protected static <T extends AbstractCookingRecipe> void simpleCookingRecipe(
        RecipeOutput recipeOutput,
        String cookingMethod,
        RecipeSerializer<T> cookingSerializer,
        AbstractCookingRecipe.Factory<T> recipeFactory,
        int cookingTime,
        ItemLike material,
        ItemLike result,
        float experience
    ) {
        SimpleCookingRecipeBuilder.generic(Ingredient.of(material), RecipeCategory.FOOD, result, experience, cookingTime, cookingSerializer, recipeFactory)
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput, getItemName(result) + "_from_" + cookingMethod);
    }

    protected static void waxRecipes(RecipeOutput recipeOutput, FeatureFlagSet requiredFeatures) {
        net.neoforged.neoforge.common.DataMapHooks.INVERSE_WAXABLES_DATAMAP
            .forEach(
                (p_337490_, p_337491_) -> {
                    if (p_337491_.requiredFeatures().isSubsetOf(requiredFeatures)) {
                        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, p_337491_)
                            .requires(p_337490_)
                            .requires(Items.HONEYCOMB)
                            .group(getItemName(p_337491_))
                            .unlockedBy(getHasName(p_337490_), has(p_337490_))
                            .save(recipeOutput, getConversionRecipeName(p_337491_, Items.HONEYCOMB));
                    }
                }
            );
    }

    protected static void grate(RecipeOutput recipeOutput, Block grateBlock, Block material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, grateBlock, 4)
            .define('M', material)
            .pattern(" M ")
            .pattern("M M")
            .pattern(" M ")
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput);
    }

    protected static void copperBulb(RecipeOutput recipeOutput, Block bulbBlock, Block material) {
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, bulbBlock, 4)
            .define('C', material)
            .define('R', Items.REDSTONE)
            .define('B', Items.BLAZE_ROD)
            .pattern(" C ")
            .pattern("CBC")
            .pattern(" R ")
            .unlockedBy(getHasName(material), has(material))
            .save(recipeOutput);
    }

    protected static void generateRecipes(RecipeOutput recipeOutput, BlockFamily blockFamily, FeatureFlagSet requiredFeatures) {
        blockFamily.getVariants()
            .forEach(
                (p_313457_, p_313458_) -> {
                    if (p_313458_.requiredFeatures().isSubsetOf(requiredFeatures)) {
                        BiFunction<ItemLike, ItemLike, RecipeBuilder> bifunction = SHAPE_BUILDERS.get(p_313457_);
                        ItemLike itemlike = getBaseBlock(blockFamily, p_313457_);
                        if (bifunction != null) {
                            RecipeBuilder recipebuilder = bifunction.apply(p_313458_, itemlike);
                            blockFamily.getRecipeGroupPrefix()
                                .ifPresent(
                                    p_293701_ -> recipebuilder.group(p_293701_ + (p_313457_ == BlockFamily.Variant.CUT ? "" : "_" + p_313457_.getRecipeGroup()))
                                );
                            recipebuilder.unlockedBy(blockFamily.getRecipeUnlockedBy().orElseGet(() -> getHasName(itemlike)), has(itemlike));
                            recipebuilder.save(recipeOutput);
                        }

                        if (p_313457_ == BlockFamily.Variant.CRACKED) {
                            smeltingResultFromBase(recipeOutput, p_313458_, itemlike);
                        }
                    }
                }
            );
    }

    protected static Block getBaseBlock(BlockFamily family, BlockFamily.Variant variant) {
        if (variant == BlockFamily.Variant.CHISELED) {
            if (!family.getVariants().containsKey(BlockFamily.Variant.SLAB)) {
                throw new IllegalStateException("Slab is not defined for the family.");
            } else {
                return family.get(BlockFamily.Variant.SLAB);
            }
        } else {
            return family.getBaseBlock();
        }
    }

    protected static Criterion<EnterBlockTrigger.TriggerInstance> insideOf(Block block) {
        return CriteriaTriggers.ENTER_BLOCK
            .createCriterion(new EnterBlockTrigger.TriggerInstance(Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.empty()));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> has(MinMaxBounds.Ints count, ItemLike item) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(item).withCount(count));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> has(ItemLike itemLike) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(itemLike));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> has(TagKey<Item> tag) {
        return inventoryTrigger(ItemPredicate.Builder.item().of(tag));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate.Builder... items) {
        return inventoryTrigger(Arrays.stream(items).map(ItemPredicate.Builder::build).toArray(ItemPredicate[]::new));
    }

    protected static Criterion<InventoryChangeTrigger.TriggerInstance> inventoryTrigger(ItemPredicate... predicates) {
        return CriteriaTriggers.INVENTORY_CHANGED
            .createCriterion(new InventoryChangeTrigger.TriggerInstance(Optional.empty(), InventoryChangeTrigger.TriggerInstance.Slots.ANY, List.of(predicates)));
    }

    protected static String getHasName(ItemLike itemLike) {
        return "has_" + getItemName(itemLike);
    }

    protected static String getItemName(ItemLike itemLike) {
        return BuiltInRegistries.ITEM.getKey(itemLike.asItem()).getPath();
    }

    protected static String getSimpleRecipeName(ItemLike itemLike) {
        return getItemName(itemLike);
    }

    protected static String getConversionRecipeName(ItemLike result, ItemLike ingredient) {
        return getItemName(result) + "_from_" + getItemName(ingredient);
    }

    protected static String getSmeltingRecipeName(ItemLike itemLike) {
        return getItemName(itemLike) + "_from_smelting";
    }

    protected static String getBlastingRecipeName(ItemLike itemLike) {
        return getItemName(itemLike) + "_from_blasting";
    }

    @Override
    public final String getName() {
        return "Recipes";
    }
}
