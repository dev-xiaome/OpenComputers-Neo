package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class FixProjectileStoredItem extends DataFix {
    private static final String EMPTY_POTION = "minecraft:empty";

    public FixProjectileStoredItem(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped(
            "Fix AbstractArrow item type",
            type,
            type1,
            ExtraDataFixUtils.chainAllFilters(
                this.fixChoice("minecraft:trident", FixProjectileStoredItem::castUnchecked),
                this.fixChoice("minecraft:arrow", FixProjectileStoredItem::fixArrow),
                this.fixChoice("minecraft:spectral_arrow", FixProjectileStoredItem::fixSpectralArrow)
            )
        );
    }

    private Function<Typed<?>, Typed<?>> fixChoice(String itemId, FixProjectileStoredItem.SubFixer<?> fixer) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, itemId);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, itemId);
        return fixChoiceCap(itemId, fixer, type, type1);
    }

    private static <T> Function<Typed<?>, Typed<?>> fixChoiceCap(
        String itemId, FixProjectileStoredItem.SubFixer<?> fixer, Type<?> oldType, Type<T> newType
    ) {
        OpticFinder<?> opticfinder = DSL.namedChoice(itemId, oldType);
        return p_309195_ -> p_309195_.updateTyped(opticfinder, newType, p_309191_ -> ((FixProjectileStoredItem.SubFixer<T>)fixer).fix(p_309191_, newType));
    }

    private static <T> Typed<T> fixArrow(Typed<?> typed, Type<T> newType) {
        return Util.writeAndReadTypedOrThrow(typed, newType, p_309043_ -> p_309043_.set("item", createItemStack(p_309043_, getArrowType(p_309043_))));
    }

    private static String getArrowType(Dynamic<?> arrowTag) {
        return arrowTag.get("Potion").asString("minecraft:empty").equals("minecraft:empty") ? "minecraft:arrow" : "minecraft:tipped_arrow";
    }

    private static <T> Typed<T> fixSpectralArrow(Typed<?> typed, Type<T> newType) {
        return Util.writeAndReadTypedOrThrow(typed, newType, p_309009_ -> p_309009_.set("item", createItemStack(p_309009_, "minecraft:spectral_arrow")));
    }

    private static Dynamic<?> createItemStack(Dynamic<?> dynamic, String itemId) {
        return dynamic.createMap(
            ImmutableMap.of(dynamic.createString("id"), dynamic.createString(itemId), dynamic.createString("Count"), dynamic.createInt(1))
        );
    }

    private static <T> Typed<T> castUnchecked(Typed<?> typed, Type<T> newType) {
        return new Typed<>(newType, typed.getOps(), (T)typed.getValue());
    }

    interface SubFixer<F> {
        Typed<F> fix(Typed<?> typed, Type<F> newType);
    }
}
