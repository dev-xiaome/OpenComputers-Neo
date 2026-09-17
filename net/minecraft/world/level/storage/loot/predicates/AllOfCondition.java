package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.Util;

public class AllOfCondition extends CompositeLootItemCondition {
    public static final MapCodec<AllOfCondition> CODEC = createCodec(AllOfCondition::new);
    public static final Codec<AllOfCondition> INLINE_CODEC = createInlineCodec(AllOfCondition::new);

    AllOfCondition(List<LootItemCondition> conditions) {
        super(conditions, Util.allOf(conditions));
    }

    public static AllOfCondition allOf(List<LootItemCondition> conditions) {
        return new AllOfCondition(List.copyOf(conditions));
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.ALL_OF;
    }

    public static AllOfCondition.Builder allOf(LootItemCondition.Builder... conditions) {
        return new AllOfCondition.Builder(conditions);
    }

    public static class Builder extends CompositeLootItemCondition.Builder {
        public Builder(LootItemCondition.Builder... p_286842_) {
            super(p_286842_);
        }

        @Override
        public AllOfCondition.Builder and(LootItemCondition.Builder p_286760_) {
            this.addTerm(p_286760_);
            return this;
        }

        @Override
        protected LootItemCondition create(List<LootItemCondition> p_298491_) {
            return new AllOfCondition(p_298491_);
        }
    }
}
