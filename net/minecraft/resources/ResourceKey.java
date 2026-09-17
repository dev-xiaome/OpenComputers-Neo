package net.minecraft.resources;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;

/**
 * An immutable key for a resource, in terms of the name of its parent registry and its location in that registry.
 * <p>
 * {@link net.minecraft.core.Registry} uses this to return resource keys for registry objects via {@link net.minecraft.core.Registry#getResourceKey(Object)}. It also uses this class to store its name, with the parent registry name set to {@code minecraft:root}. When used in this way it is usually referred to as a "registry key".</p>
 * <p>
 * @param <T> The type of the resource represented by this {@code ResourceKey}, or the type of the registry if it is a registry key.
 * @see net.minecraft.resources.ResourceLocation
 */
public class ResourceKey<T> implements java.lang.Comparable<ResourceKey<?>> {
    private static final ConcurrentMap<ResourceKey.InternKey, ResourceKey<?>> VALUES = new MapMaker().weakValues().makeMap();
    /**
     * The name of the parent registry of the resource.
     */
    private final ResourceLocation registryName;
    /**
     * The location of the resource within the registry.
     */
    private final ResourceLocation location;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> registryKey) {
        return ResourceLocation.CODEC.xmap(p_195979_ -> create(registryKey, p_195979_), ResourceKey::location);
    }

    public static <T> StreamCodec<ByteBuf, ResourceKey<T>> streamCodec(ResourceKey<? extends Registry<T>> registryKey) {
        return ResourceLocation.STREAM_CODEC.map(p_319559_ -> create(registryKey, p_319559_), ResourceKey::location);
    }

    /**
     * Constructs a new {@code ResourceKey} for a resource with the specified {@code location} within the registry specified by the given {@code registryKey}.
     *
     * @return the created resource key. The registry name is set to the location of the specified {@code registryKey} and with the specified {@code location} as the location of the resource.
     */
    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation location) {
        return create(registryKey.location, location);
    }

    /**
     * @return the created registry key. The registry name is set to {@code minecraft:root} and the location the specified {@code registryName}.
     */
    public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation location) {
        return create(Registries.ROOT_REGISTRY_NAME, location);
    }

    private static <T> ResourceKey<T> create(ResourceLocation registryName, ResourceLocation location) {
        return (ResourceKey<T>)VALUES.computeIfAbsent(
            new ResourceKey.InternKey(registryName, location), p_258225_ -> new ResourceKey(p_258225_.registry, p_258225_.location)
        );
    }

    private ResourceKey(ResourceLocation registryName, ResourceLocation location) {
        this.registryName = registryName;
        this.location = location;
    }

    @Override
    public String toString() {
        return "ResourceKey[" + this.registryName + " / " + this.location + "]";
    }

    /**
     * @return {@code true} if this resource key is a direct child of the specified {@code registryKey}.
     */
    public boolean isFor(ResourceKey<? extends Registry<?>> registryKey) {
        return this.registryName.equals(registryKey.location());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> registryKey) {
        return this.isFor(registryKey) ? Optional.of((ResourceKey<E>)this) : Optional.empty();
    }

    public ResourceLocation location() {
        return this.location;
    }

    public ResourceLocation registry() {
        return this.registryName;
    }

    public ResourceKey<Registry<T>> registryKey() {
        return createRegistryKey(this.registryName);
    }

    @Override
    public int compareTo(ResourceKey<?> o) {
        int ret = this.registry().compareTo(o.registry());
        if (ret == 0) ret = this.location().compareTo(o.location());
        return ret;
    }

    static record InternKey(ResourceLocation registry, ResourceLocation location) {
    }
}
