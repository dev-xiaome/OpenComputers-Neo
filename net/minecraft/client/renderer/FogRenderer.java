package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class FogRenderer {
    private static final int WATER_FOG_DISTANCE = 96;
    private static final List<FogRenderer.MobEffectFogFunction> MOB_EFFECT_FOG = Lists.newArrayList(
        new FogRenderer.BlindnessFogFunction(), new FogRenderer.DarknessFogFunction()
    );
    public static final float BIOME_FOG_TRANSITION_TIME = 5000.0F;
    private static float fogRed;
    private static float fogGreen;
    private static float fogBlue;
    private static int targetBiomeFog = -1;
    private static int previousBiomeFog = -1;
    private static long biomeChangedTime = -1L;

    public static void setupColor(Camera activeRenderInfo, float partialTicks, ClientLevel level, int renderDistanceChunks, float bossColorModifier) {
        FogType fogtype = activeRenderInfo.getFluidInCamera();
        Entity entity = activeRenderInfo.getEntity();
        if (fogtype == FogType.WATER) {
            long i = Util.getMillis();
            int j = level.getBiome(BlockPos.containing(activeRenderInfo.getPosition())).value().getWaterFogColor();
            if (biomeChangedTime < 0L) {
                targetBiomeFog = j;
                previousBiomeFog = j;
                biomeChangedTime = i;
            }

            int k = targetBiomeFog >> 16 & 0xFF;
            int l = targetBiomeFog >> 8 & 0xFF;
            int i1 = targetBiomeFog & 0xFF;
            int j1 = previousBiomeFog >> 16 & 0xFF;
            int k1 = previousBiomeFog >> 8 & 0xFF;
            int l1 = previousBiomeFog & 0xFF;
            float f = Mth.clamp((float)(i - biomeChangedTime) / 5000.0F, 0.0F, 1.0F);
            float f1 = Mth.lerp(f, (float)j1, (float)k);
            float f2 = Mth.lerp(f, (float)k1, (float)l);
            float f3 = Mth.lerp(f, (float)l1, (float)i1);
            fogRed = f1 / 255.0F;
            fogGreen = f2 / 255.0F;
            fogBlue = f3 / 255.0F;
            if (targetBiomeFog != j) {
                targetBiomeFog = j;
                previousBiomeFog = Mth.floor(f1) << 16 | Mth.floor(f2) << 8 | Mth.floor(f3);
                biomeChangedTime = i;
            }
        } else if (fogtype == FogType.LAVA) {
            fogRed = 0.6F;
            fogGreen = 0.1F;
            fogBlue = 0.0F;
            biomeChangedTime = -1L;
        } else if (fogtype == FogType.POWDER_SNOW) {
            fogRed = 0.623F;
            fogGreen = 0.734F;
            fogBlue = 0.785F;
            biomeChangedTime = -1L;
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
        } else {
            float f4 = 0.25F + 0.75F * (float)renderDistanceChunks / 32.0F;
            f4 = 1.0F - (float)Math.pow((double)f4, 0.25);
            Vec3 vec3 = level.getSkyColor(activeRenderInfo.getPosition(), partialTicks);
            float f6 = (float)vec3.x;
            float f8 = (float)vec3.y;
            float f10 = (float)vec3.z;
            float f11 = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTicks) * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
            BiomeManager biomemanager = level.getBiomeManager();
            Vec3 vec31 = activeRenderInfo.getPosition().subtract(2.0, 2.0, 2.0).scale(0.25);
            Vec3 vec32 = CubicSampler.gaussianSampleVec3(
                vec31,
                (p_109033_, p_109034_, p_109035_) -> level.effects()
                        .getBrightnessDependentFogColor(
                            Vec3.fromRGB24(biomemanager.getNoiseBiomeAtQuart(p_109033_, p_109034_, p_109035_).value().getFogColor()), f11
                        )
            );
            fogRed = (float)vec32.x();
            fogGreen = (float)vec32.y();
            fogBlue = (float)vec32.z();
            if (renderDistanceChunks >= 4) {
                float f12 = Mth.sin(level.getSunAngle(partialTicks)) > 0.0F ? -1.0F : 1.0F;
                Vector3f vector3f = new Vector3f(f12, 0.0F, 0.0F);
                float f16 = activeRenderInfo.getLookVector().dot(vector3f);
                if (f16 < 0.0F) {
                    f16 = 0.0F;
                }

                if (f16 > 0.0F) {
                    float[] afloat = level.effects().getSunriseColor(level.getTimeOfDay(partialTicks), partialTicks);
                    if (afloat != null) {
                        f16 *= afloat[3];
                        fogRed = fogRed * (1.0F - f16) + afloat[0] * f16;
                        fogGreen = fogGreen * (1.0F - f16) + afloat[1] * f16;
                        fogBlue = fogBlue * (1.0F - f16) + afloat[2] * f16;
                    }
                }
            }

            fogRed = fogRed + (f6 - fogRed) * f4;
            fogGreen = fogGreen + (f8 - fogGreen) * f4;
            fogBlue = fogBlue + (f10 - fogBlue) * f4;
            float f13 = level.getRainLevel(partialTicks);
            if (f13 > 0.0F) {
                float f14 = 1.0F - f13 * 0.5F;
                float f17 = 1.0F - f13 * 0.4F;
                fogRed *= f14;
                fogGreen *= f14;
                fogBlue *= f17;
            }

            float f15 = level.getThunderLevel(partialTicks);
            if (f15 > 0.0F) {
                float f18 = 1.0F - f15 * 0.5F;
                fogRed *= f18;
                fogGreen *= f18;
                fogBlue *= f18;
            }

            biomeChangedTime = -1L;
        }

        float f5 = ((float)activeRenderInfo.getPosition().y - (float)level.getMinBuildHeight()) * level.getLevelData().getClearColorScale();
        FogRenderer.MobEffectFogFunction fogrenderer$mobeffectfogfunction = getPriorityFogFunction(entity, partialTicks);
        if (fogrenderer$mobeffectfogfunction != null) {
            LivingEntity livingentity = (LivingEntity)entity;
            f5 = fogrenderer$mobeffectfogfunction.getModifiedVoidDarkness(
                livingentity, livingentity.getEffect(fogrenderer$mobeffectfogfunction.getMobEffect()), f5, partialTicks
            );
        }

        if (f5 < 1.0F && fogtype != FogType.LAVA && fogtype != FogType.POWDER_SNOW) {
            if (f5 < 0.0F) {
                f5 = 0.0F;
            }

            f5 *= f5;
            fogRed *= f5;
            fogGreen *= f5;
            fogBlue *= f5;
        }

        if (bossColorModifier > 0.0F) {
            fogRed = fogRed * (1.0F - bossColorModifier) + fogRed * 0.7F * bossColorModifier;
            fogGreen = fogGreen * (1.0F - bossColorModifier) + fogGreen * 0.6F * bossColorModifier;
            fogBlue = fogBlue * (1.0F - bossColorModifier) + fogBlue * 0.6F * bossColorModifier;
        }

        float f7;
        if (fogtype == FogType.WATER) {
            if (entity instanceof LocalPlayer) {
                f7 = ((LocalPlayer)entity).getWaterVision();
            } else {
                f7 = 1.0F;
            }
        } else {
            label86: {
                if (entity instanceof LivingEntity livingentity1
                    && livingentity1.hasEffect(MobEffects.NIGHT_VISION)
                    && !livingentity1.hasEffect(MobEffects.DARKNESS)) {
                    f7 = GameRenderer.getNightVisionScale(livingentity1, partialTicks);
                    break label86;
                }

                f7 = 0.0F;
            }
        }

        if (fogRed != 0.0F && fogGreen != 0.0F && fogBlue != 0.0F) {
            float f9 = Math.min(1.0F / fogRed, Math.min(1.0F / fogGreen, 1.0F / fogBlue));
            fogRed = fogRed * (1.0F - f7) + fogRed * f9 * f7;
            fogGreen = fogGreen * (1.0F - f7) + fogGreen * f9 * f7;
            fogBlue = fogBlue * (1.0F - f7) + fogBlue * f9 * f7;
        }

        Vector3f fogColor = net.neoforged.neoforge.client.ClientHooks.getFogColor(activeRenderInfo, partialTicks, level, renderDistanceChunks, bossColorModifier, fogRed, fogGreen, fogBlue);

        fogRed = fogColor.x();
        fogGreen = fogColor.y();
        fogBlue = fogColor.z();

        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    public static void setupNoFog() {
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
    }

    @Nullable
    private static FogRenderer.MobEffectFogFunction getPriorityFogFunction(Entity entity, float partialTick) {
        return entity instanceof LivingEntity livingentity
            ? MOB_EFFECT_FOG.stream().filter(p_234171_ -> p_234171_.isEnabled(livingentity, partialTick)).findFirst().orElse(null)
            : null;
    }

    public static void setupFog(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance, boolean shouldCreateFog, float partialTick) {
        FogType fogtype = camera.getFluidInCamera();
        Entity entity = camera.getEntity();
        FogRenderer.FogData fogrenderer$fogdata = new FogRenderer.FogData(fogMode);
        FogRenderer.MobEffectFogFunction fogrenderer$mobeffectfogfunction = getPriorityFogFunction(entity, partialTick);
        if (fogtype == FogType.LAVA) {
            if (entity.isSpectator()) {
                fogrenderer$fogdata.start = -8.0F;
                fogrenderer$fogdata.end = farPlaneDistance * 0.5F;
            } else if (entity instanceof LivingEntity && ((LivingEntity)entity).hasEffect(MobEffects.FIRE_RESISTANCE)) {
                fogrenderer$fogdata.start = 0.0F;
                fogrenderer$fogdata.end = 5.0F;
            } else {
                fogrenderer$fogdata.start = 0.25F;
                fogrenderer$fogdata.end = 1.0F;
            }
        } else if (fogtype == FogType.POWDER_SNOW) {
            if (entity.isSpectator()) {
                fogrenderer$fogdata.start = -8.0F;
                fogrenderer$fogdata.end = farPlaneDistance * 0.5F;
            } else {
                fogrenderer$fogdata.start = 0.0F;
                fogrenderer$fogdata.end = 2.0F;
            }
        } else if (fogrenderer$mobeffectfogfunction != null) {
            LivingEntity livingentity = (LivingEntity)entity;
            MobEffectInstance mobeffectinstance = livingentity.getEffect(fogrenderer$mobeffectfogfunction.getMobEffect());
            if (mobeffectinstance != null) {
                fogrenderer$mobeffectfogfunction.setupFog(fogrenderer$fogdata, livingentity, mobeffectinstance, farPlaneDistance, partialTick);
            }
        } else if (fogtype == FogType.WATER) {
            fogrenderer$fogdata.start = -8.0F;
            fogrenderer$fogdata.end = 96.0F;
            if (entity instanceof LocalPlayer localplayer) {
                fogrenderer$fogdata.end = fogrenderer$fogdata.end * Math.max(0.25F, localplayer.getWaterVision());
                Holder<Biome> holder = localplayer.level().getBiome(localplayer.blockPosition());
                if (holder.is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
                    fogrenderer$fogdata.end *= 0.85F;
                }
            }

            if (fogrenderer$fogdata.end > farPlaneDistance) {
                fogrenderer$fogdata.end = farPlaneDistance;
                fogrenderer$fogdata.shape = FogShape.CYLINDER;
            }
        } else if (shouldCreateFog) {
            fogrenderer$fogdata.start = farPlaneDistance * 0.05F;
            fogrenderer$fogdata.end = Math.min(farPlaneDistance, 192.0F) * 0.5F;
        } else if (fogMode == FogRenderer.FogMode.FOG_SKY) {
            fogrenderer$fogdata.start = 0.0F;
            fogrenderer$fogdata.end = farPlaneDistance;
            fogrenderer$fogdata.shape = FogShape.CYLINDER;
        } else {
            float f = Mth.clamp(farPlaneDistance / 10.0F, 4.0F, 64.0F);
            fogrenderer$fogdata.start = farPlaneDistance - f;
            fogrenderer$fogdata.end = farPlaneDistance;
            fogrenderer$fogdata.shape = FogShape.CYLINDER;
        }

        RenderSystem.setShaderFogStart(fogrenderer$fogdata.start);
        RenderSystem.setShaderFogEnd(fogrenderer$fogdata.end);
        RenderSystem.setShaderFogShape(fogrenderer$fogdata.shape);
        net.neoforged.neoforge.client.ClientHooks.onFogRender(fogMode, fogtype, camera, partialTick, farPlaneDistance, fogrenderer$fogdata.start, fogrenderer$fogdata.end, fogrenderer$fogdata.shape);
    }

    public static void levelFogColor() {
        RenderSystem.setShaderFogColor(fogRed, fogGreen, fogBlue);
    }

    @OnlyIn(Dist.CLIENT)
    static class BlindnessFogFunction implements FogRenderer.MobEffectFogFunction {
        @Override
        public Holder<MobEffect> getMobEffect() {
            return MobEffects.BLINDNESS;
        }

        @Override
        public void setupFog(FogRenderer.FogData p_234181_, LivingEntity p_234182_, MobEffectInstance p_234183_, float p_234184_, float p_234185_) {
            float f = p_234183_.isInfiniteDuration() ? 5.0F : Mth.lerp(Math.min(1.0F, (float)p_234183_.getDuration() / 20.0F), p_234184_, 5.0F);
            if (p_234181_.mode == FogRenderer.FogMode.FOG_SKY) {
                p_234181_.start = 0.0F;
                p_234181_.end = f * 0.8F;
            } else {
                p_234181_.start = f * 0.25F;
                p_234181_.end = f;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class DarknessFogFunction implements FogRenderer.MobEffectFogFunction {
        @Override
        public Holder<MobEffect> getMobEffect() {
            return MobEffects.DARKNESS;
        }

        @Override
        public void setupFog(FogRenderer.FogData p_234194_, LivingEntity p_234195_, MobEffectInstance p_234196_, float p_234197_, float p_234198_) {
            float f = Mth.lerp(p_234196_.getBlendFactor(p_234195_, p_234198_), p_234197_, 15.0F);
            p_234194_.start = p_234194_.mode == FogRenderer.FogMode.FOG_SKY ? 0.0F : f * 0.75F;
            p_234194_.end = f;
        }

        @Override
        public float getModifiedVoidDarkness(LivingEntity p_234189_, MobEffectInstance p_234190_, float p_234191_, float p_234192_) {
            return 1.0F - p_234190_.getBlendFactor(p_234189_, p_234192_);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class FogData {
        public final FogRenderer.FogMode mode;
        public float start;
        public float end;
        public FogShape shape = FogShape.SPHERE;

        public FogData(FogRenderer.FogMode mode) {
            this.mode = mode;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum FogMode {
        FOG_SKY,
        FOG_TERRAIN;
    }

    @OnlyIn(Dist.CLIENT)
    interface MobEffectFogFunction {
        Holder<MobEffect> getMobEffect();

        void setupFog(FogRenderer.FogData fogData, LivingEntity entity, MobEffectInstance effectInstance, float farPlaneDistance, float p_234216_);

        default boolean isEnabled(LivingEntity entity, float p_234207_) {
            return entity.hasEffect(this.getMobEffect());
        }

        default float getModifiedVoidDarkness(LivingEntity entity, MobEffectInstance effectInstance, float p_234210_, float partialTick) {
            MobEffectInstance mobeffectinstance = entity.getEffect(this.getMobEffect());
            if (mobeffectinstance != null) {
                if (mobeffectinstance.endsWithin(19)) {
                    p_234210_ = 1.0F - (float)mobeffectinstance.getDuration() / 20.0F;
                } else {
                    p_234210_ = 0.0F;
                }
            }

            return p_234210_;
        }
    }
}
