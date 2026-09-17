package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * A LootItemCondition which checks {@link LootContextParams#ORIGIN} and {@link LootContextParams#DAMAGE_SOURCE} against a {@link DamageSourcePredicate}.
 */
public record DamageSourceCondition(Optional<DamageSourcePredicate> predicate) implements LootItemCondition {
    public static final MapCodec<DamageSourceCondition> CODEC = RecordCodecBuilder.mapCodec(
        p_338167_ -> p_338167_.group(DamageSourcePredicate.CODEC.optionalFieldOf("predicate").forGetter(DamageSourceCondition::predicate))
                .apply(p_338167_, DamageSourceCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.DAMAGE_SOURCE_PROPERTIES;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.ORIGIN, LootContextParams.DAMAGE_SOURCE);
    }

    public boolean test(LootContext context) {
        DamageSource damagesource = context.getParamOrNull(LootContextParams.DAMAGE_SOURCE);
        Vec3 vec3 = context.getParamOrNull(LootContextParams.ORIGIN);
        return vec3 != null && damagesource != null ? this.predicate.isEmpty() || this.predicate.get().matches(context.getLevel(), vec3, damagesource) : false;
    }

    public static LootItemCondition.Builder hasDamageSource(DamageSourcePredicate.Builder builder) {
        return () -> new DamageSourceCondition(Optional.of(builder.build()));
    }
}
