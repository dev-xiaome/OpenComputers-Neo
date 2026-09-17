package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;
import java.util.UUID;

public class EntityStringUuidFix extends DataFix {
    public EntityStringUuidFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "EntityStringUuidFix",
            this.getInputSchema().getType(References.ENTITY),
            p_15697_ -> p_15697_.update(
                    DSL.remainderFinder(),
                    p_337618_ -> {
                        Optional<String> optional = p_337618_.get("UUID").asString().result();
                        if (optional.isPresent()) {
                            UUID uuid = UUID.fromString(optional.get());
                            return p_337618_.remove("UUID")
                                .set("UUIDMost", p_337618_.createLong(uuid.getMostSignificantBits()))
                                .set("UUIDLeast", p_337618_.createLong(uuid.getLeastSignificantBits()));
                        } else {
                            return p_337618_;
                        }
                    }
                )
        );
    }
}
