package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.function.Supplier;
import net.minecraft.Util;

public class EntityZombieSplitFix extends EntityRenameFix {
    private final Supplier<Type<?>> zombieVillagerType = Suppliers.memoize(() -> this.getOutputSchema().getChoiceType(References.ENTITY, "ZombieVillager"));

    public EntityZombieSplitFix(Schema outputSchema) {
        super("EntityZombieSplitFix", outputSchema, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String entityName, Typed<?> p_typed) {
        if (!entityName.equals("Zombie")) {
            return Pair.of(entityName, p_typed);
        } else {
            Dynamic<?> dynamic = p_typed.getOptional(DSL.remainderFinder()).orElseThrow();
            int i = dynamic.get("ZombieType").asInt(0);
            String s;
            Typed<?> typed;
            switch (i) {
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                    s = "ZombieVillager";
                    typed = this.changeSchemaToZombieVillager(p_typed, i - 1);
                    break;
                case 6:
                    s = "Husk";
                    typed = p_typed;
                    break;
                default:
                    s = "Zombie";
                    typed = p_typed;
            }

            return Pair.of(s, typed.update(DSL.remainderFinder(), p_341600_ -> p_341600_.remove("ZombieType")));
        }
    }

    private Typed<?> changeSchemaToZombieVillager(Typed<?> typed, int profession) {
        return Util.writeAndReadTypedOrThrow(typed, this.zombieVillagerType.get(), p_341611_ -> p_341611_.set("Profession", p_341611_.createInt(profession)));
    }
}
