package net.minecraft.world.level.storage.loot.providers.score;

import com.mojang.serialization.MapCodec;

/**
 * The SerializerType for {@link ScoreboardNameProvider}.
 */
public record LootScoreProviderType(MapCodec<? extends ScoreboardNameProvider> codec) {
}
