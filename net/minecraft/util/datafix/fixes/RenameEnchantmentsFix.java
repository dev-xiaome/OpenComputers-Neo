package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DataResult.Error;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class RenameEnchantmentsFix extends DataFix {
    final String name;
    final Map<String, String> renames;

    public RenameEnchantmentsFix(Schema outputSchema, String name, Map<String, String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");
        return this.fixTypeEverywhereTyped(
            this.name, type, p_320338_ -> p_320338_.updateTyped(opticfinder, p_320499_ -> p_320499_.update(DSL.remainderFinder(), this::fixTag))
        );
    }

    private Dynamic<?> fixTag(Dynamic<?> tag) {
        tag = this.fixEnchantmentList(tag, "Enchantments");
        return this.fixEnchantmentList(tag, "StoredEnchantments");
    }

    private Dynamic<?> fixEnchantmentList(Dynamic<?> tag, String key) {
        return tag.update(
            key,
            p_337664_ -> p_337664_.asStreamOpt()
                    .map(
                        p_320850_ -> p_320850_.map(
                                p_320794_ -> p_320794_.update(
                                        "id",
                                        p_337663_ -> p_337663_.asString()
                                                .map(
                                                    p_344279_ -> p_320794_.createString(
                                                            this.renames.getOrDefault(NamespacedSchema.ensureNamespaced(p_344279_), p_344279_)
                                                        )
                                                )
                                                .mapOrElse(Function.identity(), p_338509_ -> p_337663_)
                                    )
                            )
                    )
                    .map(p_337664_::createList)
                    .mapOrElse(Function.identity(), p_338319_ -> p_337664_)
        );
    }
}
