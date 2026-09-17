package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

/**
 * LootItemFunction that adds an effect to any suspicious stew items. A random effect is chosen from the given map every time.
 */
public class SetStewEffectFunction extends LootItemConditionalFunction {
    private static final Codec<List<SetStewEffectFunction.EffectEntry>> EFFECTS_LIST = SetStewEffectFunction.EffectEntry.CODEC.listOf().validate(p_298165_ -> {
        Set<Holder<MobEffect>> set = new ObjectOpenHashSet<>();

        for (SetStewEffectFunction.EffectEntry setsteweffectfunction$effectentry : p_298165_) {
            if (!set.add(setsteweffectfunction$effectentry.effect())) {
                return DataResult.error(() -> "Encountered duplicate mob effect: '" + setsteweffectfunction$effectentry.effect() + "'");
            }
        }

        return DataResult.success(p_298165_);
    });
    public static final MapCodec<SetStewEffectFunction> CODEC = RecordCodecBuilder.mapCodec(
        p_338164_ -> commonFields(p_338164_)
                .and(EFFECTS_LIST.optionalFieldOf("effects", List.of()).forGetter(p_298162_ -> p_298162_.effects))
                .apply(p_338164_, SetStewEffectFunction::new)
    );
    private final List<SetStewEffectFunction.EffectEntry> effects;

    SetStewEffectFunction(List<LootItemCondition> conditions, List<SetStewEffectFunction.EffectEntry> effects) {
        super(conditions);
        this.effects = effects;
    }

    @Override
    public LootItemFunctionType<SetStewEffectFunction> getType() {
        return LootItemFunctions.SET_STEW_EFFECT;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.effects.stream().flatMap(p_298164_ -> p_298164_.duration().getReferencedContextParams().stream()).collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Called to perform the actual action of this function, after conditions have been checked.
     */
    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.is(Items.SUSPICIOUS_STEW) && !this.effects.isEmpty()) {
            SetStewEffectFunction.EffectEntry setsteweffectfunction$effectentry = Util.getRandom(this.effects, context.getRandom());
            Holder<MobEffect> holder = setsteweffectfunction$effectentry.effect();
            int i = setsteweffectfunction$effectentry.duration().getInt(context);
            if (!holder.value().isInstantenous()) {
                i *= 20;
            }

            SuspiciousStewEffects.Entry suspicioussteweffects$entry = new SuspiciousStewEffects.Entry(holder, i);
            stack.update(
                DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY, suspicioussteweffects$entry, SuspiciousStewEffects::withEffectAdded
            );
            return stack;
        } else {
            return stack;
        }
    }

    public static SetStewEffectFunction.Builder stewEffect() {
        return new SetStewEffectFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetStewEffectFunction.Builder> {
        private final ImmutableList.Builder<SetStewEffectFunction.EffectEntry> effects = ImmutableList.builder();

        protected SetStewEffectFunction.Builder getThis() {
            return this;
        }

        public SetStewEffectFunction.Builder withEffect(Holder<MobEffect> effect, NumberProvider amplifier) {
            this.effects.add(new SetStewEffectFunction.EffectEntry(effect, amplifier));
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetStewEffectFunction(this.getConditions(), this.effects.build());
        }
    }

    static record EffectEntry(Holder<MobEffect> effect, NumberProvider duration) {
        public static final Codec<SetStewEffectFunction.EffectEntry> CODEC = RecordCodecBuilder.create(
            p_348461_ -> p_348461_.group(
                        MobEffect.CODEC.fieldOf("type").forGetter(SetStewEffectFunction.EffectEntry::effect),
                        NumberProviders.CODEC.fieldOf("duration").forGetter(SetStewEffectFunction.EffectEntry::duration)
                    )
                    .apply(p_348461_, SetStewEffectFunction.EffectEntry::new)
        );
    }
}
