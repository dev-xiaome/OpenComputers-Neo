package net.minecraft.util;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.IdMap;

public class CrudeIncrementalIntIdentityHashBiMap<K> implements IdMap<K> {
    private static final int NOT_FOUND = -1;
    private static final Object EMPTY_SLOT = null;
    private static final float LOADFACTOR = 0.8F;
    private K[] keys;
    private int[] values;
    private K[] byId;
    private int nextId;
    private int size;

    private CrudeIncrementalIntIdentityHashBiMap(int size) {
        this.keys = (K[])(new Object[size]);
        this.values = new int[size];
        this.byId = (K[])(new Object[size]);
    }

    private CrudeIncrementalIntIdentityHashBiMap(K[] keys, int[] values, K[] byId, int nextId, int size) {
        this.keys = keys;
        this.values = values;
        this.byId = byId;
        this.nextId = nextId;
        this.size = size;
    }

    public static <A> CrudeIncrementalIntIdentityHashBiMap<A> create(int size) {
        return new CrudeIncrementalIntIdentityHashBiMap((int)((float)size / 0.8F));
    }

    /**
     * Gets the integer ID we use to identify the given object.
     */
    @Override
    public int getId(@Nullable K value) {
        return this.getValue(this.indexOf(value, this.hash(value)));
    }

    @Nullable
    @Override
    public K byId(int value) {
        return value >= 0 && value < this.byId.length ? this.byId[value] : null;
    }

    private int getValue(int key) {
        return key == -1 ? -1 : this.values[key];
    }

    public boolean contains(K value) {
        return this.getId(value) != -1;
    }

    public boolean contains(int value) {
        return this.byId(value) != null;
    }

    /**
     * Adds the given object while expanding this map
     */
    public int add(K object) {
        int i = this.nextId();
        this.addMapping(object, i);
        return i;
    }

    private int nextId() {
        while (this.nextId < this.byId.length && this.byId[this.nextId] != null) {
            this.nextId++;
        }

        return this.nextId;
    }

    /**
     * Rehashes the map to the new capacity
     */
    private void grow(int capacity) {
        K[] ak = this.keys;
        int[] aint = this.values;
        CrudeIncrementalIntIdentityHashBiMap<K> crudeincrementalintidentityhashbimap = new CrudeIncrementalIntIdentityHashBiMap<>(capacity);

        for (int i = 0; i < ak.length; i++) {
            if (ak[i] != null) {
                crudeincrementalintidentityhashbimap.addMapping(ak[i], aint[i]);
            }
        }

        this.keys = crudeincrementalintidentityhashbimap.keys;
        this.values = crudeincrementalintidentityhashbimap.values;
        this.byId = crudeincrementalintidentityhashbimap.byId;
        this.nextId = crudeincrementalintidentityhashbimap.nextId;
        this.size = crudeincrementalintidentityhashbimap.size;
    }

    /**
     * Puts the provided object value with the integer key.
     */
    public void addMapping(K object, int intKey) {
        int i = Math.max(intKey, this.size + 1);
        if ((float)i >= (float)this.keys.length * 0.8F) {
            int j = this.keys.length << 1;

            while (j < intKey) {
                j <<= 1;
            }

            this.grow(j);
        }

        int k = this.findEmpty(this.hash(object));
        this.keys[k] = object;
        this.values[k] = intKey;
        this.byId[intKey] = object;
        this.size++;
        if (intKey == this.nextId) {
            this.nextId++;
        }
    }

    private int hash(@Nullable K object) {
        return (Mth.murmurHash3Mixer(System.identityHashCode(object)) & 2147483647) % this.keys.length;
    }

    private int indexOf(@Nullable K object, int startIndex) {
        for (int i = startIndex; i < this.keys.length; i++) {
            if (this.keys[i] == object) {
                return i;
            }

            if (this.keys[i] == EMPTY_SLOT) {
                return -1;
            }
        }

        for (int j = 0; j < startIndex; j++) {
            if (this.keys[j] == object) {
                return j;
            }

            if (this.keys[j] == EMPTY_SLOT) {
                return -1;
            }
        }

        return -1;
    }

    private int findEmpty(int startIndex) {
        for (int i = startIndex; i < this.keys.length; i++) {
            if (this.keys[i] == EMPTY_SLOT) {
                return i;
            }
        }

        for (int j = 0; j < startIndex; j++) {
            if (this.keys[j] == EMPTY_SLOT) {
                return j;
            }
        }

        throw new RuntimeException("Overflowed :(");
    }

    @Override
    public Iterator<K> iterator() {
        return Iterators.filter(Iterators.forArray(this.byId), Predicates.notNull());
    }

    public void clear() {
        Arrays.fill(this.keys, null);
        Arrays.fill(this.byId, null);
        this.nextId = 0;
        this.size = 0;
    }

    @Override
    public int size() {
        return this.size;
    }

    public CrudeIncrementalIntIdentityHashBiMap<K> copy() {
        return new CrudeIncrementalIntIdentityHashBiMap<>(
            (K[])((Object[])this.keys.clone()), (int[])this.values.clone(), (K[])((Object[])this.byId.clone()), this.nextId, this.size
        );
    }
}
