package net.minecraft.network.syncher;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Handles encoding and decoding of data for {@link SynchedEntityData}.
 * Note that mods cannot add new serializers, because this is not a managed registry and the serializer ID is limited to 16.
 */
public interface EntityDataSerializer<T> {
    StreamCodec<? super RegistryFriendlyByteBuf, T> codec();

    default EntityDataAccessor<T> createAccessor(int id) {
        return new EntityDataAccessor<>(id, this);
    }

    T copy(T value);

    static <T> EntityDataSerializer<T> forValueType(StreamCodec<? super RegistryFriendlyByteBuf, T> p_319946_) {
        return (EntityDataSerializer.ForValueType<T>)() -> p_319946_;
    }

    public interface ForValueType<T> extends EntityDataSerializer<T> {
        @Override
        default T copy(T p_238112_) {
            return p_238112_;
        }
    }
}
