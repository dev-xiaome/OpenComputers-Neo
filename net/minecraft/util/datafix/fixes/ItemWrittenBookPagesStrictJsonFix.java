package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class ItemWrittenBookPagesStrictJsonFix extends DataFix {
    public ItemWrittenBookPagesStrictJsonFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        return tag.update(
            "pages",
            p_337643_ -> DataFixUtils.orElse(
                    p_337643_.asStreamOpt().map(p_145441_ -> p_145441_.map(ComponentDataFixUtils::rewriteFromLenient)).map(tag::createList).result(),
                    tag.emptyList()
                )
        );
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            "ItemWrittenBookPagesStrictJsonFix",
            type,
            p_16168_ -> p_16168_.updateTyped(opticfinder, p_145439_ -> p_145439_.update(DSL.remainderFinder(), this::fixTag))
        );
    }
}
