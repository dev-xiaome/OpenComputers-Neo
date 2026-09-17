package net.minecraft.client.particle;

import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ParticleProvider<T extends ParticleOptions> {
    @Nullable
    Particle createParticle(
        T type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    );

    @OnlyIn(Dist.CLIENT)
    public interface Sprite<T extends ParticleOptions> {
        @Nullable
        TextureSheetParticle createParticle(
            T type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
        );
    }
}
