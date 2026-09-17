package net.minecraft.world.level.entity;

import javax.annotation.Nullable;

public interface EntityTypeTest<B, T extends B> {
    static <B, T extends B> EntityTypeTest<B, T> forClass(final Class<T> clazz) {
        return new EntityTypeTest<B, T>() {
            @Nullable
            @Override
            public T tryCast(B p_156924_) {
                return (T)(clazz.isInstance(p_156924_) ? p_156924_ : null);
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return clazz;
            }
        };
    }

    static <B, T extends B> EntityTypeTest<B, T> forExactClass(final Class<T> clazz) {
        return new EntityTypeTest<B, T>() {
            @Nullable
            @Override
            public T tryCast(B p_313860_) {
                return (T)(clazz.equals(p_313860_.getClass()) ? p_313860_ : null);
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return clazz;
            }
        };
    }

    @Nullable
    T tryCast(B entity);

    Class<? extends B> getBaseClass();
}
