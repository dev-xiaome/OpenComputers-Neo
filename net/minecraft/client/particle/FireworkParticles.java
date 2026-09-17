package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FireworkParticles {
    @OnlyIn(Dist.CLIENT)
    public static class FlashProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public FlashProvider(SpriteSet sprites) {
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
            FireworkParticles.OverlayParticle fireworkparticles$overlayparticle = new FireworkParticles.OverlayParticle(
                level, x, y, z
            );
            fireworkparticles$overlayparticle.pickSprite(this.sprite);
            return fireworkparticles$overlayparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class OverlayParticle extends TextureSheetParticle {
        protected OverlayParticle(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
            this.lifetime = 4;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
            this.setAlpha(0.6F - ((float)this.age + partialTicks - 1.0F) * 0.25F * 0.5F);
            super.render(buffer, renderInfo, partialTicks);
        }

        @Override
        public float getQuadSize(float scaleFactor) {
            return 7.1F * Mth.sin(((float)this.age + scaleFactor - 1.0F) * 0.25F * (float) Math.PI);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class SparkParticle extends SimpleAnimatedParticle {
        private boolean trail;
        private boolean twinkle;
        private final ParticleEngine engine;
        private float fadeR;
        private float fadeG;
        private float fadeB;
        private boolean hasFade;

        protected SparkParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            ParticleEngine engine,
            SpriteSet sprites
        ) {
            super(level, x, y, z, sprites, 0.1F);
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
            this.engine = engine;
            this.quadSize *= 0.75F;
            this.lifetime = 48 + this.random.nextInt(12);
            this.setSpriteFromAge(sprites);
        }

        public void setTrail(boolean trail) {
            this.trail = trail;
        }

        public void setTwinkle(boolean twinkle) {
            this.twinkle = twinkle;
        }

        @Override
        public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
            if (!this.twinkle || this.age < this.lifetime / 3 || (this.age + this.lifetime) / 3 % 2 == 0) {
                super.render(buffer, renderInfo, partialTicks);
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (this.trail && this.age < this.lifetime / 2 && (this.age + this.lifetime) % 2 == 0) {
                FireworkParticles.SparkParticle fireworkparticles$sparkparticle = new FireworkParticles.SparkParticle(
                    this.level, this.x, this.y, this.z, 0.0, 0.0, 0.0, this.engine, this.sprites
                );
                fireworkparticles$sparkparticle.setAlpha(0.99F);
                fireworkparticles$sparkparticle.setColor(this.rCol, this.gCol, this.bCol);
                fireworkparticles$sparkparticle.age = fireworkparticles$sparkparticle.lifetime / 2;
                if (this.hasFade) {
                    fireworkparticles$sparkparticle.hasFade = true;
                    fireworkparticles$sparkparticle.fadeR = this.fadeR;
                    fireworkparticles$sparkparticle.fadeG = this.fadeG;
                    fireworkparticles$sparkparticle.fadeB = this.fadeB;
                }

                fireworkparticles$sparkparticle.twinkle = this.twinkle;
                this.engine.add(fireworkparticles$sparkparticle);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SparkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SparkProvider(SpriteSet sprites) {
            this.sprites = sprites;
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
            FireworkParticles.SparkParticle fireworkparticles$sparkparticle = new FireworkParticles.SparkParticle(
                level, x, y, z, xSpeed, ySpeed, zSpeed, Minecraft.getInstance().particleEngine, this.sprites
            );
            fireworkparticles$sparkparticle.setAlpha(0.99F);
            return fireworkparticles$sparkparticle;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Starter extends NoRenderParticle {
        private static final double[][] CREEPER_PARTICLE_COORDS = new double[][]{
            {0.0, 0.2}, {0.2, 0.2}, {0.2, 0.6}, {0.6, 0.6}, {0.6, 0.2}, {0.2, 0.2}, {0.2, 0.0}, {0.4, 0.0}, {0.4, -0.6}, {0.2, -0.6}, {0.2, -0.4}, {0.0, -0.4}
        };
        private static final double[][] STAR_PARTICLE_COORDS = new double[][]{
            {0.0, 1.0},
            {0.3455, 0.309},
            {0.9511, 0.309},
            {0.3795918367346939, -0.12653061224489795},
            {0.6122448979591837, -0.8040816326530612},
            {0.0, -0.35918367346938773}
        };
        private int life;
        private final ParticleEngine engine;
        private final List<FireworkExplosion> explosions;
        private boolean twinkleDelay;

        public Starter(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            ParticleEngine engine,
            List<FireworkExplosion> explosions
        ) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.engine = engine;
            if (explosions.isEmpty()) {
                throw new IllegalArgumentException("Cannot create firework starter with no explosions");
            } else {
                this.explosions = explosions;
                this.lifetime = explosions.size() * 2 - 1;

                for (FireworkExplosion fireworkexplosion : explosions) {
                    if (fireworkexplosion.hasTwinkle()) {
                        this.twinkleDelay = true;
                        this.lifetime += 15;
                        break;
                    }
                }
            }
        }

        @Override
        public void tick() {
            if (this.life == 0) {
                boolean flag = this.isFarAwayFromCamera();
                boolean flag1 = false;
                if (this.explosions.size() >= 3) {
                    flag1 = true;
                } else {
                    for (FireworkExplosion fireworkexplosion : this.explosions) {
                        if (fireworkexplosion.shape() == FireworkExplosion.Shape.LARGE_BALL) {
                            flag1 = true;
                            break;
                        }
                    }
                }

                SoundEvent soundevent1;
                if (flag1) {
                    soundevent1 = flag ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR : SoundEvents.FIREWORK_ROCKET_LARGE_BLAST;
                } else {
                    soundevent1 = flag ? SoundEvents.FIREWORK_ROCKET_BLAST_FAR : SoundEvents.FIREWORK_ROCKET_BLAST;
                }

                this.level.playLocalSound(this.x, this.y, this.z, soundevent1, SoundSource.AMBIENT, 20.0F, 0.95F + this.random.nextFloat() * 0.1F, true);
            }

            if (this.life % 2 == 0 && this.life / 2 < this.explosions.size()) {
                int j = this.life / 2;
                FireworkExplosion fireworkexplosion1 = this.explosions.get(j);
                boolean flag3 = fireworkexplosion1.hasTrail();
                boolean flag4 = fireworkexplosion1.hasTwinkle();
                IntList intlist = fireworkexplosion1.colors();
                IntList intlist1 = fireworkexplosion1.fadeColors();
                if (intlist.isEmpty()) {
                    intlist = IntList.of(DyeColor.BLACK.getFireworkColor());
                }

                var factory = net.neoforged.neoforge.client.FireworkShapeFactoryRegistry.get(fireworkexplosion1.shape());
                if (factory != null)
                    factory.build(this, flag3, flag4, intlist.toIntArray(), intlist1.toIntArray());
                else
                switch (fireworkexplosion1.shape()) {
                    case SMALL_BALL:
                        this.createParticleBall(0.25, 2, intlist, intlist1, flag3, flag4);
                        break;
                    case LARGE_BALL:
                        this.createParticleBall(0.5, 4, intlist, intlist1, flag3, flag4);
                        break;
                    case STAR:
                        this.createParticleShape(0.5, STAR_PARTICLE_COORDS, intlist, intlist1, flag3, flag4, false);
                        break;
                    case CREEPER:
                        this.createParticleShape(0.5, CREEPER_PARTICLE_COORDS, intlist, intlist1, flag3, flag4, true);
                        break;
                    case BURST:
                        this.createParticleBurst(intlist, intlist1, flag3, flag4);
                }

                int i = intlist.getInt(0);
                Particle particle = this.engine.createParticle(ParticleTypes.FLASH, this.x, this.y, this.z, 0.0, 0.0, 0.0);
                particle.setColor((float)FastColor.ARGB32.red(i) / 255.0F, (float)FastColor.ARGB32.green(i) / 255.0F, (float)FastColor.ARGB32.blue(i) / 255.0F);
            }

            this.life++;
            if (this.life > this.lifetime) {
                if (this.twinkleDelay) {
                    boolean flag2 = this.isFarAwayFromCamera();
                    SoundEvent soundevent = flag2 ? SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR : SoundEvents.FIREWORK_ROCKET_TWINKLE;
                    this.level.playLocalSound(this.x, this.y, this.z, soundevent, SoundSource.AMBIENT, 20.0F, 0.9F + this.random.nextFloat() * 0.15F, true);
                }

                this.remove();
            }
        }

        private boolean isFarAwayFromCamera() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(this.x, this.y, this.z) >= 256.0;
        }

        public void createParticle(
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            IntList colors,
            IntList fadeColors,
            boolean trail,
            boolean twinkle
        ) {
            FireworkParticles.SparkParticle fireworkparticles$sparkparticle = (FireworkParticles.SparkParticle)this.engine
                .createParticle(ParticleTypes.FIREWORK, x, y, z, xSpeed, ySpeed, zSpeed);
            fireworkparticles$sparkparticle.setTrail(trail);
            fireworkparticles$sparkparticle.setTwinkle(twinkle);
            fireworkparticles$sparkparticle.setAlpha(0.99F);
            fireworkparticles$sparkparticle.setColor(Util.getRandom(colors, this.random));
            if (!fadeColors.isEmpty()) {
                fireworkparticles$sparkparticle.setFadeColor(Util.getRandom(fadeColors, this.random));
            }
        }

        public void createParticleBall(double speed, int radius, IntList colors, IntList fadeColors, boolean trail, boolean twinkle) {
            double d0 = this.x;
            double d1 = this.y;
            double d2 = this.z;

            for (int i = -radius; i <= radius; i++) {
                for (int j = -radius; j <= radius; j++) {
                    for (int k = -radius; k <= radius; k++) {
                        double d3 = (double)j + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double d4 = (double)i + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double d5 = (double)k + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double d6 = Math.sqrt(d3 * d3 + d4 * d4 + d5 * d5) / speed + this.random.nextGaussian() * 0.05;
                        this.createParticle(d0, d1, d2, d3 / d6, d4 / d6, d5 / d6, colors, fadeColors, trail, twinkle);
                        if (i != -radius && i != radius && j != -radius && j != radius) {
                            k += radius * 2 - 1;
                        }
                    }
                }
            }
        }

        public void createParticleShape(
            double speed, double[][] coords, IntList colors, IntList fadeColors, boolean trail, boolean twinkle, boolean isCreeper
        ) {
            double d0 = coords[0][0];
            double d1 = coords[0][1];
            this.createParticle(this.x, this.y, this.z, d0 * speed, d1 * speed, 0.0, colors, fadeColors, trail, twinkle);
            float f = this.random.nextFloat() * (float) Math.PI;
            double d2 = isCreeper ? 0.034 : 0.34;

            for (int i = 0; i < 3; i++) {
                double d3 = (double)f + (double)((float)i * (float) Math.PI) * d2;
                double d4 = d0;
                double d5 = d1;

                for (int j = 1; j < coords.length; j++) {
                    double d6 = coords[j][0];
                    double d7 = coords[j][1];

                    for (double d8 = 0.25; d8 <= 1.0; d8 += 0.25) {
                        double d9 = Mth.lerp(d8, d4, d6) * speed;
                        double d10 = Mth.lerp(d8, d5, d7) * speed;
                        double d11 = d9 * Math.sin(d3);
                        d9 *= Math.cos(d3);

                        for (double d12 = -1.0; d12 <= 1.0; d12 += 2.0) {
                            this.createParticle(this.x, this.y, this.z, d9 * d12, d10, d11 * d12, colors, fadeColors, trail, twinkle);
                        }
                    }

                    d4 = d6;
                    d5 = d7;
                }
            }
        }

        public void createParticleBurst(IntList colors, IntList fadeColors, boolean trail, boolean twinkle) {
            double d0 = this.random.nextGaussian() * 0.05;
            double d1 = this.random.nextGaussian() * 0.05;

            for (int i = 0; i < 70; i++) {
                double d2 = this.xd * 0.5 + this.random.nextGaussian() * 0.15 + d0;
                double d3 = this.zd * 0.5 + this.random.nextGaussian() * 0.15 + d1;
                double d4 = this.yd * 0.5 + this.random.nextDouble() * 0.5;
                this.createParticle(this.x, this.y, this.z, d2, d4, d3, colors, fadeColors, trail, twinkle);
            }
        }
    }
}
