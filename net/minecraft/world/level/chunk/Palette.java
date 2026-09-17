package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

public interface Palette<T> {
    int idFor(T state);

    boolean maybeHas(Predicate<T> filter);

    T valueFor(int id);

    void read(FriendlyByteBuf buffer);

    void write(FriendlyByteBuf buffer);

    int getSerializedSize();

    int getSize();

    Palette<T> copy();

    public interface Factory {
        <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values);
    }
}
