package net.minecraft.tags;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;

public class TagNetworkSerialization {
    public static Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> serializeTagsToNetwork(
        LayeredRegistryAccess<RegistryLayer> registryAccess
    ) {
        return RegistrySynchronization.networkSafeRegistries(registryAccess)
            .map(p_203949_ -> Pair.of(p_203949_.key(), serializeToNetwork(p_203949_.value())))
            .filter(p_321439_ -> p_321439_.getSecond().size() > 0)
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

    private static <T> TagNetworkSerialization.NetworkPayload serializeToNetwork(Registry<T> registry) {
        Map<ResourceLocation, IntList> map = new HashMap<>();
        registry.getTags().forEach(p_339471_ -> {
            HolderSet<T> holderset = p_339471_.getSecond();
            IntList intlist = new IntArrayList(holderset.size());

            for (Holder<T> holder : holderset) {
                if (holder.kind() != Holder.Kind.REFERENCE) {
                    throw new IllegalStateException("Can't serialize unregistered value " + holder);
                }

                intlist.add(registry.getId(holder.value()));
            }

            map.put(p_339471_.getFirst().location(), intlist);
        });
        return new TagNetworkSerialization.NetworkPayload(map);
    }

    static <T> void deserializeTagsFromNetwork(
        ResourceKey<? extends Registry<T>> registryKey,
        Registry<T> registry,
        TagNetworkSerialization.NetworkPayload networkPayload,
        TagNetworkSerialization.TagOutput<T> output
    ) {
        networkPayload.tags.forEach((p_248278_, p_248279_) -> {
            TagKey<T> tagkey = TagKey.create(registryKey, p_248278_);
            List<Holder<T>> list = p_248279_.intStream().mapToObj(registry::getHolder).flatMap(Optional::stream).collect(Collectors.toUnmodifiableList());
            output.accept(tagkey, list);
        });
    }

    public static final class NetworkPayload {
        final Map<ResourceLocation, IntList> tags;

        NetworkPayload(Map<ResourceLocation, IntList> tags) {
            this.tags = tags;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeMap(this.tags, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeIntIdList);
        }

        public static TagNetworkSerialization.NetworkPayload read(FriendlyByteBuf buffer) {
            return new TagNetworkSerialization.NetworkPayload(buffer.readMap(FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readIntIdList));
        }

        public int size() {
            return this.tags.size();
        }

        public <T> void applyToRegistry(Registry<T> registry) {
            if (this.size() != 0) {
                Map<TagKey<T>, List<Holder<T>>> map = new HashMap<>(this.size());
                TagNetworkSerialization.deserializeTagsFromNetwork(registry.key(), registry, this, map::put);
                registry.bindTags(map);
            }
        }
    }

    @FunctionalInterface
    public interface TagOutput<T> {
        void accept(TagKey<T> key, List<Holder<T>> values);
    }
}
