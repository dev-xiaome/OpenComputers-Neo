package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.stream.Stream;

public class BlockEntityBannerColorFix extends NamedEntityFix {
    public BlockEntityBannerColorFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "BlockEntityBannerColorFix", References.BLOCK_ENTITY, "minecraft:banner");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        tag = tag.update("Base", p_14808_ -> p_14808_.createInt(15 - p_14808_.asInt(0)));
        return tag.update(
            "Patterns",
            p_337597_ -> DataFixUtils.orElse(
                    p_337597_.asStreamOpt()
                        .map(p_145125_ -> p_145125_.map(p_145127_ -> p_145127_.update("Color", p_145129_ -> p_145129_.createInt(15 - p_145129_.asInt(0)))))
                        .map(p_337597_::createList)
                        .result(),
                    p_337597_
                )
        );
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
