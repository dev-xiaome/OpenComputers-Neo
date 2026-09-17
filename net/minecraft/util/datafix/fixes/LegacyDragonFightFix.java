package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class LegacyDragonFightFix extends DataFix {
    public LegacyDragonFightFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    private static <T> Dynamic<T> fixDragonFight(Dynamic<T> data) {
        return data.update("ExitPortalLocation", ExtraDataFixUtils::fixBlockPos);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "LegacyDragonFightFix", this.getInputSchema().getType(References.LEVEL), p_289787_ -> p_289787_.update(DSL.remainderFinder(), p_325659_ -> {
                    OptionalDynamic<?> optionaldynamic = p_325659_.get("DragonFight");
                    if (optionaldynamic.result().isPresent()) {
                        return p_325659_;
                    } else {
                        Dynamic<?> dynamic = p_325659_.get("DimensionData").get("1").get("DragonFight").orElseEmptyMap();
                        return p_325659_.set("DragonFight", fixDragonFight(dynamic));
                    }
                })
        );
    }
}
