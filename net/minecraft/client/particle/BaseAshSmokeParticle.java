package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BaseAshSmokeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected BaseAshSmokeParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        float xSeedMultiplier,
        float ySpeedMultiplier,
        float zSpeedMultiplier,
        double xSpeed,
        double ySpeed,
        double zSpeed,
        float quadSizeMultiplier,
        SpriteSet sprites,
        float rColMultiplier,
        int lifetime,
        float gravity,
        boolean hasPhysics
    ) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.friction = 0.96F;
        this.gravity = gravity;
        this.speedUpWhenYMotionIsBlocked = true;
        this.sprites = sprites;
        this.xd *= (double)xSeedMultiplier;
        this.yd *= (double)ySpeedMultiplier;
        this.zd *= (double)zSpeedMultiplier;
        this.xd += xSpeed;
        this.yd += ySpeed;
        this.zd += zSpeed;
        float f = level.random.nextFloat() * rColMultiplier;
        this.rCol = f;
        this.gCol = f;
        this.bCol = f;
        this.quadSize *= 0.75F * quadSizeMultiplier;
        this.lifetime = (int)((double)lifetime / ((double)level.random.nextFloat() * 0.8 + 0.2) * (double)quadSizeMultiplier);
        this.lifetime = Math.max(this.lifetime, 1);
        this.setSpriteFromAge(sprites);
        this.hasPhysics = hasPhysics;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp(((float)this.age + scaleFactor) / (float)this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }
}
