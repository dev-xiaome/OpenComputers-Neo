package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BeeNestDestroyedTrigger extends SimpleCriterionTrigger<BeeNestDestroyedTrigger.TriggerInstance> {
    @Override
    public Codec<BeeNestDestroyedTrigger.TriggerInstance> codec() {
        return BeeNestDestroyedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, BlockState state, ItemStack stack, int numBees) {
        this.trigger(player, p_146660_ -> p_146660_.matches(state, stack, numBees));
    }

    public static record TriggerInstance(
        Optional<ContextAwarePredicate> player, Optional<Holder<Block>> block, Optional<ItemPredicate> item, MinMaxBounds.Ints beesInside
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<BeeNestDestroyedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_344144_ -> p_344144_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BeeNestDestroyedTrigger.TriggerInstance::player),
                        BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("block").forGetter(BeeNestDestroyedTrigger.TriggerInstance::block),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(BeeNestDestroyedTrigger.TriggerInstance::item),
                        MinMaxBounds.Ints.CODEC
                            .optionalFieldOf("num_bees_inside", MinMaxBounds.Ints.ANY)
                            .forGetter(BeeNestDestroyedTrigger.TriggerInstance::beesInside)
                    )
                    .apply(p_344144_, BeeNestDestroyedTrigger.TriggerInstance::new)
        );

        public static Criterion<BeeNestDestroyedTrigger.TriggerInstance> destroyedBeeNest(
            Block block, ItemPredicate.Builder item, MinMaxBounds.Ints numBees
        ) {
            return CriteriaTriggers.BEE_NEST_DESTROYED
                .createCriterion(
                    new BeeNestDestroyedTrigger.TriggerInstance(
                        Optional.empty(), Optional.of(block.builtInRegistryHolder()), Optional.of(item.build()), numBees
                    )
                );
        }

        public boolean matches(BlockState state, ItemStack stack, int numBees) {
            if (this.block.isPresent() && !state.is(this.block.get())) {
                return false;
            } else {
                return this.item.isPresent() && !this.item.get().test(stack) ? false : this.beesInside.matches(numBees);
            }
        }
    }
}
