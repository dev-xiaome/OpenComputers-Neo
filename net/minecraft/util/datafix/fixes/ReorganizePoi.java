package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class ReorganizePoi extends DataFix {
    public ReorganizePoi(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<Pair<String, Dynamic<?>>> type = DSL.named(References.POI_CHUNK.typeName(), DSL.remainderType());
        if (!Objects.equals(type, this.getInputSchema().getType(References.POI_CHUNK))) {
            throw new IllegalStateException("Poi type is not what was expected.");
        } else {
            return this.fixTypeEverywhere("POI reorganization", type, p_16860_ -> p_145640_ -> p_145640_.mapSecond(ReorganizePoi::cap));
        }
    }

    private static <T> Dynamic<T> cap(Dynamic<T> p_dynamic) {
        Map<Dynamic<T>, Dynamic<T>> map = Maps.newHashMap();

        for (int i = 0; i < 16; i++) {
            String s = String.valueOf(i);
            Optional<Dynamic<T>> optional = p_dynamic.get(s).result();
            if (optional.isPresent()) {
                Dynamic<T> dynamic = optional.get();
                Dynamic<T> dynamic1 = p_dynamic.createMap(ImmutableMap.of(p_dynamic.createString("Records"), dynamic));
                map.put(p_dynamic.createInt(i), dynamic1);
                p_dynamic = p_dynamic.remove(s);
            }
        }

        return p_dynamic.set("Sections", p_dynamic.createMap(map));
    }
}
