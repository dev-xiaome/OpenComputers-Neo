package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class JigsawPropertiesFix extends NamedEntityFix {
    public JigsawPropertiesFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "JigsawPropertiesFix", References.BLOCK_ENTITY, "minecraft:jigsaw");
    }

    private static Dynamic<?> fixTag(Dynamic<?> tag) {
        String s = tag.get("attachement_type").asString("minecraft:empty");
        String s1 = tag.get("target_pool").asString("minecraft:empty");
        return tag.set("name", tag.createString(s))
            .set("target", tag.createString(s))
            .remove("attachement_type")
            .set("pool", tag.createString(s1))
            .remove("target_pool");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), JigsawPropertiesFix::fixTag);
    }
}
