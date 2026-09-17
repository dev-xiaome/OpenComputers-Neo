package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.RandomSource;

public class EntityZombieVillagerTypeFix extends NamedEntityFix {
    private static final int PROFESSION_MAX = 6;

    public EntityZombieVillagerTypeFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType, "EntityZombieVillagerTypeFix", References.ENTITY, "Zombie");
    }

    public Dynamic<?> fixTag(Dynamic<?> tag) {
        if (tag.get("IsVillager").asBoolean(false)) {
            if (tag.get("ZombieType").result().isEmpty()) {
                int i = this.getVillagerProfession(tag.get("VillagerProfession").asInt(-1));
                if (i == -1) {
                    i = this.getVillagerProfession(RandomSource.create().nextInt(6));
                }

                tag = tag.set("ZombieType", tag.createInt(i));
            }

            tag = tag.remove("IsVillager");
        }

        return tag;
    }

    private int getVillagerProfession(int villagerProfession) {
        return villagerProfession >= 0 && villagerProfession < 6 ? villagerProfession : -1;
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fixTag);
    }
}
