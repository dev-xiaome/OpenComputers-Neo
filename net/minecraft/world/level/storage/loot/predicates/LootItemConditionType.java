package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;

/**
 * The SerializerType for {@link LootItemCondition}.
 */
public record LootItemConditionType(MapCodec<? extends LootItemCondition> codec) {
}
