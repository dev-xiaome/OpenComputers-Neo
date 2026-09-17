package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;

public class AddFlagIfNotPresentFix extends DataFix {
    private final String name;
    private final boolean flagValue;
    private final String flagKey;
    private final TypeReference typeReference;

    public AddFlagIfNotPresentFix(Schema outputSchema, TypeReference typeReference, String flagKey, boolean flagValue) {
        super(outputSchema, true);
        this.flagValue = flagValue;
        this.flagKey = flagKey;
        this.name = "AddFlagIfNotPresentFix_" + this.flagKey + "=" + this.flagValue + " for " + outputSchema.getVersionKey();
        this.typeReference = typeReference;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.typeReference);
        return this.fixTypeEverywhereTyped(
            this.name,
            type,
            p_184815_ -> p_184815_.update(
                    DSL.remainderFinder(),
                    p_184817_ -> p_184817_.set(
                            this.flagKey, DataFixUtils.orElseGet(p_184817_.get(this.flagKey).result(), () -> p_184817_.createBoolean(this.flagValue))
                        )
                )
        );
    }
}
