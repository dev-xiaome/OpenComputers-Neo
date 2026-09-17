package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsRenameFieldFix extends DataFix {
    private final String fixName;
    private final String fieldFrom;
    private final String fieldTo;

    public OptionsRenameFieldFix(Schema outputSchema, boolean changesType, String fixName, String fieldFrom, String fieldTo) {
        super(outputSchema, changesType);
        this.fixName = fixName;
        this.fieldFrom = fieldFrom;
        this.fieldTo = fieldTo;
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.fixName,
            this.getInputSchema().getType(References.OPTIONS),
            p_16676_ -> p_16676_.update(
                    DSL.remainderFinder(),
                    p_145592_ -> DataFixUtils.orElse(
                            p_145592_.get(this.fieldFrom).result().map(p_145595_ -> p_145592_.set(this.fieldTo, (Dynamic<?>)p_145595_).remove(this.fieldFrom)),
                            p_145592_
                        )
                )
        );
    }
}
