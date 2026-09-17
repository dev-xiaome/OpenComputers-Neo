package net.minecraft.world.level.gameevent;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public interface PositionSourceType<T extends PositionSource> {
    /**
     * This PositionSource type represents blocks within the world and a fixed position.
     */
    PositionSourceType<BlockPositionSource> BLOCK = register("block", new BlockPositionSource.Type());
    /**
     * This PositionSource type represents an entity within the world. This source type will keep a reference to the entity itself.
     */
    PositionSourceType<EntityPositionSource> ENTITY = register("entity", new EntityPositionSource.Type());

    MapCodec<T> codec();

    StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec();

    /**
     * Registers a new PositionSource type with the game registry.
     * @see net.minecraft.core.Registry#POSITION_SOURCE_TYPE
     * @return The newly registered source type.
     *
     * @param id   The Id to register the type to.
     * @param type The type to register.
     */
    static <S extends PositionSourceType<T>, T extends PositionSource> S register(String id, S type) {
        return Registry.register(BuiltInRegistries.POSITION_SOURCE_TYPE, id, type);
    }
}
