package net.minecraft.world.level.chunk;

import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import org.apache.commons.lang3.Validate;

public class SingleValuePalette<T> implements Palette<T> {
    private final IdMap<T> registry;
    @Nullable
    private T value;
    private final PaletteResize<T> resizeHandler;

    public SingleValuePalette(IdMap<T> registry, PaletteResize<T> resizeHandler, List<T> value) {
        this.registry = registry;
        this.resizeHandler = resizeHandler;
        if (value.size() > 0) {
            Validate.isTrue(value.size() <= 1, "Can't initialize SingleValuePalette with %d values.", (long)value.size());
            this.value = value.get(0);
        }
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> value) {
        return new SingleValuePalette<>(registry, resizeHandler, value);
    }

    @Override
    public int idFor(T state) {
        if (this.value != null && this.value != state) {
            return this.resizeHandler.onResize(1, state);
        } else {
            this.value = state;
            return 0;
        }
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        if (this.value == null) {
            throw new IllegalStateException("Use of an uninitialized palette");
        } else {
            return filter.test(this.value);
        }
    }

    @Override
    public T valueFor(int id) {
        if (this.value != null && id == 0) {
            return this.value;
        } else {
            throw new IllegalStateException("Missing Palette entry for id " + id + ".");
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
        this.value = this.registry.byIdOrThrow(buffer.readVarInt());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        if (this.value == null) {
            throw new IllegalStateException("Use of an uninitialized palette");
        } else {
            buffer.writeVarInt(this.registry.getId(this.value));
        }
    }

    @Override
    public int getSerializedSize() {
        if (this.value == null) {
            throw new IllegalStateException("Use of an uninitialized palette");
        } else {
            return VarInt.getByteSize(this.registry.getId(this.value));
        }
    }

    @Override
    public int getSize() {
        return 1;
    }

    @Override
    public Palette<T> copy() {
        if (this.value == null) {
            throw new IllegalStateException("Use of an uninitialized palette");
        } else {
            return this;
        }
    }
}
