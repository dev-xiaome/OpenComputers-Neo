package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.Util;

public class AnyOfCondition extends CompositeLootItemCondition {
    public static final MapCodec<AnyOfCondition> CODEC = createCodec(AnyOfCondition::new);

    AnyOfCondition(List<LootItemCondition> conditions) {
        super(conditions, Util.anyOf(conditions));
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.ANY_OF;
    }

    public static AnyOfCondition.Builder anyOf(LootItemCondition.Builder... conditions) {
        return new AnyOfCondition.Builder(conditions);
    }

    public static class Builder extends CompositeLootItemCondition.Builder {
        public Builder(LootItemCondition.Builder... p_286497_) {
            super(p_286497_);
        }

        @Override
        public AnyOfCondition.Builder or(LootItemCondition.Builder p_286344_) {
            this.addTerm(p_286344_);
            return this;
        }

        @Override
        protected LootItemCondition create(List<LootItemCondition> p_298816_) {
            return new AnyOfCondition(p_298816_);
        }
    }
}
