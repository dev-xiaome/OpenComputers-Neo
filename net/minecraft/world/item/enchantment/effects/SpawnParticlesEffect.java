package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public record SpawnParticlesEffect(
    ParticleOptions particle,
    SpawnParticlesEffect.PositionSource horizontalPosition,
    SpawnParticlesEffect.PositionSource verticalPosition,
    SpawnParticlesEffect.VelocitySource horizontalVelocity,
    SpawnParticlesEffect.VelocitySource verticalVelocity,
    FloatProvider speed
) implements EnchantmentEntityEffect {
    public static final MapCodec<SpawnParticlesEffect> CODEC = RecordCodecBuilder.mapCodec(
        p_345987_ -> p_345987_.group(
                    ParticleTypes.CODEC.fieldOf("particle").forGetter(SpawnParticlesEffect::particle),
                    SpawnParticlesEffect.PositionSource.CODEC.fieldOf("horizontal_position").forGetter(SpawnParticlesEffect::horizontalPosition),
                    SpawnParticlesEffect.PositionSource.CODEC.fieldOf("vertical_position").forGetter(SpawnParticlesEffect::verticalPosition),
                    SpawnParticlesEffect.VelocitySource.CODEC.fieldOf("horizontal_velocity").forGetter(SpawnParticlesEffect::horizontalVelocity),
                    SpawnParticlesEffect.VelocitySource.CODEC.fieldOf("vertical_velocity").forGetter(SpawnParticlesEffect::verticalVelocity),
                    FloatProvider.CODEC.optionalFieldOf("speed", ConstantFloat.ZERO).forGetter(SpawnParticlesEffect::speed)
                )
                .apply(p_345987_, SpawnParticlesEffect::new)
    );

    public static SpawnParticlesEffect.PositionSource offsetFromEntityPosition(float offset) {
        return new SpawnParticlesEffect.PositionSource(SpawnParticlesEffect.PositionSourceType.ENTITY_POSITION, offset, 1.0F);
    }

    public static SpawnParticlesEffect.PositionSource inBoundingBox() {
        return new SpawnParticlesEffect.PositionSource(SpawnParticlesEffect.PositionSourceType.BOUNDING_BOX, 0.0F, 1.0F);
    }

    public static SpawnParticlesEffect.VelocitySource movementScaled(float movementScale) {
        return new SpawnParticlesEffect.VelocitySource(movementScale, ConstantFloat.ZERO);
    }

    public static SpawnParticlesEffect.VelocitySource fixedVelocity(FloatProvider velocity) {
        return new SpawnParticlesEffect.VelocitySource(0.0F, velocity);
    }

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        RandomSource randomsource = entity.getRandom();
        Vec3 vec3 = entity.getKnownMovement();
        float f = entity.getBbWidth();
        float f1 = entity.getBbHeight();
        level.sendParticles(
            this.particle,
            this.horizontalPosition.getCoordinate(origin.x(), origin.x(), f, randomsource),
            this.verticalPosition.getCoordinate(origin.y(), origin.y() + (double)(f1 / 2.0F), f1, randomsource),
            this.horizontalPosition.getCoordinate(origin.z(), origin.z(), f, randomsource),
            0,
            this.horizontalVelocity.getVelocity(vec3.x(), randomsource),
            this.verticalVelocity.getVelocity(vec3.y(), randomsource),
            this.horizontalVelocity.getVelocity(vec3.z(), randomsource),
            (double)this.speed.sample(randomsource)
        );
    }

    @Override
    public MapCodec<SpawnParticlesEffect> codec() {
        return CODEC;
    }

    public static record PositionSource(SpawnParticlesEffect.PositionSourceType type, float offset, float scale) {
        public static final MapCodec<SpawnParticlesEffect.PositionSource> CODEC = RecordCodecBuilder.<SpawnParticlesEffect.PositionSource>mapCodec(
                p_345074_ -> p_345074_.group(
                            SpawnParticlesEffect.PositionSourceType.CODEC.fieldOf("type").forGetter(SpawnParticlesEffect.PositionSource::type),
                            Codec.FLOAT.optionalFieldOf("offset", Float.valueOf(0.0F)).forGetter(SpawnParticlesEffect.PositionSource::offset),
                            ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("scale", 1.0F).forGetter(SpawnParticlesEffect.PositionSource::scale)
                        )
                        .apply(p_345074_, SpawnParticlesEffect.PositionSource::new)
            )
            .validate(
                p_345424_ -> p_345424_.type() == SpawnParticlesEffect.PositionSourceType.ENTITY_POSITION && p_345424_.scale() != 1.0F
                        ? DataResult.error(() -> "Cannot scale an entity position coordinate source")
                        : DataResult.success(p_345424_)
            );

        public double getCoordinate(double position, double center, float size, RandomSource random) {
            return this.type.getCoordinate(position, center, size * this.scale, random) + (double)this.offset;
        }
    }

    public static enum PositionSourceType implements StringRepresentable {
        ENTITY_POSITION("entity_position", (p_344963_, p_352938_, p_346310_, p_345258_) -> p_344963_),
        BOUNDING_BOX("in_bounding_box", (p_345669_, p_352951_, p_345281_, p_345162_) -> p_352951_ + (p_345162_.nextDouble() - 0.5) * (double)p_345281_);

        public static final Codec<SpawnParticlesEffect.PositionSourceType> CODEC = StringRepresentable.fromEnum(SpawnParticlesEffect.PositionSourceType::values);
        private final String id;
        private final SpawnParticlesEffect.PositionSourceType.CoordinateSource source;

        private PositionSourceType(String id, SpawnParticlesEffect.PositionSourceType.CoordinateSource source) {
            this.id = id;
            this.source = source;
        }

        public double getCoordinate(double position, double center, float size, RandomSource random) {
            return this.source.getCoordinate(position, center, size, random);
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }

        @FunctionalInterface
        interface CoordinateSource {
            double getCoordinate(double position, double center, float size, RandomSource random);
        }
    }

    public static record VelocitySource(float movementScale, FloatProvider base) {
        public static final MapCodec<SpawnParticlesEffect.VelocitySource> CODEC = RecordCodecBuilder.mapCodec(
            p_346005_ -> p_346005_.group(
                        Codec.FLOAT.optionalFieldOf("movement_scale", Float.valueOf(0.0F)).forGetter(SpawnParticlesEffect.VelocitySource::movementScale),
                        FloatProvider.CODEC.optionalFieldOf("base", ConstantFloat.ZERO).forGetter(SpawnParticlesEffect.VelocitySource::base)
                    )
                    .apply(p_346005_, SpawnParticlesEffect.VelocitySource::new)
        );

        public double getVelocity(double scale, RandomSource random) {
            return scale * (double)this.movementScale + (double)this.base.sample(random);
        }
    }
}
