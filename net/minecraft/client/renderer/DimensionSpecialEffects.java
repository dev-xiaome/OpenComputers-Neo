package net.minecraft.client.renderer;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class DimensionSpecialEffects implements net.neoforged.neoforge.client.extensions.IDimensionSpecialEffectsExtension {
    private static final Object2ObjectMap<ResourceLocation, DimensionSpecialEffects> EFFECTS = Util.make(new Object2ObjectArrayMap<>(), p_108881_ -> {
        DimensionSpecialEffects.OverworldEffects dimensionspecialeffects$overworldeffects = new DimensionSpecialEffects.OverworldEffects();
        p_108881_.defaultReturnValue(dimensionspecialeffects$overworldeffects);
        p_108881_.put(BuiltinDimensionTypes.OVERWORLD_EFFECTS, dimensionspecialeffects$overworldeffects);
        p_108881_.put(BuiltinDimensionTypes.NETHER_EFFECTS, new DimensionSpecialEffects.NetherEffects());
        p_108881_.put(BuiltinDimensionTypes.END_EFFECTS, new DimensionSpecialEffects.EndEffects());
    });
    private final float[] sunriseCol = new float[4];
    private final float cloudLevel;
    private final boolean hasGround;
    private final DimensionSpecialEffects.SkyType skyType;
    private final boolean forceBrightLightmap;
    private final boolean constantAmbientLight;

    public DimensionSpecialEffects(float cloudLevel, boolean hasGround, DimensionSpecialEffects.SkyType skyType, boolean forceBrightLightmap, boolean constantAmbientLight) {
        this.cloudLevel = cloudLevel;
        this.hasGround = hasGround;
        this.skyType = skyType;
        this.forceBrightLightmap = forceBrightLightmap;
        this.constantAmbientLight = constantAmbientLight;
    }

    public static DimensionSpecialEffects forType(DimensionType dimensionType) {
        return net.neoforged.neoforge.client.DimensionSpecialEffectsManager.getForType(dimensionType.effectsLocation());
    }

    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        float f = 0.4F;
        float f1 = Mth.cos(timeOfDay * (float) (Math.PI * 2)) - 0.0F;
        float f2 = -0.0F;
        if (f1 >= -0.4F && f1 <= 0.4F) {
            float f3 = (f1 - -0.0F) / 0.4F * 0.5F + 0.5F;
            float f4 = 1.0F - (1.0F - Mth.sin(f3 * (float) Math.PI)) * 0.99F;
            f4 *= f4;
            this.sunriseCol[0] = f3 * 0.3F + 0.7F;
            this.sunriseCol[1] = f3 * f3 * 0.7F + 0.2F;
            this.sunriseCol[2] = f3 * f3 * 0.0F + 0.2F;
            this.sunriseCol[3] = f4;
            return this.sunriseCol;
        } else {
            return null;
        }
    }

    public float getCloudHeight() {
        return this.cloudLevel;
    }

    public boolean hasGround() {
        return this.hasGround;
    }

    public abstract Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness);

    public abstract boolean isFoggyAt(int x, int y);

    public DimensionSpecialEffects.SkyType skyType() {
        return this.skyType;
    }

    public boolean forceBrightLightmap() {
        return this.forceBrightLightmap;
    }

    public boolean constantAmbientLight() {
        return this.constantAmbientLight;
    }

    @OnlyIn(Dist.CLIENT)
    public static class EndEffects extends DimensionSpecialEffects {
        public EndEffects() {
            super(Float.NaN, false, DimensionSpecialEffects.SkyType.END, true, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 p_108894_, float p_108895_) {
            return p_108894_.scale(0.15F);
        }

        @Override
        public boolean isFoggyAt(int p_108891_, int p_108892_) {
            return false;
        }

        @Nullable
        @Override
        public float[] getSunriseColor(float p_108888_, float p_108889_) {
            return null;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class NetherEffects extends DimensionSpecialEffects {
        public NetherEffects() {
            super(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, true);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 p_108901_, float p_108902_) {
            return p_108901_;
        }

        @Override
        public boolean isFoggyAt(int p_108898_, int p_108899_) {
            return true;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class OverworldEffects extends DimensionSpecialEffects {
        public static final int CLOUD_LEVEL = 192;

        public OverworldEffects() {
            super(192.0F, true, DimensionSpecialEffects.SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 p_108908_, float p_108909_) {
            return p_108908_.multiply((double)(p_108909_ * 0.94F + 0.06F), (double)(p_108909_ * 0.94F + 0.06F), (double)(p_108909_ * 0.91F + 0.09F));
        }

        @Override
        public boolean isFoggyAt(int p_108905_, int p_108906_) {
            return false;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum SkyType {
        NONE,
        NORMAL,
        END;
    }
}
