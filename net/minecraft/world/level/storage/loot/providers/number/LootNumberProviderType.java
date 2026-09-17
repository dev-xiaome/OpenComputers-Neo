package net.minecraft.world.level.storage.loot.providers.number;

import com.mojang.serialization.MapCodec;

/**
 * The SerializerType for {@link NumberProvider}.
 */
public record LootNumberProviderType(MapCodec<? extends NumberProvider> codec) {
}
