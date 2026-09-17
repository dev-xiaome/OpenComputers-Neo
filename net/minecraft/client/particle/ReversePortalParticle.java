package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ReversePortalParticle extends PortalParticle {
    protected ReversePortalParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.quadSize *= 1.5F;
        this.lifetime = (int)(Math.random() * 2.0) + 60;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        float f = 1.0F - ((float)this.age + scaleFactor) / ((float)this.lifetime * 1.5F);
        return this.quadSize * f;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            float f = (float)this.age / (float)this.lifetime;
            this.x = this.x + this.xd * (double)f;
            this.y = this.y + this.yd * (double)f;
            this.z = this.z + this.zd * (double)f;
            this.setPos(this.x, this.y, this.z); // Neo: update the particle's bounding box
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ReversePortalProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ReversePortalProvider(SpriteSet sprites) {
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
            ReversePortalParticle reverseportalparticle = new ReversePortalParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            reverseportalparticle.pickSprite(this.sprite);
            return reverseportalparticle;
        }
    }
}
