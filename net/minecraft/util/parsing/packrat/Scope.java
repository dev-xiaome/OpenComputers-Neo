package net.minecraft.util.parsing.packrat;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Objects;
import javax.annotation.Nullable;

public final class Scope {
    private final Object2ObjectMap<Atom<?>, Object> values = new Object2ObjectArrayMap<>();

    public <T> void put(Atom<T> atom, @Nullable T value) {
        this.values.put(atom, value);
    }

    @Nullable
    public <T> T get(Atom<T> atom) {
        return (T)this.values.get(atom);
    }

    public <T> T getOrThrow(Atom<T> atom) {
        return Objects.requireNonNull(this.get(atom));
    }

    public <T> T getOrDefault(Atom<T> atom, T defaultValue) {
        return Objects.requireNonNullElse(this.get(atom), defaultValue);
    }

    @Nullable
    @SafeVarargs
    public final <T> T getAny(Atom<T>... atoms) {
        for (Atom<T> atom : atoms) {
            T t = this.get(atom);
            if (t != null) {
                return t;
            }
        }

        return null;
    }

    @SafeVarargs
    public final <T> T getAnyOrThrow(Atom<T>... atoms) {
        return Objects.requireNonNull(this.getAny(atoms));
    }

    @Override
    public String toString() {
        return this.values.toString();
    }

    public void putAll(Scope scope) {
        this.values.putAll(scope.values);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return other instanceof Scope scope ? this.values.equals(scope.values) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }
}
