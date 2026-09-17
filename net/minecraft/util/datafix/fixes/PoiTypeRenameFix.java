package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.Function;
import java.util.stream.Stream;

public class PoiTypeRenameFix extends AbstractPoiSectionFix {
    private final Function<String, String> renamer;

    public PoiTypeRenameFix(Schema schema, String name, Function<String, String> renamer) {
        super(schema, name);
        this.renamer = renamer;
    }

    @Override
    protected <T> Stream<Dynamic<T>> processRecords(Stream<Dynamic<T>> records) {
        return records.map(
            p_216714_ -> p_216714_.update(
                    "type", p_337661_ -> DataFixUtils.orElse(p_337661_.asString().map(this.renamer).map(p_337661_::createString).result(), p_337661_)
                )
        );
    }
}
