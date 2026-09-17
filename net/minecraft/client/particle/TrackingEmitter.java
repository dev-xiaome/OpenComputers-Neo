package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TrackingEmitter extends NoRenderParticle {
    private final Entity entity;
    private int life;
    private final int lifeTime;
    private final ParticleOptions particleType;

    public TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType) {
        this(level, entity, particleType, 3);
    }

    public TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType, int lifetime) {
        this(level, entity, particleType, lifetime, entity.getDeltaMovement());
    }

    private TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType, int lifetime, Vec3 speedVector) {
        super(level, entity.getX(), entity.getY(0.5), entity.getZ(), speedVector.x, speedVector.y, speedVector.z);
        this.entity = entity;
        this.lifeTime = lifetime;
        this.particleType = particleType;
        this.tick();
    }

    @Override
    public void tick() {
        for (int i = 0; i < 16; i++) {
            double d0 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
            double d1 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
            double d2 = (double)(this.random.nextFloat() * 2.0F - 1.0F);
            if (!(d0 * d0 + d1 * d1 + d2 * d2 > 1.0)) {
                double d3 = this.entity.getX(d0 / 4.0);
                double d4 = this.entity.getY(0.5 + d1 / 4.0);
                double d5 = this.entity.getZ(d2 / 4.0);
                this.level.addParticle(this.particleType, false, d3, d4, d5, d0, d1 + 0.2, d2);
            }
        }

        this.life++;
        if (this.life >= this.lifeTime) {
            this.remove();
        }
    }
}
