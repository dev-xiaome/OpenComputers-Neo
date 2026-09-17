package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.function.Function;
import java.util.function.IntFunction;

public class EntityVariantFix extends NamedEntityFix {
    private final String fieldName;
    private final IntFunction<String> idConversions;

    public EntityVariantFix(Schema outputSchema, String name, TypeReference type, String entityName, String fieldName, IntFunction<String> idConversions) {
        super(outputSchema, false, name, type, entityName);
        this.fieldName = fieldName;
        this.idConversions = idConversions;
    }

    private static <T> Dynamic<T> updateAndRename(Dynamic<T> dynamic, String fieldName, String newFieldName, Function<Dynamic<T>, Dynamic<T>> fixer) {
        return dynamic.map(
            p_337624_ -> {
                DynamicOps<T> dynamicops = dynamic.getOps();
                Function<T, T> function = p_216656_ -> fixer.apply(new Dynamic<>(dynamicops, p_216656_)).getValue();
                return dynamicops.get((T)p_337624_, fieldName)
                    .map(p_216652_ -> dynamicops.set((T)p_337624_, newFieldName, function.apply((T)p_216652_)))
                    .result()
                    .orElse((T)p_337624_);
            }
        );
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(
            DSL.remainderFinder(),
            p_216632_ -> updateAndRename(
                    p_216632_,
                    this.fieldName,
                    "variant",
                    p_337619_ -> DataFixUtils.orElse(
                            p_337619_.asNumber().map(p_216635_ -> p_337619_.createString(this.idConversions.apply(p_216635_.intValue()))).result(), p_337619_
                        )
                )
        );
    }
}
