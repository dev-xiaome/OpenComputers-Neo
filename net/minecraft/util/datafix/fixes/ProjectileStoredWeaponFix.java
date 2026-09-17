package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.minecraft.Util;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class ProjectileStoredWeaponFix extends DataFix {
    public ProjectileStoredWeaponFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ENTITY);
        Type<?> type1 = this.getOutputSchema().getType(References.ENTITY);
        return this.fixTypeEverywhereTyped(
            "Fix Arrow stored weapon",
            type,
            type1,
            ExtraDataFixUtils.chainAllFilters(this.fixChoice("minecraft:arrow"), this.fixChoice("minecraft:spectral_arrow"))
        );
    }

    private Function<Typed<?>, Typed<?>> fixChoice(String entityId) {
        Type<?> type = this.getInputSchema().getChoiceType(References.ENTITY, entityId);
        Type<?> type1 = this.getOutputSchema().getChoiceType(References.ENTITY, entityId);
        return fixChoiceCap(entityId, type, type1);
    }

    private static <T> Function<Typed<?>, Typed<?>> fixChoiceCap(String entityId, Type<?> inputType, Type<T> outputType) {
        OpticFinder<?> opticfinder = DSL.namedChoice(entityId, inputType);
        return p_346386_ -> p_346386_.updateTyped(
                opticfinder, outputType, p_345343_ -> Util.writeAndReadTypedOrThrow(p_345343_, outputType, UnaryOperator.identity())
            );
    }
}
