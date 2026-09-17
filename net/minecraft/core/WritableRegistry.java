package net.minecraft.core;

import net.minecraft.resources.ResourceKey;

public interface WritableRegistry<T> extends Registry<T> {
    Holder.Reference<T> register(ResourceKey<T> key, T value, RegistrationInfo registrationInfo);

    boolean isEmpty();

    HolderGetter<T> createRegistrationLookup();
}
