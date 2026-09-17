package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class OptionsForceVBOFix extends DataFix {
    public OptionsForceVBOFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "OptionsForceVBOFix",
            this.getInputSchema().getType(References.OPTIONS),
            p_16623_ -> p_16623_.update(DSL.remainderFinder(), p_145572_ -> p_145572_.set("useVbo", p_145572_.createString("true")))
        );
    }
}
