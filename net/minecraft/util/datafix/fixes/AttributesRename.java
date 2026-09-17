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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class AttributesRename extends DataFix {
    private final String name;
    private final UnaryOperator<String> renames;

    public AttributesRename(Schema outputSchema, String name, UnaryOperator<String> renames) {
        super(outputSchema, false);
        this.name = name;
        this.renames = renames;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.ITEM_STACK);
        OpticFinder<?> opticfinder = type.findField("tag");
        return TypeRewriteRule.seq(
            this.fixTypeEverywhereTyped(this.name + " (ItemStack)", type, p_325649_ -> p_325649_.updateTyped(opticfinder, this::fixItemStackTag)),
            this.fixTypeEverywhereTyped(this.name + " (Entity)", this.getInputSchema().getType(References.ENTITY), this::fixEntity),
            this.fixTypeEverywhereTyped(this.name + " (Player)", this.getInputSchema().getType(References.PLAYER), this::fixEntity)
        );
    }

    private Dynamic<?> fixName(Dynamic<?> data) {
        return DataFixUtils.orElse(data.asString().result().map(this.renames).map(data::createString), data);
    }

    private Typed<?> fixItemStackTag(Typed<?> data) {
        return data.update(
            DSL.remainderFinder(),
            p_325650_ -> p_325650_.update(
                    "AttributeModifiers",
                    p_337594_ -> DataFixUtils.orElse(
                            p_337594_.asStreamOpt()
                                .result()
                                .map(p_325642_ -> p_325642_.map(p_325645_ -> p_325645_.update("AttributeName", this::fixName)))
                                .map(p_337594_::createList),
                            p_337594_
                        )
                )
        );
    }

    private Typed<?> fixEntity(Typed<?> data) {
        return data.update(
            DSL.remainderFinder(),
            p_325651_ -> p_325651_.update(
                    "Attributes",
                    p_337595_ -> DataFixUtils.orElse(
                            p_337595_.asStreamOpt()
                                .result()
                                .map(p_325646_ -> p_325646_.map(p_325643_ -> p_325643_.update("Name", this::fixName)))
                                .map(p_337595_::createList),
                            p_337595_
                        )
                )
        );
    }
}
