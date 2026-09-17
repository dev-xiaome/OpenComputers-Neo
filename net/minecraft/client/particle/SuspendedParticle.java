package net.minecraft.client.particle;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SuspendedParticle extends TextureSheetParticle {
    protected SuspendedParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, x, y - 0.125, z);
        this.setSize(0.01F, 0.01F);
        this.pickSprite(sprites);
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.2F);
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.gravity = 0.0F;
    }

    SuspendedParticle(
        ClientLevel level, SpriteSet sprites, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        super(level, x, y - 0.125, z, xSpeed, ySpeed, zSpeed);
        this.setSize(0.01F, 0.01F);
        this.pickSprite(sprites);
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.6F);
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.gravity = 0.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @OnlyIn(Dist.CLIENT)
    public static class CrimsonSporeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public CrimsonSporeProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
        ) {
            RandomSource randomsource = level.random;
            double d0 = randomsource.nextGaussian() * 1.0E-6F;
            double d1 = randomsource.nextGaussian() * 1.0E-4F;
            double d2 = randomsource.nextGaussian() * 1.0E-6F;
            SuspendedParticle suspendedparticle = new SuspendedParticle(level, this.sprite, x, y, z, d0, d1, d2);
            suspendedparticle.setColor(0.9F, 0.4F, 0.5F);
            return suspendedparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SporeBlossomAirProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public SporeBlossomAirProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel p_level,
            double p_x,
            double p_y,
            double p_z,
            double xSpeed,
            double ySpeed,
            double zSpeed
        ) {
            SuspendedParticle suspendedparticle = new SuspendedParticle(p_level, this.sprite, p_x, p_y, p_z, 0.0, -0.8F, 0.0) {
                @Override
                public Optional<ParticleGroup> getParticleGroup() {
                    return Optional.of(ParticleGroup.SPORE_BLOSSOM);
                }
            };
            suspendedparticle.lifetime = Mth.randomBetweenInclusive(p_level.random, 500, 1000);
            suspendedparticle.gravity = 0.01F;
            suspendedparticle.setColor(0.32F, 0.5F, 0.22F);
            return suspendedparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class UnderwaterProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public UnderwaterProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
        ) {
            SuspendedParticle suspendedparticle = new SuspendedParticle(level, this.sprite, x, y, z);
            suspendedparticle.setColor(0.4F, 0.4F, 0.7F);
            return suspendedparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WarpedSporeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public WarpedSporeProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed
        ) {
            double d0 = (double)level.random.nextFloat() * -1.9 * (double)level.random.nextFloat() * 0.1;
            SuspendedParticle suspendedparticle = new SuspendedParticle(level, this.sprite, x, y, z, 0.0, d0, 0.0);
            suspendedparticle.setColor(0.1F, 0.1F, 0.3F);
            suspendedparticle.setSize(0.001F, 0.001F);
            return suspendedparticle;
        }
    }
}
