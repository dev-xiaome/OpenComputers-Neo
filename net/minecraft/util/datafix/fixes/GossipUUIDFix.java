package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.stream.Stream;

public class GossipUUIDFix extends NamedEntityFix {
    public GossipUUIDFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "Gossip for for " + entityName, References.ENTITY, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(
            DSL.remainderFinder(),
            p_15883_ -> p_15883_.update(
                    "Gossips",
                    p_337635_ -> DataFixUtils.orElse(
                            p_337635_.asStreamOpt()
                                .result()
                                .map(
                                    p_145374_ -> p_145374_.map(
                                            p_145378_ -> AbstractUUIDFix.replaceUUIDLeastMost((Dynamic<?>)p_145378_, "Target", "Target")
                                                    .orElse((Dynamic<?>)p_145378_)
                                        )
                                )
                                .map(p_337635_::createList),
                            p_337635_
                        )
                )
        );
    }
}
