package net.minecraft.resources;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;

/**
 * A codec that wraps a single element, or "file", within a registry. Possibly allows inline definitions, and always falls back to the element codec (and thus writing the registry element inline) if it fails to decode from the registry.
 */
public final class RegistryFileCodec<E> implements Codec<Holder<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<E> elementCodec;
    private final boolean allowInline;

    /**
     * Creates a codec for a single registry element, which is held as an un-resolved {@code Supplier<E>}. Both inline definitions of the object, and references to an existing registry element id are allowed.
     *
     * @param registryKey  The registry which elements may belong to.
     * @param elementCodec The codec used to decode either inline definitions, or
     *                     elements before entering them into the registry.
     */
    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec) {
        return create(registryKey, elementCodec, true);
    }

    public static <E> RegistryFileCodec<E> create(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        return new RegistryFileCodec<>(registryKey, elementCodec, allowInline);
    }

    private RegistryFileCodec(ResourceKey<? extends Registry<E>> registryKey, Codec<E> elementCodec, boolean allowInline) {
        this.registryKey = registryKey;
        this.elementCodec = elementCodec;
        this.allowInline = allowInline;
    }

    public <T> DataResult<T> encode(Holder<E> input, DynamicOps<T> ops, T prefix) {
        if (ops instanceof RegistryOps<?> registryops) {
            Optional<HolderOwner<E>> optional = registryops.owner(this.registryKey);
            if (optional.isPresent()) {
                if (!input.canSerializeIn(optional.get())) {
                    return DataResult.error(() -> "Element " + input + " is not valid in current registry set");
                }

                return input.unwrap()
                    .map(
                        p_206714_ -> ResourceLocation.CODEC.encode(p_206714_.location(), ops, prefix),
                        p_206710_ -> this.elementCodec.encode((E)p_206710_, ops, prefix)
                    );
            }
        }

        return this.elementCodec.encode(input.value(), ops, prefix);
    }

    @Override
    public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
        if (ops instanceof RegistryOps<?> registryops) {
            Optional<HolderGetter<E>> optional = registryops.getter(this.registryKey);
            if (optional.isEmpty()) {
                return DataResult.error(() -> "Registry does not exist: " + this.registryKey);
            } else {
                HolderGetter<E> holdergetter = optional.get();
                DataResult<Pair<ResourceLocation, T>> dataresult = ResourceLocation.CODEC.decode(ops, input);
                if (dataresult.result().isEmpty()) {
                    return !this.allowInline
                        ? DataResult.error(() -> "Inline definitions not allowed here")
                        : this.elementCodec.decode(ops, input).map(p_206720_ -> p_206720_.mapFirst(Holder::direct));
                } else {
                    Pair<ResourceLocation, T> pair = dataresult.result().get();
                    ResourceKey<E> resourcekey = ResourceKey.create(this.registryKey, pair.getFirst());
                    return holdergetter.get(resourcekey)
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Failed to get element " + resourcekey))
                        .<Pair<Holder<E>, T>>map(p_255658_ -> Pair.of(p_255658_, pair.getSecond()))
                        .setLifecycle(Lifecycle.stable());
                }
            }
        } else {
            return this.elementCodec.decode(ops, input).map(p_214212_ -> p_214212_.mapFirst(Holder::direct));
        }
    }

    @Override
    public String toString() {
        return "RegistryFileCodec[" + this.registryKey + " " + this.elementCodec + "]";
    }
}
