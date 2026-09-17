package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagNetworkSerialization;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RegistryDataCollector {
    @Nullable
    private RegistryDataCollector.ContentsCollector contentsCollector;
    @Nullable
    private TagCollector tagCollector;

    public void appendContents(ResourceKey<? extends Registry<?>> registryKey, List<RegistrySynchronization.PackedRegistryEntry> registryEntries) {
        if (this.contentsCollector == null) {
            this.contentsCollector = new RegistryDataCollector.ContentsCollector();
        }

        this.contentsCollector.append(registryKey, registryEntries);
    }

    public void appendTags(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags) {
        if (this.tagCollector == null) {
            this.tagCollector = new TagCollector();
        }

        tags.forEach(this.tagCollector::append);
    }

    public RegistryAccess.Frozen collectGameRegistries(ResourceProvider resourceProvider, RegistryAccess registryAccess, boolean isMemoryConnection) {
        LayeredRegistryAccess<ClientRegistryLayer> layeredregistryaccess = ClientRegistryLayer.createRegistryAccess();
        RegistryAccess registryaccess;
        if (this.contentsCollector != null) {
            RegistryAccess.Frozen registryaccess$frozen = layeredregistryaccess.getAccessForLoading(ClientRegistryLayer.REMOTE);
            RegistryAccess.Frozen registryaccess$frozen1 = this.contentsCollector.loadRegistries(resourceProvider, registryaccess$frozen).freeze();
            registryaccess = layeredregistryaccess.replaceFrom(ClientRegistryLayer.REMOTE, registryaccess$frozen1).compositeAccess();
        } else {
            registryaccess = registryAccess;
        }

        if (this.tagCollector != null) {
            this.tagCollector.updateTags(registryaccess, isMemoryConnection);
        }

        return registryaccess.freeze();
    }

    @OnlyIn(Dist.CLIENT)
    static class ContentsCollector {
        private final Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> elements = new HashMap<>();

        public void append(ResourceKey<? extends Registry<?>> registryKey, List<RegistrySynchronization.PackedRegistryEntry> entries) {
            this.elements.computeIfAbsent(registryKey, p_321745_ -> new ArrayList<>()).addAll(entries);
        }

        public RegistryAccess loadRegistries(ResourceProvider resourceProvider, RegistryAccess registryAccess) {
            return RegistryDataLoader.load(this.elements, resourceProvider, registryAccess, RegistryDataLoader.SYNCHRONIZED_REGISTRIES);
        }
    }
}
