package net.minecraft.world.effect;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class MobEffect implements FeatureElement, net.neoforged.neoforge.common.extensions.IMobEffectExtension {
    public static final Codec<Holder<MobEffect>> CODEC = BuiltInRegistries.MOB_EFFECT.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<MobEffect>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT);
    private static final int AMBIENT_ALPHA = Mth.floor(38.25F);
    /**
     * Contains a Map of the AttributeModifiers registered by potions
     */
    private final Map<Holder<Attribute>, MobEffect.AttributeTemplate> attributeModifiers = new Object2ObjectOpenHashMap<>();
    private final MobEffectCategory category;
    private final int color;
    private final Function<MobEffectInstance, ParticleOptions> particleFactory;
    @Nullable
    private String descriptionId;
    private int blendDurationTicks;
    private Optional<SoundEvent> soundOnAdded = Optional.empty();
    private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

    protected MobEffect(MobEffectCategory category, int color) {
        this.category = category;
        this.color = color;
        this.particleFactory = p_333517_ -> {
            int i = p_333517_.isAmbient() ? AMBIENT_ALPHA : 255;
            return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, FastColor.ARGB32.color(i, color));
        };
    }

    protected MobEffect(MobEffectCategory category, int color, ParticleOptions particle) {
        this.category = category;
        this.color = color;
        this.particleFactory = p_333515_ -> particle;
    }

    public int getBlendDurationTicks() {
        return this.blendDurationTicks;
    }

    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        return true;
    }

    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity indirectSource, LivingEntity livingEntity, int amplifier, double health) {
        this.applyEffectTick(livingEntity, amplifier);
    }

    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    public void onEffectStarted(LivingEntity livingEntity, int amplifier) {
    }

    public void onEffectAdded(LivingEntity livingEntity, int amplifier) {
        this.soundOnAdded
            .ifPresent(
                p_352700_ -> livingEntity.level()
                        .playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(), p_352700_, livingEntity.getSoundSource(), 1.0F, 1.0F)
            );
    }

    public void onMobRemoved(LivingEntity livingEntity, int amplifier, Entity.RemovalReason reason) {
    }

    public void onMobHurt(LivingEntity livingEntity, int amplifier, DamageSource damageSource, float amount) {
    }

    public boolean isInstantenous() {
        return false;
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public Component getDisplayName() {
        return Component.translatable(this.getDescriptionId());
    }

    public MobEffectCategory getCategory() {
        return this.category;
    }

    public int getColor() {
        return this.color;
    }

    public MobEffect addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        this.attributeModifiers.put(attribute, new MobEffect.AttributeTemplate(id, amount, operation));
        return this;
    }

    /**
     * Neo: attribute template with custom level curve, for mob effects providing non-linear attribute modifiers.
     * @param attribute The attribute of the modifier
     * @param id ID of the modifier
     * @param operation Operation of the modifier
     * @param curve A function mapping effect instance amplifier to modifier amount
     */
    public MobEffect addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id, AttributeModifier.Operation operation, it.unimi.dsi.fastutil.ints.Int2DoubleFunction curve) {
        this.attributeModifiers.put(attribute, new MobEffect.AttributeTemplate(id, curve.apply(0), operation, curve));
        return this;
    }

    public MobEffect setBlendDuration(int blendDuration) {
        this.blendDurationTicks = blendDuration;
        return this;
    }

    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> output) {
        this.attributeModifiers.forEach((p_349971_, p_349972_) -> output.accept((Holder<Attribute>)p_349971_, p_349972_.create(amplifier)));
    }

    public void removeAttributeModifiers(AttributeMap attributeMap) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeinstance = attributeMap.getInstance(entry.getKey());
            if (attributeinstance != null) {
                attributeinstance.removeModifier(entry.getValue().id());
            }
        }
    }

    public void addAttributeModifiers(AttributeMap attributeMap, int amplifier) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeinstance = attributeMap.getInstance(entry.getKey());
            if (attributeinstance != null) {
                attributeinstance.removeModifier(entry.getValue().id());
                attributeinstance.addPermanentModifier(entry.getValue().create(amplifier));
            }
        }
    }

    public boolean isBeneficial() {
        return this.category == MobEffectCategory.BENEFICIAL;
    }

    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return this.particleFactory.apply(effect);
    }

    public MobEffect withSoundOnAdded(SoundEvent sound) {
        this.soundOnAdded = Optional.of(sound);
        return this;
    }

    public MobEffect requiredFeatures(FeatureFlag... requiredFeatures) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    /**
     * Neo: Allowing mods to define client behavior for their MobEffects
     * @deprecated Use {@link net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent} instead
     */
    @Deprecated(forRemoval = true, since = "1.21")
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
    }

    public static record AttributeTemplate(ResourceLocation id, double amount, AttributeModifier.Operation operation, @Nullable it.unimi.dsi.fastutil.ints.Int2DoubleFunction curve) {

        public AttributeTemplate(ResourceLocation id, double amount, AttributeModifier.Operation operation) {
            this(id, amount, operation, null);
        }

        public AttributeModifier create(int level) {
            if (curve != null) { // Neo: Use the custom attribute value curve if one is present
                return new AttributeModifier(this.id, this.curve.apply(level), this.operation);
            }
            return new AttributeModifier(this.id, this.amount * (double)(level + 1), this.operation);
        }
    }
}
