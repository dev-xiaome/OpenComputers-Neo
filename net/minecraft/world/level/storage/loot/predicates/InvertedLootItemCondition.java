package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Set;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

/**
 * A LootItemCondition that inverts the output of another one.
 */
public record InvertedLootItemCondition(LootItemCondition term) implements LootItemCondition {
    public static final MapCodec<InvertedLootItemCondition> CODEC = RecordCodecBuilder.mapCodec(
        p_344715_ -> p_344715_.group(LootItemCondition.DIRECT_CODEC.fieldOf("term").forGetter(InvertedLootItemCondition::term))
                .apply(p_344715_, InvertedLootItemCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.INVERTED;
    }

    public boolean test(LootContext context) {
        return !this.term.test(context);
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.term.getReferencedContextParams();
    }

    /**
     * Validate that this object is used correctly according to the given ValidationContext.
     */
    @Override
    public void validate(ValidationContext context) {
        LootItemCondition.super.validate(context);
        this.term.validate(context);
    }

    public static LootItemCondition.Builder invert(LootItemCondition.Builder toInvert) {
        InvertedLootItemCondition invertedlootitemcondition = new InvertedLootItemCondition(toInvert.build());
        return () -> invertedlootitemcondition;
    }
}
