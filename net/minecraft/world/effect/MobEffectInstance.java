package net.minecraft.world.effect;

import com.google.common.collect.ComparisonChain;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

public class MobEffectInstance implements Comparable<MobEffectInstance> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;
    public static final Codec<MobEffectInstance> CODEC = RecordCodecBuilder.create(
        p_348152_ -> p_348152_.group(
                    MobEffect.CODEC.fieldOf("id").forGetter(MobEffectInstance::getEffect),
                    MobEffectInstance.Details.MAP_CODEC.forGetter(MobEffectInstance::asDetails)
                )
                .apply(p_348152_, MobEffectInstance::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance> STREAM_CODEC = StreamCodec.composite(
        MobEffect.STREAM_CODEC, MobEffectInstance::getEffect, MobEffectInstance.Details.STREAM_CODEC, MobEffectInstance::asDetails, MobEffectInstance::new
    );
    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    /**
     * A hidden effect which is not shown to the player.
     */
    @Nullable
    private MobEffectInstance hiddenEffect;
    private final MobEffectInstance.BlendState blendState = new MobEffectInstance.BlendState();

    public MobEffectInstance(Holder<MobEffect> effect) {
        this(effect, 0, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration) {
        this(effect, duration, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, true);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        this(effect, duration, amplifier, ambient, visible, visible);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) {
        this(effect, duration, amplifier, ambient, visible, showIcon, null);
    }

    public MobEffectInstance(
        Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon, @Nullable MobEffectInstance hiddenEffect
    ) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Mth.clamp(amplifier, 0, 255);
        this.ambient = ambient;
        this.visible = visible;
        this.showIcon = showIcon;
        this.hiddenEffect = hiddenEffect;
        this.effect.value().fillEffectCures(this.cures, this);
    }

    public MobEffectInstance(MobEffectInstance other) {
        this.effect = other.effect;
        this.setDetailsFrom(other);
    }

    private MobEffectInstance(Holder<MobEffect> effect, MobEffectInstance.Details details) {
        this(
            effect,
            details.duration(),
            details.amplifier(),
            details.ambient(),
            details.showParticles(),
            details.showIcon(),
            details.hiddenEffect().map(p_323227_ -> new MobEffectInstance(effect, p_323227_)).orElse(null)
        );
        this.cures.clear();
        details.cures().ifPresent(this.cures::addAll);
    }

    private MobEffectInstance.Details asDetails() {
        return new MobEffectInstance.Details(
            this.getAmplifier(),
            this.getDuration(),
            this.isAmbient(),
            this.isVisible(),
            this.showIcon(),
            Optional.ofNullable(this.hiddenEffect).map(MobEffectInstance::asDetails),
            Optional.of(this.getCures()).filter(cures -> !cures.isEmpty())
        );
    }

    public float getBlendFactor(LivingEntity entity, float delta) {
        return this.blendState.getFactor(entity, delta);
    }

    public ParticleOptions getParticleOptions() {
        return this.effect.value().createParticleOptions(this);
    }

    void setDetailsFrom(MobEffectInstance effectInstance) {
        this.duration = effectInstance.duration;
        this.amplifier = effectInstance.amplifier;
        this.ambient = effectInstance.ambient;
        this.visible = effectInstance.visible;
        this.showIcon = effectInstance.showIcon;
        this.cures.clear();
        this.cures.addAll(effectInstance.cures);
    }

    public boolean update(MobEffectInstance other) {
        if (!this.effect.equals(other.effect)) {
            LOGGER.warn("This method should only be called for matching effects!");
        }

        boolean flag = false;
        if (other.amplifier > this.amplifier) {
            if (other.isShorterDurationThan(this)) {
                MobEffectInstance mobeffectinstance = this.hiddenEffect;
                this.hiddenEffect = new MobEffectInstance(this);
                this.hiddenEffect.hiddenEffect = mobeffectinstance;
            }

            this.amplifier = other.amplifier;
            this.duration = other.duration;
            flag = true;
        } else if (this.isShorterDurationThan(other)) {
            if (other.amplifier == this.amplifier) {
                this.duration = other.duration;
                flag = true;
            } else if (this.hiddenEffect == null) {
                this.hiddenEffect = new MobEffectInstance(other);
            } else {
                this.hiddenEffect.update(other);
            }
        }

        if (!other.ambient && this.ambient || flag) {
            this.ambient = other.ambient;
            flag = true;
        }

        if (other.visible != this.visible) {
            this.visible = other.visible;
            flag = true;
        }

        if (other.showIcon != this.showIcon) {
            this.showIcon = other.showIcon;
            flag = true;
        }

        return flag;
    }

    private boolean isShorterDurationThan(MobEffectInstance other) {
        return !this.isInfiniteDuration() && (this.duration < other.duration || other.isInfiniteDuration());
    }

    public boolean isInfiniteDuration() {
        return this.duration == -1;
    }

    public boolean endsWithin(int duration) {
        return !this.isInfiniteDuration() && this.duration <= duration;
    }

    public int mapDuration(Int2IntFunction mapper) {
        return !this.isInfiniteDuration() && this.duration != 0 ? mapper.applyAsInt(this.duration) : this.duration;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public boolean isAmbient() {
        return this.ambient;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean showIcon() {
        return this.showIcon;
    }

    public boolean tick(LivingEntity entity, Runnable onExpirationRunnable) {
        if (this.hasRemainingDuration()) {
            int i = this.isInfiniteDuration() ? entity.tickCount : this.duration;
            if (this.effect.value().shouldApplyEffectTickThisTick(i, this.amplifier) && !this.effect.value().applyEffectTick(entity, this.amplifier)) {
                entity.removeEffect(this.effect);
            }

            this.tickDownDuration();
            if (this.duration == 0 && this.hiddenEffect != null) {
                this.setDetailsFrom(this.hiddenEffect);
                this.hiddenEffect = this.hiddenEffect.hiddenEffect;
                onExpirationRunnable.run();
            }
        }

        this.blendState.tick(this);
        return this.hasRemainingDuration();
    }

    private boolean hasRemainingDuration() {
        return this.isInfiniteDuration() || this.duration > 0;
    }

    private int tickDownDuration() {
        if (this.hiddenEffect != null) {
            this.hiddenEffect.tickDownDuration();
        }

        return this.duration = this.mapDuration(p_267916_ -> p_267916_ - 1);
    }

    public void onEffectStarted(LivingEntity entity) {
        this.effect.value().onEffectStarted(entity, this.amplifier);
    }

    public void onMobRemoved(LivingEntity livingEntity, Entity.RemovalReason reason) {
        this.effect.value().onMobRemoved(livingEntity, this.amplifier, reason);
    }

    public void onMobHurt(LivingEntity livingEntity, DamageSource damageSource, float amount) {
        this.effect.value().onMobHurt(livingEntity, this.amplifier, damageSource, amount);
    }

    public String getDescriptionId() {
        return this.effect.value().getDescriptionId();
    }

    @Override
    public String toString() {
        String s;
        if (this.amplifier > 0) {
            s = this.getDescriptionId() + " x " + (this.amplifier + 1) + ", Duration: " + this.describeDuration();
        } else {
            s = this.getDescriptionId() + ", Duration: " + this.describeDuration();
        }

        if (!this.visible) {
            s = s + ", Particles: false";
        }

        if (!this.showIcon) {
            s = s + ", Show Icon: false";
        }

        return s;
    }

    private String describeDuration() {
        return this.isInfiniteDuration() ? "infinite" : Integer.toString(this.duration);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof MobEffectInstance mobeffectinstance)
                ? false
                : this.duration == mobeffectinstance.duration
                    && this.amplifier == mobeffectinstance.amplifier
                    && this.ambient == mobeffectinstance.ambient
                    && this.visible == mobeffectinstance.visible
                    && this.showIcon == mobeffectinstance.showIcon
                    && this.effect.equals(mobeffectinstance.effect);
        }
    }

    @Override
    public int hashCode() {
        int i = this.effect.hashCode();
        i = 31 * i + this.duration;
        i = 31 * i + this.amplifier;
        i = 31 * i + (this.ambient ? 1 : 0);
        i = 31 * i + (this.visible ? 1 : 0);
        return 31 * i + (this.showIcon ? 1 : 0);
    }

    public Tag save() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    /**
     * Read a custom potion effect from a potion item's NBT data.
     */
    @Nullable
    public static MobEffectInstance load(CompoundTag nbt) {
        return CODEC.parse(NbtOps.INSTANCE, nbt).resultOrPartial(LOGGER::error).orElse(null);
    }

    public int compareTo(MobEffectInstance other) {
        int i = 32147;
        return (this.getDuration() <= 32147 || other.getDuration() <= 32147) && (!this.isAmbient() || !other.isAmbient())
            ? ComparisonChain.start()
                .compareFalseFirst(this.isAmbient(), other.isAmbient())
                .compareFalseFirst(this.isInfiniteDuration(), other.isInfiniteDuration())
                .compare(this.getDuration(), other.getDuration())
                .compare(this.getEffect().value().getSortOrder(this), other.getEffect().value().getSortOrder(other))
                .result()
            : ComparisonChain.start()
                .compare(this.isAmbient(), other.isAmbient())
                .compare(this.getEffect().value().getSortOrder(this), other.getEffect().value().getSortOrder(other))
                .result();
    }

    public void onEffectAdded(LivingEntity livingEntity) {
        this.effect.value().onEffectAdded(livingEntity, this.amplifier);
    }

    public boolean is(Holder<MobEffect> effect) {
        return this.effect.equals(effect);
    }

    public void copyBlendState(MobEffectInstance effectInstance) {
        this.blendState.copyFrom(effectInstance.blendState);
    }

    public void skipBlending() {
        this.blendState.setImmediate(this);
    }

    private final java.util.Set<net.neoforged.neoforge.common.EffectCure> cures = com.google.common.collect.Sets.newIdentityHashSet();

    /**
     * {@return the {@link net.neoforged.neoforge.common.EffectCure}s which can cure the {@link MobEffect} held by this {@link MobEffectInstance}}
     */
    public java.util.Set<net.neoforged.neoforge.common.EffectCure> getCures() {
        return cures;
    }

    static class BlendState {
        private float factor;
        private float factorPreviousFrame;

        public void setImmediate(MobEffectInstance effectInstance) {
            this.factor = computeTarget(effectInstance);
            this.factorPreviousFrame = this.factor;
        }

        public void copyFrom(MobEffectInstance.BlendState blendState) {
            this.factor = blendState.factor;
            this.factorPreviousFrame = blendState.factorPreviousFrame;
        }

        public void tick(MobEffectInstance effect) {
            this.factorPreviousFrame = this.factor;
            int i = getBlendDuration(effect);
            if (i == 0) {
                this.factor = 1.0F;
            } else {
                float f = computeTarget(effect);
                if (this.factor != f) {
                    float f1 = 1.0F / (float)i;
                    this.factor = this.factor + Mth.clamp(f - this.factor, -f1, f1);
                }
            }
        }

        private static float computeTarget(MobEffectInstance effect) {
            boolean flag = !effect.endsWithin(getBlendDuration(effect));
            return flag ? 1.0F : 0.0F;
        }

        private static int getBlendDuration(MobEffectInstance effect) {
            return effect.getEffect().value().getBlendDurationTicks();
        }

        public float getFactor(LivingEntity entity, float delta) {
            if (entity.isRemoved()) {
                this.factorPreviousFrame = this.factor;
            }

            return Mth.lerp(delta, this.factorPreviousFrame, this.factor);
        }
    }

    static record Details(
        int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect, Optional<java.util.Set<net.neoforged.neoforge.common.EffectCure>> cures) {
        public static final MapCodec<MobEffectInstance.Details> MAP_CODEC = MapCodec.recursive(
            "MobEffectInstance.Details",
            p_323465_ -> RecordCodecBuilder.mapCodec(
                    p_324063_ -> p_324063_.group(
                                ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("amplifier", 0).forGetter(MobEffectInstance.Details::amplifier),
                                Codec.INT.optionalFieldOf("duration", Integer.valueOf(0)).forGetter(MobEffectInstance.Details::duration),
                                Codec.BOOL.optionalFieldOf("ambient", Boolean.valueOf(false)).forGetter(MobEffectInstance.Details::ambient),
                                Codec.BOOL.optionalFieldOf("show_particles", Boolean.valueOf(true)).forGetter(MobEffectInstance.Details::showParticles),
                                Codec.BOOL.optionalFieldOf("show_icon").forGetter(p_323788_ -> Optional.of(p_323788_.showIcon())),
                                p_323465_.optionalFieldOf("hidden_effect").forGetter(MobEffectInstance.Details::hiddenEffect)
                                // Neo: Add additional serialization logic for custom EffectCure(s)
                                , net.neoforged.neoforge.common.util.NeoForgeExtraCodecs.setOf(net.neoforged.neoforge.common.EffectCure.CODEC).optionalFieldOf("neoforge:cures").forGetter(MobEffectInstance.Details::cures)
                            )
                            .apply(p_324063_, MobEffectInstance.Details::create)
                )
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance.Details> STREAM_CODEC = StreamCodec.recursive(
            p_329990_ -> net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.composite(
                    ByteBufCodecs.VAR_INT,
                    MobEffectInstance.Details::amplifier,
                    ByteBufCodecs.VAR_INT,
                    MobEffectInstance.Details::duration,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::ambient,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::showParticles,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::showIcon,
                    p_329990_.apply(ByteBufCodecs::optional),
                    MobEffectInstance.Details::hiddenEffect,
                    // Neo: Add additional serialization logic for custom EffectCure(s)
                    net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.connectionAware(
                            ByteBufCodecs.optional(net.neoforged.neoforge.common.EffectCure.STREAM_CODEC.apply(ByteBufCodecs.collection(java.util.HashSet::new))),
                            net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs.uncheckedUnit(Optional.empty())
                    ),
                    MobEffectInstance.Details::cures,
                    MobEffectInstance.Details::new
                )
        );

        private static MobEffectInstance.Details create(
            int amplifier, int duration, boolean ambient, boolean showParticles, Optional<Boolean> showIcon, Optional<MobEffectInstance.Details> hiddenEffect
        ) {
            return new MobEffectInstance.Details(amplifier, duration, ambient, showParticles, showIcon.orElse(showParticles), hiddenEffect);
        }

        private static MobEffectInstance.Details create(
            int amplifier, int duration, boolean ambient, boolean showParticles, Optional<Boolean> showIcon, Optional<MobEffectInstance.Details> hiddenEffect, Optional<java.util.Set<net.neoforged.neoforge.common.EffectCure>> cures
        ) {
            return new MobEffectInstance.Details(amplifier, duration, ambient, showParticles, showIcon.orElse(showParticles), hiddenEffect, cures);
        }

        @Deprecated
        Details(int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect) {
            this(amplifier, duration, ambient, showParticles, showIcon, hiddenEffect, Optional.empty());
        }

    }
}
