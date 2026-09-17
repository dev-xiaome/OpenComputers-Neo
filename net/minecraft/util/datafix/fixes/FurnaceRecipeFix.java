package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;

public class FurnaceRecipeFix extends DataFix {
    public FurnaceRecipeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.cap(this.getOutputSchema().getTypeRaw(References.RECIPE));
    }

    private <R> TypeRewriteRule cap(Type<R> p_type) {
        Type<Pair<Either<Pair<List<Pair<R, Integer>>, Dynamic<?>>, Unit>, Dynamic<?>>> type = DSL.and(
            DSL.optional(DSL.field("RecipesUsed", DSL.and(DSL.compoundList(p_type, DSL.intType()), DSL.remainderType()))), DSL.remainderType()
        );
        OpticFinder<?> opticfinder = DSL.namedChoice("minecraft:furnace", this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:furnace"));
        OpticFinder<?> opticfinder1 = DSL.namedChoice(
            "minecraft:blast_furnace", this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:blast_furnace")
        );
        OpticFinder<?> opticfinder2 = DSL.namedChoice("minecraft:smoker", this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:smoker"));
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:furnace");
        Type<?> type2 = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:blast_furnace");
        Type<?> type3 = this.getOutputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:smoker");
        Type<?> type4 = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type5 = this.getOutputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "FurnaceRecipesFix",
            type4,
            type5,
            p_15848_ -> p_15848_.updateTyped(opticfinder, type1, p_145372_ -> this.updateFurnaceContents(p_type, type, p_145372_))
                    .updateTyped(opticfinder1, type2, p_145368_ -> this.updateFurnaceContents(p_type, type, p_145368_))
                    .updateTyped(opticfinder2, type3, p_145364_ -> this.updateFurnaceContents(p_type, type, p_145364_))
        );
    }

    private <R> Typed<?> updateFurnaceContents(
        Type<R> type, Type<Pair<Either<Pair<List<Pair<R, Integer>>, Dynamic<?>>, Unit>, Dynamic<?>>> recipesUsed, Typed<?> data
    ) {
        Dynamic<?> dynamic = data.getOrCreate(DSL.remainderFinder());
        int i = dynamic.get("RecipesUsedSize").asInt(0);
        dynamic = dynamic.remove("RecipesUsedSize");
        List<Pair<R, Integer>> list = Lists.newArrayList();

        for (int j = 0; j < i; j++) {
            String s = "RecipeLocation" + j;
            String s1 = "RecipeAmount" + j;
            Optional<? extends Dynamic<?>> optional = dynamic.get(s).result();
            int k = dynamic.get(s1).asInt(0);
            if (k > 0) {
                optional.ifPresent(p_337634_ -> {
                    Optional<? extends Pair<R, ? extends Dynamic<?>>> optional1 = type.read((Dynamic<?>)p_337634_).result();
                    optional1.ifPresent(p_145360_ -> list.add(Pair.of(p_145360_.getFirst(), k)));
                });
            }

            dynamic = dynamic.remove(s).remove(s1);
        }

        return data.set(DSL.remainderFinder(), recipesUsed, Pair.of(Either.left(Pair.of(list, dynamic.emptyMap())), dynamic));
    }
}
