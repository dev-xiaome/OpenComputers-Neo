package net.minecraft.world.level.storage.loot.providers.nbt;

import com.mojang.serialization.MapCodec;

/**
 * The SerializerType for {@link NbtProvider}.
 */
public record LootNbtProviderType(MapCodec<? extends NbtProvider> codec) {
}
