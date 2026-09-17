package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SculkChargePopParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected SculkChargePopParticle(
        ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprite
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.friction = 0.96F;
        this.sprites = sprite;
        this.scale(1.0F);
        this.hasPhysics = false;
        this.setSpriteFromAge(sprite);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @OnlyIn(Dist.CLIENT)
    public static record Provider(SpriteSet sprite) implements ParticleProvider<SimpleParticleType> {
        public Particle createParticle(
            SimpleParticleType p_233958_,
            ClientLevel p_233959_,
            double p_233960_,
            double p_233961_,
            double p_233962_,
            double p_233963_,
            double p_233964_,
            double p_233965_
        ) {
            SculkChargePopParticle sculkchargepopparticle = new SculkChargePopParticle(
                p_233959_, p_233960_, p_233961_, p_233962_, p_233963_, p_233964_, p_233965_, this.sprite
            );
            sculkchargepopparticle.setAlpha(1.0F);
            sculkchargepopparticle.setParticleSpeed(p_233963_, p_233964_, p_233965_);
            sculkchargepopparticle.setLifetime(p_233959_.random.nextInt(4) + 6);
            return sculkchargepopparticle;
        }
    }
}
