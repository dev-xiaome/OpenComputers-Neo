package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;

public class ForcePoiRebuild extends DataFix {
    public ForcePoiRebuild(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<Pair<String, Dynamic<?>>> type = DSL.named(References.POI_CHUNK.typeName(), DSL.remainderType());
        if (!Objects.equals(type, this.getInputSchema().getType(References.POI_CHUNK))) {
            throw new IllegalStateException("Poi type is not what was expected.");
        } else {
            return this.fixTypeEverywhere("POI rebuild", type, p_15828_ -> p_145354_ -> p_145354_.mapSecond(ForcePoiRebuild::cap));
        }
    }

    private static <T> Dynamic<T> cap(Dynamic<T> dynamic) {
        return dynamic.update("Sections", p_15832_ -> p_15832_.updateMapValues(p_145352_ -> p_145352_.mapSecond(p_145356_ -> p_145356_.remove("Valid"))));
    }
}
