package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SuspendedTownParticle extends TextureSheetParticle {
    protected SuspendedTownParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        float f = this.random.nextFloat() * 0.1F + 0.2F;
        this.rCol = f;
        this.gCol = f;
        this.bCol = f;
        this.setSize(0.02F, 0.02F);
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.5F);
        this.xd *= 0.02F;
        this.yd *= 0.02F;
        this.zd *= 0.02F;
        this.lifetime = (int)(20.0 / (Math.random() * 0.8 + 0.2));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void move(double x, double y, double z) {
        this.setBoundingBox(this.getBoundingBox().move(x, y, z));
        this.setLocationFromBoundingbox();
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0) {
            this.remove();
        } else {
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.99;
            this.yd *= 0.99;
            this.zd *= 0.99;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ComposterFillProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ComposterFillProvider(SpriteSet sprites) {
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
            SuspendedTownParticle suspendedtownparticle = new SuspendedTownParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            suspendedtownparticle.pickSprite(this.sprite);
            suspendedtownparticle.setColor(1.0F, 1.0F, 1.0F);
            suspendedtownparticle.setLifetime(3 + level.getRandom().nextInt(5));
            return suspendedtownparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class DolphinSpeedProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public DolphinSpeedProvider(SpriteSet sprites) {
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
            SuspendedTownParticle suspendedtownparticle = new SuspendedTownParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            suspendedtownparticle.setColor(0.3F, 0.5F, 1.0F);
            suspendedtownparticle.pickSprite(this.sprite);
            suspendedtownparticle.setAlpha(1.0F - level.random.nextFloat() * 0.7F);
            suspendedtownparticle.setLifetime(suspendedtownparticle.getLifetime() / 2);
            return suspendedtownparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class EggCrackProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public EggCrackProvider(SpriteSet sprite) {
            this.sprite = sprite;
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
            SuspendedTownParticle suspendedtownparticle = new SuspendedTownParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            suspendedtownparticle.pickSprite(this.sprite);
            suspendedtownparticle.setColor(1.0F, 1.0F, 1.0F);
            return suspendedtownparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class HappyVillagerProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public HappyVillagerProvider(SpriteSet sprites) {
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
            SuspendedTownParticle suspendedtownparticle = new SuspendedTownParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            suspendedtownparticle.pickSprite(this.sprite);
            suspendedtownparticle.setColor(1.0F, 1.0F, 1.0F);
            return suspendedtownparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprites) {
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
            SuspendedTownParticle suspendedtownparticle = new SuspendedTownParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            suspendedtownparticle.pickSprite(this.sprite);
            return suspendedtownparticle;
        }
    }
}
