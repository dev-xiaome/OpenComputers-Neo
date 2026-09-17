package net.minecraft.core.component;

import javax.annotation.Nullable;

public interface DataComponentHolder extends net.neoforged.neoforge.common.extensions.IDataComponentHolderExtension {
    DataComponentMap getComponents();

    @Nullable
    default <T> T get(DataComponentType<? extends T> component) {
        return this.getComponents().get(component);
    }

    default <T> T getOrDefault(DataComponentType<? extends T> component, T defaultValue) {
        return this.getComponents().getOrDefault(component, defaultValue);
    }

    default boolean has(DataComponentType<?> component) {
        return this.getComponents().has(component);
    }
}
