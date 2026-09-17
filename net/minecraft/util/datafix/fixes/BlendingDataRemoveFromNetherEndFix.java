package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;

public class BlendingDataRemoveFromNetherEndFix extends DataFix {
    public BlendingDataRemoveFromNetherEndFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getOutputSchema().getType(References.CHUNK);
        return this.fixTypeEverywhereTyped(
            "BlendingDataRemoveFromNetherEndFix",
            type,
            p_240286_ -> p_240286_.update(DSL.remainderFinder(), p_240254_ -> updateChunkTag(p_240254_, p_240254_.get("__context")))
        );
    }

    private static Dynamic<?> updateChunkTag(Dynamic<?> chunkTag, OptionalDynamic<?> context) {
        boolean flag = "minecraft:overworld".equals(context.get("dimension").asString().result().orElse(""));
        return flag ? chunkTag : chunkTag.remove("blending_data");
    }
}
