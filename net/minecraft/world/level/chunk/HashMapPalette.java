package net.minecraft.world.level.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;

public class HashMapPalette<T> implements Palette<T> {
    private final IdMap<T> registry;
    private final CrudeIncrementalIntIdentityHashBiMap<T> values;
    private final PaletteResize<T> resizeHandler;
    private final int bits;

    public HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, List<T> values) {
        this(registry, bits, resizeHandler);
        values.forEach(this.values::add);
    }

    public HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler) {
        this(registry, bits, resizeHandler, CrudeIncrementalIntIdentityHashBiMap.create(1 << bits));
    }

    private HashMapPalette(IdMap<T> registry, int bits, PaletteResize<T> resizeHandler, CrudeIncrementalIntIdentityHashBiMap<T> values) {
        this.registry = registry;
        this.bits = bits;
        this.resizeHandler = resizeHandler;
        this.values = values;
    }

    public static <A> Palette<A> create(int bits, IdMap<A> registry, PaletteResize<A> resizeHandler, List<A> values) {
        return new HashMapPalette<>(registry, bits, resizeHandler, values);
    }

    @Override
    public int idFor(T state) {
        int i = this.values.getId(state);
        if (i == -1) {
            i = this.values.add(state);
            if (i >= 1 << this.bits) {
                i = this.resizeHandler.onResize(this.bits + 1, state);
            }
        }

        return i;
    }

    @Override
    public boolean maybeHas(Predicate<T> filter) {
        for (int i = 0; i < this.getSize(); i++) {
            if (filter.test(this.values.byId(i))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public T valueFor(int id) {
        T t = this.values.byId(id);
        if (t == null) {
            throw new MissingPaletteEntryException(id);
        } else {
            return t;
        }
    }

    @Override
    public void read(FriendlyByteBuf buffer) {
        this.values.clear();
        int i = buffer.readVarInt();

        for (int j = 0; j < i; j++) {
            this.values.add(this.registry.byIdOrThrow(buffer.readVarInt()));
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        int i = this.getSize();
        buffer.writeVarInt(i);

        for (int j = 0; j < i; j++) {
            buffer.writeVarInt(this.registry.getId(this.values.byId(j)));
        }
    }

    @Override
    public int getSerializedSize() {
        int i = VarInt.getByteSize(this.getSize());

        for (int j = 0; j < this.getSize(); j++) {
            i += VarInt.getByteSize(this.registry.getId(this.values.byId(j)));
        }

        return i;
    }

    public List<T> getEntries() {
        ArrayList<T> arraylist = new ArrayList<>();
        this.values.iterator().forEachRemaining(arraylist::add);
        return arraylist;
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public Palette<T> copy() {
        return new HashMapPalette<>(this.registry, this.bits, this.resizeHandler, this.values.copy());
    }
}
