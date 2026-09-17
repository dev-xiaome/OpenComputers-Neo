package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.datafix.ComponentDataFixUtils;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class EntityCustomNameToComponentFix extends DataFix {
    public EntityCustomNameToComponentFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        OpticFinder<String> opticfinder = DSL.fieldFinder("id", NamespacedSchema.namespacedString());
        return this.fixTypeEverywhereTyped(
            "EntityCustomNameToComponentFix",
            this.getInputSchema().getType(References.ENTITY),
            p_15402_ -> p_15402_.update(DSL.remainderFinder(), p_145277_ -> {
                    Optional<String> optional = p_15402_.getOptional(opticfinder);
                    return optional.isPresent() && Objects.equals(optional.get(), "minecraft:commandblock_minecart") ? p_145277_ : fixTagCustomName(p_145277_);
                })
        );
    }

    public static Dynamic<?> fixTagCustomName(Dynamic<?> tag) {
        String s = tag.get("CustomName").asString("");
        return s.isEmpty() ? tag.remove("CustomName") : tag.set("CustomName", ComponentDataFixUtils.createPlainTextComponent(tag.getOps(), s));
    }
}
