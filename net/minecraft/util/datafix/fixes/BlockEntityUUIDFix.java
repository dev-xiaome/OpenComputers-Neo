package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;

public class BlockEntityUUIDFix extends AbstractUUIDFix {
    public BlockEntityUUIDFix(Schema outputSchema) {
        super(outputSchema, References.BLOCK_ENTITY);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped("BlockEntityUUIDFix", this.getInputSchema().getType(this.typeReference), p_14885_ -> {
            p_14885_ = this.updateNamedChoice(p_14885_, "minecraft:conduit", this::updateConduit);
            return this.updateNamedChoice(p_14885_, "minecraft:skull", this::updateSkull);
        });
    }

    private Dynamic<?> updateSkull(Dynamic<?> skullTag) {
        return skullTag.get("Owner")
            .get()
            .map(p_14894_ -> replaceUUIDString((Dynamic<?>)p_14894_, "Id", "Id").orElse((Dynamic<?>)p_14894_))
            .<Dynamic<?>>map(p_14888_ -> skullTag.remove("Owner").set("SkullOwner", (Dynamic<?>)p_14888_))
            .result()
            .orElse(skullTag);
    }

    private Dynamic<?> updateConduit(Dynamic<?> conduitTag) {
        return replaceUUIDMLTag(conduitTag, "target_uuid", "Target").orElse(conduitTag);
    }
}
