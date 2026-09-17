package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;

public class Cloner<T> {
    private final Codec<T> directCodec;

    Cloner(Codec<T> directCodec) {
        this.directCodec = directCodec;
    }

    public T clone(T p_object, HolderLookup.Provider lookupProvider1, HolderLookup.Provider lookupProvider2) {
        DynamicOps<Object> dynamicops = lookupProvider1.createSerializationContext(JavaOps.INSTANCE);
        DynamicOps<Object> dynamicops1 = lookupProvider2.createSerializationContext(JavaOps.INSTANCE);
        Object object = this.directCodec
            .encodeStart(dynamicops, p_object)
            .getOrThrow(p_312200_ -> new IllegalStateException("Failed to encode: " + p_312200_));
        return this.directCodec.parse(dynamicops1, object).getOrThrow(p_312832_ -> new IllegalStateException("Failed to decode: " + p_312832_));
    }

    public static class Factory {
        private final Map<ResourceKey<? extends Registry<?>>, Cloner<?>> codecs = new HashMap<>();

        public <T> Cloner.Factory addCodec(ResourceKey<? extends Registry<? extends T>> registryKey, Codec<T> codec) {
            this.codecs.put(registryKey, new Cloner<>(codec));
            return this;
        }

        @Nullable
        public <T> Cloner<T> cloner(ResourceKey<? extends Registry<? extends T>> registryKey) {
            return (Cloner<T>)this.codecs.get(registryKey);
        }
    }
}
