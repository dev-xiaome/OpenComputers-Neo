package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SculkChargeParticleOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SculkChargeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected SculkChargeParticle(
        ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, SpriteSet sprite
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.friction = 0.96F;
        this.sprites = sprite;
        this.scale(1.5F);
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
    public static record Provider(SpriteSet sprite) implements ParticleProvider<SculkChargeParticleOptions> {
        public Particle createParticle(
            SculkChargeParticleOptions p_233918_,
            ClientLevel p_233919_,
            double p_233920_,
            double p_233921_,
            double p_233922_,
            double p_233923_,
            double p_233924_,
            double p_233925_
        ) {
            SculkChargeParticle sculkchargeparticle = new SculkChargeParticle(
                p_233919_, p_233920_, p_233921_, p_233922_, p_233923_, p_233924_, p_233925_, this.sprite
            );
            sculkchargeparticle.setAlpha(1.0F);
            sculkchargeparticle.setParticleSpeed(p_233923_, p_233924_, p_233925_);
            sculkchargeparticle.oRoll = p_233918_.roll();
            sculkchargeparticle.roll = p_233918_.roll();
            sculkchargeparticle.setLifetime(p_233919_.random.nextInt(12) + 8);
            return sculkchargeparticle;
        }
    }
}
