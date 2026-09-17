package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class AbstractPoiSectionFix extends DataFix {
    private final String name;

    public AbstractPoiSectionFix(Schema outputSchema, String name) {
        super(outputSchema, false);
        this.name = name;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<Pair<String, Dynamic<?>>> type = DSL.named(References.POI_CHUNK.typeName(), DSL.remainderType());
        if (!Objects.equals(type, this.getInputSchema().getType(References.POI_CHUNK))) {
            throw new IllegalStateException("Poi type is not what was expected.");
        } else {
            return this.fixTypeEverywhere(this.name, type, p_216546_ -> p_216549_ -> p_216549_.mapSecond(this::cap));
        }
    }

    private <T> Dynamic<T> cap(Dynamic<T> dynamic) {
        return dynamic.update("Sections", p_216555_ -> p_216555_.updateMapValues(p_216539_ -> p_216539_.mapSecond(this::processSection)));
    }

    private Dynamic<?> processSection(Dynamic<?> dynamic) {
        return dynamic.update("Records", this::processSectionRecords);
    }

    private <T> Dynamic<T> processSectionRecords(Dynamic<T> dynamic) {
        return DataFixUtils.orElse(
            dynamic.asStreamOpt().result().map(p_216544_ -> dynamic.createList(this.processRecords((Stream<Dynamic<T>>)p_216544_))), dynamic
        );
    }

    protected abstract <T> Stream<Dynamic<T>> processRecords(Stream<Dynamic<T>> records);
}
