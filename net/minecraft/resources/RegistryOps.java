package net.minecraft.resources;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.util.ExtraCodecs;

public class RegistryOps<T> extends DelegatingOps<T> {
    public final RegistryOps.RegistryInfoLookup lookupProvider;

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, HolderLookup.Provider registries) {
        return create(delegate, new RegistryOps.HolderLookupAdapter(registries));
    }

    public static <T> RegistryOps<T> create(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup lookupProvider) {
        return new RegistryOps<>(delegate, lookupProvider);
    }

    public static <T> Dynamic<T> injectRegistryContext(Dynamic<T> dynamic, HolderLookup.Provider registries) {
        return new Dynamic<>(registries.createSerializationContext(dynamic.getOps()), dynamic.getValue());
    }

    protected RegistryOps(DynamicOps<T> delegate, RegistryOps.RegistryInfoLookup lookupProvider) {
        super(delegate);
        this.lookupProvider = lookupProvider;
    }

    protected RegistryOps(RegistryOps<T> other) {
        super(other);
        this.lookupProvider = other.lookupProvider;
    }

    public <U> RegistryOps<U> withParent(DynamicOps<U> ops) {
        return (RegistryOps<U>)(ops == this.delegate ? this : new RegistryOps<>(ops, this.lookupProvider));
    }

    public <E> Optional<HolderOwner<E>> owner(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::owner);
    }

    public <E> Optional<HolderGetter<E>> getter(ResourceKey<? extends Registry<? extends E>> registryKey) {
        return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::getter);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            RegistryOps<?> registryops = (RegistryOps<?>)other;
            return this.delegate.equals(registryops.delegate) && this.lookupProvider.equals(registryops.lookupProvider);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode() * 31 + this.lookupProvider.hashCode();
    }

    public static <E, O> RecordCodecBuilder<O, HolderGetter<E>> retrieveGetter(ResourceKey<? extends Registry<? extends E>> registryOps) {
        return ExtraCodecs.retrieveContext(
                p_274811_ -> p_274811_ instanceof RegistryOps<?> registryops
                        ? registryops.lookupProvider
                            .lookup(registryOps)
                            .map(p_255527_ -> DataResult.success(p_255527_.getter(), p_255527_.elementsLifecycle()))
                            .orElseGet(() -> DataResult.error(() -> "Unknown registry: " + registryOps))
                        : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(p_255526_ -> null);
    }

    public static <E> com.mojang.serialization.MapCodec<HolderLookup.RegistryLookup<E>> retrieveRegistryLookup(ResourceKey<? extends Registry<? extends E>> resourceKey) {
        return ExtraCodecs.retrieveContext(ops -> {
            if (!(ops instanceof RegistryOps<?> registryOps))
                return DataResult.error(() -> "Not a registry ops");

            return registryOps.lookupProvider.lookup(resourceKey).map(registryInfo -> {
                if (!(registryInfo.owner() instanceof HolderLookup.RegistryLookup<E> registryLookup))
                    return DataResult.<HolderLookup.RegistryLookup<E>>error(() -> "Found holder getter but was not a registry lookup for " + resourceKey);

                return DataResult.success(registryLookup, registryInfo.elementsLifecycle());
            }).orElseGet(() -> DataResult.error(() -> "Unknown registry: " + resourceKey));
        });
    }

    public static <E, O> RecordCodecBuilder<O, Holder.Reference<E>> retrieveElement(ResourceKey<E> key) {
        ResourceKey<? extends Registry<E>> resourcekey = ResourceKey.createRegistryKey(key.registry());
        return ExtraCodecs.retrieveContext(
                p_274808_ -> p_274808_ instanceof RegistryOps<?> registryops
                        ? registryops.lookupProvider
                            .lookup(resourcekey)
                            .flatMap(p_255518_ -> p_255518_.getter().get(key))
                            .map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> "Can't find value: " + key))
                        : DataResult.error(() -> "Not a registry ops")
            )
            .forGetter(p_255524_ -> null);
    }

    public static final class HolderLookupAdapter implements RegistryOps.RegistryInfoLookup {
        public final HolderLookup.Provider lookupProvider;
        private final Map<ResourceKey<? extends Registry<?>>, Optional<? extends RegistryOps.RegistryInfo<?>>> lookups = new ConcurrentHashMap<>();

        public HolderLookupAdapter(HolderLookup.Provider lookupProvider) {
            this.lookupProvider = lookupProvider;
        }

        @Override
        public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> registryKey) {
            return (Optional<RegistryOps.RegistryInfo<E>>)this.lookups.computeIfAbsent(registryKey, this::createLookup);
        }

        private Optional<RegistryOps.RegistryInfo<Object>> createLookup(ResourceKey<? extends Registry<?>> registryKey) {
            return this.lookupProvider.lookup(registryKey).map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else {
                if (other instanceof RegistryOps.HolderLookupAdapter registryops$holderlookupadapter
                    && this.lookupProvider.equals(registryops$holderlookupadapter.lookupProvider)) {
                    return true;
                }

                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.lookupProvider.hashCode();
        }
    }

    public static record RegistryInfo<T>(HolderOwner<T> owner, HolderGetter<T> getter, Lifecycle elementsLifecycle) {
        public static <T> RegistryOps.RegistryInfo<T> fromRegistryLookup(HolderLookup.RegistryLookup<T> registryLookup) {
            return new RegistryOps.RegistryInfo<>(registryLookup, registryLookup, registryLookup.registryLifecycle());
        }
    }

    public interface RegistryInfoLookup {
        <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey);
    }
}
