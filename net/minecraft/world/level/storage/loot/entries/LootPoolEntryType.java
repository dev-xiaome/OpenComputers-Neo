package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;

/**
 * The SerializerType for {@link LootPoolEntryContainer}.
 */
public record LootPoolEntryType(MapCodec<? extends LootPoolEntryContainer> codec) {
}
