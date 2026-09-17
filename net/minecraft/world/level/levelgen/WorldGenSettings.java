package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;

public record WorldGenSettings(WorldOptions options, WorldDimensions dimensions) {
    public static final Codec<WorldGenSettings> CODEC = RecordCodecBuilder.create(
        p_248477_ -> p_248477_.group(WorldOptions.CODEC.forGetter(WorldGenSettings::options), WorldDimensions.CODEC.forGetter(WorldGenSettings::dimensions))
                .apply(p_248477_, p_248477_.stable(WorldGenSettings::new))
    );

    public static <T> DataResult<T> encode(DynamicOps<T> ops, WorldOptions options, WorldDimensions dimensions) {
        return CODEC.encodeStart(ops, new WorldGenSettings(options, dimensions));
    }

    public static <T> DataResult<T> encode(DynamicOps<T> ops, WorldOptions options, RegistryAccess access) {
        return encode(ops, options, new WorldDimensions(access.registryOrThrow(Registries.LEVEL_STEM)));
    }
}
