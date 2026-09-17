package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GlowParticle extends TextureSheetParticle {
    static final RandomSource RANDOM = RandomSource.create();
    private final SpriteSet sprites;

    protected GlowParticle(
        ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.friction = 0.96F;
        this.speedUpWhenYMotionIsBlocked = true;
        this.sprites = sprites;
        this.quadSize *= 0.75F;
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        float f = ((float)this.age + partialTick) / (float)this.lifetime;
        f = Mth.clamp(f, 0.0F, 1.0F);
        int i = super.getLightColor(partialTick);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        j += (int)(f * 15.0F * 16.0F);
        if (j > 240) {
            j = 240;
        }

        return j | k << 16;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @OnlyIn(Dist.CLIENT)
    public static class ElectricSparkProvider implements ParticleProvider<SimpleParticleType> {
        private final double SPEED_FACTOR = 0.25;
        private final SpriteSet sprite;

        public ElectricSparkProvider(SpriteSet sprites) {
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
            GlowParticle glowparticle = new GlowParticle(level, x, y, z, 0.0, 0.0, 0.0, this.sprite);
            glowparticle.setColor(1.0F, 0.9F, 1.0F);
            glowparticle.setParticleSpeed(xSpeed * 0.25, ySpeed * 0.25, zSpeed * 0.25);
            int i = 2;
            int j = 4;
            glowparticle.setLifetime(level.random.nextInt(2) + 2);
            return glowparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class GlowSquidProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public GlowSquidProvider(SpriteSet sprites) {
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
            GlowParticle glowparticle = new GlowParticle(
                level,
                x,
                y,
                z,
                0.5 - GlowParticle.RANDOM.nextDouble(),
                ySpeed,
                0.5 - GlowParticle.RANDOM.nextDouble(),
                this.sprite
            );
            if (level.random.nextBoolean()) {
                glowparticle.setColor(0.6F, 1.0F, 0.8F);
            } else {
                glowparticle.setColor(0.08F, 0.4F, 0.4F);
            }

            glowparticle.yd *= 0.2F;
            if (xSpeed == 0.0 && zSpeed == 0.0) {
                glowparticle.xd *= 0.1F;
                glowparticle.zd *= 0.1F;
            }

            glowparticle.setLifetime((int)(8.0 / (level.random.nextDouble() * 0.8 + 0.2)));
            return glowparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ScrapeProvider implements ParticleProvider<SimpleParticleType> {
        private final double SPEED_FACTOR = 0.01;
        private final SpriteSet sprite;

        public ScrapeProvider(SpriteSet sprites) {
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
            GlowParticle glowparticle = new GlowParticle(level, x, y, z, 0.0, 0.0, 0.0, this.sprite);
            if (level.random.nextBoolean()) {
                glowparticle.setColor(0.29F, 0.58F, 0.51F);
            } else {
                glowparticle.setColor(0.43F, 0.77F, 0.62F);
            }

            glowparticle.setParticleSpeed(xSpeed * 0.01, ySpeed * 0.01, zSpeed * 0.01);
            int i = 10;
            int j = 40;
            glowparticle.setLifetime(level.random.nextInt(30) + 10);
            return glowparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WaxOffProvider implements ParticleProvider<SimpleParticleType> {
        private final double SPEED_FACTOR = 0.01;
        private final SpriteSet sprite;

        public WaxOffProvider(SpriteSet sprites) {
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
            GlowParticle glowparticle = new GlowParticle(level, x, y, z, 0.0, 0.0, 0.0, this.sprite);
            glowparticle.setColor(1.0F, 0.9F, 1.0F);
            glowparticle.setParticleSpeed(xSpeed * 0.01 / 2.0, ySpeed * 0.01, zSpeed * 0.01 / 2.0);
            int i = 10;
            int j = 40;
            glowparticle.setLifetime(level.random.nextInt(30) + 10);
            return glowparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WaxOnProvider implements ParticleProvider<SimpleParticleType> {
        private final double SPEED_FACTOR = 0.01;
        private final SpriteSet sprite;

        public WaxOnProvider(SpriteSet sprites) {
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
            GlowParticle glowparticle = new GlowParticle(level, x, y, z, 0.0, 0.0, 0.0, this.sprite);
            glowparticle.setColor(0.91F, 0.55F, 0.08F);
            glowparticle.setParticleSpeed(xSpeed * 0.01 / 2.0, ySpeed * 0.01, zSpeed * 0.01 / 2.0);
            int i = 10;
            int j = 40;
            glowparticle.setLifetime(level.random.nextInt(30) + 10);
            return glowparticle;
        }
    }
}
