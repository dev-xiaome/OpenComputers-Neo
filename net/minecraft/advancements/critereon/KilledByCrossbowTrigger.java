package net.minecraft.advancements.critereon;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootContext;

public class KilledByCrossbowTrigger extends SimpleCriterionTrigger<KilledByCrossbowTrigger.TriggerInstance> {
    @Override
    public Codec<KilledByCrossbowTrigger.TriggerInstance> codec() {
        return KilledByCrossbowTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, Collection<Entity> entities) {
        List<LootContext> list = Lists.newArrayList();
        Set<EntityType<?>> set = Sets.newHashSet();

        for (Entity entity : entities) {
            set.add(entity.getType());
            list.add(EntityPredicate.createContext(player, entity));
        }

        this.trigger(player, p_46881_ -> p_46881_.matches(list, set.size()));
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, List<ContextAwarePredicate> victims, MinMaxBounds.Ints uniqueEntityTypes)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<KilledByCrossbowTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337373_ -> p_337373_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(KilledByCrossbowTrigger.TriggerInstance::player),
                        EntityPredicate.ADVANCEMENT_CODEC
                            .listOf()
                            .optionalFieldOf("victims", List.of())
                            .forGetter(KilledByCrossbowTrigger.TriggerInstance::victims),
                        MinMaxBounds.Ints.CODEC
                            .optionalFieldOf("unique_entity_types", MinMaxBounds.Ints.ANY)
                            .forGetter(KilledByCrossbowTrigger.TriggerInstance::uniqueEntityTypes)
                    )
                    .apply(p_337373_, KilledByCrossbowTrigger.TriggerInstance::new)
        );

        public static Criterion<KilledByCrossbowTrigger.TriggerInstance> crossbowKilled(EntityPredicate.Builder... victims) {
            return CriteriaTriggers.KILLED_BY_CROSSBOW
                .createCriterion(new KilledByCrossbowTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(victims), MinMaxBounds.Ints.ANY));
        }

        public static Criterion<KilledByCrossbowTrigger.TriggerInstance> crossbowKilled(MinMaxBounds.Ints uniqueEntityTypes) {
            return CriteriaTriggers.KILLED_BY_CROSSBOW.createCriterion(new KilledByCrossbowTrigger.TriggerInstance(Optional.empty(), List.of(), uniqueEntityTypes));
        }

        public boolean matches(Collection<LootContext> contexts, int bounds) {
            if (!this.victims.isEmpty()) {
                List<LootContext> list = Lists.newArrayList(contexts);

                for (ContextAwarePredicate contextawarepredicate : this.victims) {
                    boolean flag = false;
                    Iterator<LootContext> iterator = list.iterator();

                    while (iterator.hasNext()) {
                        LootContext lootcontext = iterator.next();
                        if (contextawarepredicate.matches(lootcontext)) {
                            iterator.remove();
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }
            }

            return this.uniqueEntityTypes.matches(bounds);
        }

        @Override
        public void validate(CriterionValidator validator) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
            validator.validateEntities(this.victims, ".victims");
        }
    }
}
