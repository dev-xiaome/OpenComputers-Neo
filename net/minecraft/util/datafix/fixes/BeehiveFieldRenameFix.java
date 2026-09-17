package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.datafix.ExtraDataFixUtils;

public class BeehiveFieldRenameFix extends DataFix {
    public BeehiveFieldRenameFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    private Dynamic<?> fixBeehive(Dynamic<?> tag) {
        return tag.remove("Bees");
    }

    private Dynamic<?> fixBee(Dynamic<?> tag) {
        tag = tag.remove("EntityData");
        tag = tag.renameField("TicksInHive", "ticks_in_hive");
        return tag.renameField("MinOccupationTicks", "min_ticks_in_hive");
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getChoiceType(References.BLOCK_ENTITY, "minecraft:beehive");
        OpticFinder<?> opticfinder = DSL.namedChoice("minecraft:beehive", type);
        ListType<?> listtype = (ListType<?>)type.findFieldType("Bees");
        Type<?> type1 = listtype.getElement();
        OpticFinder<?> opticfinder1 = DSL.fieldFinder("Bees", listtype);
        OpticFinder<?> opticfinder2 = DSL.typeFinder(type1);
        Type<?> type2 = this.getInputSchema().getType(References.BLOCK_ENTITY);
        Type<?> type3 = this.getOutputSchema().getType(References.BLOCK_ENTITY);
        return this.fixTypeEverywhereTyped(
            "BeehiveFieldRenameFix",
            type2,
            type3,
            p_330576_ -> ExtraDataFixUtils.cast(
                    type3,
                    p_330576_.updateTyped(
                        opticfinder,
                        p_331994_ -> p_331994_.update(DSL.remainderFinder(), this::fixBeehive)
                                .updateTyped(
                                    opticfinder1,
                                    p_331926_ -> p_331926_.updateTyped(opticfinder2, p_331349_ -> p_331349_.update(DSL.remainderFinder(), this::fixBee))
                                )
                    )
                )
        );
    }
}
