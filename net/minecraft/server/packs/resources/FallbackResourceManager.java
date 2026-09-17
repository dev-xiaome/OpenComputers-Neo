package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

public class FallbackResourceManager implements ResourceManager {
    static final Logger LOGGER = LogUtils.getLogger();
    public final List<FallbackResourceManager.PackEntry> fallbacks = Lists.newArrayList();
    private final PackType type;
    private final String namespace;

    public FallbackResourceManager(PackType type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }

    public void push(PackResources resources) {
        this.pushInternal(resources.packId(), resources, null);
    }

    public void push(PackResources resources, Predicate<ResourceLocation> filter) {
        this.pushInternal(resources.packId(), resources, filter);
    }

    public void pushFilterOnly(String name, Predicate<ResourceLocation> filter) {
        this.pushInternal(name, null, filter);
    }

    private void pushInternal(String name, @Nullable PackResources resources, @Nullable Predicate<ResourceLocation> filter) {
        this.fallbacks.add(new FallbackResourceManager.PackEntry(name, resources, filter));
    }

    @Override
    public Set<String> getNamespaces() {
        return ImmutableSet.of(this.namespace);
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            FallbackResourceManager.PackEntry fallbackresourcemanager$packentry = this.fallbacks.get(i);
            PackResources packresources = fallbackresourcemanager$packentry.resources;
            if (packresources != null) {
                IoSupplier<InputStream> iosupplier = packresources.getResource(this.type, location);
                if (iosupplier != null) {
                    IoSupplier<ResourceMetadata> iosupplier1 = this.createStackMetadataFinder(location, i);
                    return Optional.of(createResource(packresources, location, iosupplier, iosupplier1));
                }
            }

            if (fallbackresourcemanager$packentry.isFiltered(location)) {
                LOGGER.warn("Resource {} not found, but was filtered by pack {}", location, fallbackresourcemanager$packentry.name);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static Resource createResource(
        PackResources source, ResourceLocation location, IoSupplier<InputStream> streamSupplier, IoSupplier<ResourceMetadata> metadataSupplier
    ) {
        return new Resource(source, wrapForDebug(location, source, streamSupplier), metadataSupplier);
    }

    private static IoSupplier<InputStream> wrapForDebug(ResourceLocation location, PackResources packResources, IoSupplier<InputStream> stream) {
        return LOGGER.isDebugEnabled()
            ? () -> new FallbackResourceManager.LeakedResourceWarningInputStream(stream.get(), location, packResources.packId())
            : stream;
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation location) {
        ResourceLocation resourcelocation = getMetadataLocation(location);
        List<Resource> list = new ArrayList<>();
        boolean flag = false;
        String s = null;

        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            FallbackResourceManager.PackEntry fallbackresourcemanager$packentry = this.fallbacks.get(i);
            PackResources packresources = fallbackresourcemanager$packentry.resources;
            if (packresources != null) {
                IoSupplier<InputStream> iosupplier = packresources.getResource(this.type, location);
                if (iosupplier != null) {
                    IoSupplier<ResourceMetadata> iosupplier1;
                    if (flag) {
                        iosupplier1 = ResourceMetadata.EMPTY_SUPPLIER;
                    } else {
                        iosupplier1 = () -> {
                            IoSupplier<InputStream> iosupplier2 = packresources.getResource(this.type, resourcelocation);
                            return iosupplier2 != null ? parseMetadata(iosupplier2) : ResourceMetadata.EMPTY;
                        };
                    }

                    list.add(new Resource(packresources, iosupplier, iosupplier1));
                }
            }

            if (fallbackresourcemanager$packentry.isFiltered(location)) {
                s = fallbackresourcemanager$packentry.name;
                break;
            }

            if (fallbackresourcemanager$packentry.isFiltered(resourcelocation)) {
                flag = true;
            }
        }

        if (list.isEmpty() && s != null) {
            LOGGER.warn("Resource {} not found, but was filtered by pack {}", location, s);
        }

        return Lists.reverse(list);
    }

    private static boolean isMetadata(ResourceLocation location) {
        return location.getPath().endsWith(".mcmeta");
    }

    private static ResourceLocation getResourceLocationFromMetadata(ResourceLocation metadataResourceLocation) {
        String s = metadataResourceLocation.getPath().substring(0, metadataResourceLocation.getPath().length() - ".mcmeta".length());
        return metadataResourceLocation.withPath(s);
    }

    static ResourceLocation getMetadataLocation(ResourceLocation location) {
        return location.withPath(location.getPath() + ".mcmeta");
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        record ResourceWithSourceAndIndex(PackResources packResources, IoSupplier<InputStream> resource, int packIndex) {
        }

        Map<ResourceLocation, ResourceWithSourceAndIndex> map = new HashMap<>();
        Map<ResourceLocation, ResourceWithSourceAndIndex> map1 = new HashMap<>();
        int i = this.fallbacks.size();

        for (int j = 0; j < i; j++) {
            FallbackResourceManager.PackEntry fallbackresourcemanager$packentry = this.fallbacks.get(j);
            fallbackresourcemanager$packentry.filterAll(map.keySet());
            fallbackresourcemanager$packentry.filterAll(map1.keySet());
            PackResources packresources = fallbackresourcemanager$packentry.resources;
            if (packresources != null) {
                int k = j;
                packresources.listResources(this.type, this.namespace, path, (p_248254_, p_248255_) -> {
                    if (isMetadata(p_248254_)) {
                        if (filter.test(getResourceLocationFromMetadata(p_248254_))) {
                            map1.put(p_248254_, new ResourceWithSourceAndIndex(packresources, p_248255_, k));
                        }
                    } else if (filter.test(p_248254_)) {
                        map.put(p_248254_, new ResourceWithSourceAndIndex(packresources, p_248255_, k));
                    }
                });
            }
        }

        Map<ResourceLocation, Resource> map2 = Maps.newTreeMap();
        map.forEach(
            (p_248258_, p_248259_) -> {
                ResourceLocation resourcelocation = getMetadataLocation(p_248258_);
                ResourceWithSourceAndIndex fallbackresourcemanager$1resourcewithsourceandindex = map1.get(resourcelocation);
                IoSupplier<ResourceMetadata> iosupplier;
                if (fallbackresourcemanager$1resourcewithsourceandindex != null
                    && fallbackresourcemanager$1resourcewithsourceandindex.packIndex >= p_248259_.packIndex) {
                    iosupplier = convertToMetadata(fallbackresourcemanager$1resourcewithsourceandindex.resource);
                } else {
                    iosupplier = ResourceMetadata.EMPTY_SUPPLIER;
                }

                map2.put(p_248258_, createResource(p_248259_.packResources, p_248258_, p_248259_.resource, iosupplier));
            }
        );
        return map2;
    }

    private IoSupplier<ResourceMetadata> createStackMetadataFinder(ResourceLocation location, int fallbackIndex) {
        return () -> {
            ResourceLocation resourcelocation = getMetadataLocation(location);

            for (int i = this.fallbacks.size() - 1; i >= fallbackIndex; i--) {
                FallbackResourceManager.PackEntry fallbackresourcemanager$packentry = this.fallbacks.get(i);
                PackResources packresources = fallbackresourcemanager$packentry.resources;
                if (packresources != null) {
                    IoSupplier<InputStream> iosupplier = packresources.getResource(this.type, resourcelocation);
                    if (iosupplier != null) {
                        return parseMetadata(iosupplier);
                    }
                }

                if (fallbackresourcemanager$packentry.isFiltered(resourcelocation)) {
                    break;
                }
            }

            return ResourceMetadata.EMPTY;
        };
    }

    private static IoSupplier<ResourceMetadata> convertToMetadata(IoSupplier<InputStream> streamSupplier) {
        return () -> parseMetadata(streamSupplier);
    }

    private static ResourceMetadata parseMetadata(IoSupplier<InputStream> streamSupplier) throws IOException {
        ResourceMetadata resourcemetadata;
        try (InputStream inputstream = streamSupplier.get()) {
            resourcemetadata = ResourceMetadata.fromJsonStream(inputstream);
        }

        return resourcemetadata;
    }

    private static void applyPackFiltersToExistingResources(
        FallbackResourceManager.PackEntry packEntry, Map<ResourceLocation, FallbackResourceManager.EntryStack> resources
    ) {
        for (FallbackResourceManager.EntryStack fallbackresourcemanager$entrystack : resources.values()) {
            if (packEntry.isFiltered(fallbackresourcemanager$entrystack.fileLocation)) {
                fallbackresourcemanager$entrystack.fileSources.clear();
            } else if (packEntry.isFiltered(fallbackresourcemanager$entrystack.metadataLocation())) {
                fallbackresourcemanager$entrystack.metaSources.clear();
            }
        }
    }

    private void listPackResources(
        FallbackResourceManager.PackEntry entry,
        String path,
        Predicate<ResourceLocation> filter,
        Map<ResourceLocation, FallbackResourceManager.EntryStack> output
    ) {
        PackResources packresources = entry.resources;
        if (packresources != null) {
            packresources.listResources(
                this.type,
                this.namespace,
                path,
                (p_248266_, p_248267_) -> {
                    if (isMetadata(p_248266_)) {
                        ResourceLocation resourcelocation = getResourceLocationFromMetadata(p_248266_);
                        if (!filter.test(resourcelocation)) {
                            return;
                        }

                        output.computeIfAbsent(resourcelocation, FallbackResourceManager.EntryStack::new).metaSources.put(packresources, p_248267_);
                    } else {
                        if (!filter.test(p_248266_)) {
                            return;
                        }

                        output.computeIfAbsent(p_248266_, FallbackResourceManager.EntryStack::new)
                            .fileSources
                            .add(new FallbackResourceManager.ResourceWithSource(packresources, p_248267_));
                    }
                }
            );
        }
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, FallbackResourceManager.EntryStack> map = Maps.newHashMap();

        for (FallbackResourceManager.PackEntry fallbackresourcemanager$packentry : this.fallbacks) {
            applyPackFiltersToExistingResources(fallbackresourcemanager$packentry, map);
            this.listPackResources(fallbackresourcemanager$packentry, path, filter, map);
        }

        TreeMap<ResourceLocation, List<Resource>> treemap = Maps.newTreeMap();

        for (FallbackResourceManager.EntryStack fallbackresourcemanager$entrystack : map.values()) {
            if (!fallbackresourcemanager$entrystack.fileSources.isEmpty()) {
                List<Resource> list = new ArrayList<>();

                for (FallbackResourceManager.ResourceWithSource fallbackresourcemanager$resourcewithsource : fallbackresourcemanager$entrystack.fileSources) {
                    PackResources packresources = fallbackresourcemanager$resourcewithsource.source;
                    IoSupplier<InputStream> iosupplier = fallbackresourcemanager$entrystack.metaSources.get(packresources);
                    IoSupplier<ResourceMetadata> iosupplier1 = iosupplier != null ? convertToMetadata(iosupplier) : ResourceMetadata.EMPTY_SUPPLIER;
                    list.add(
                        createResource(
                            packresources, fallbackresourcemanager$entrystack.fileLocation, fallbackresourcemanager$resourcewithsource.resource, iosupplier1
                        )
                    );
                }

                treemap.put(fallbackresourcemanager$entrystack.fileLocation, list);
            }
        }

        return treemap;
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.fallbacks.stream().map(p_215386_ -> p_215386_.resources).filter(Objects::nonNull);
    }

    static record EntryStack(
        ResourceLocation fileLocation,
        ResourceLocation metadataLocation,
        List<FallbackResourceManager.ResourceWithSource> fileSources,
        Map<PackResources, IoSupplier<InputStream>> metaSources
    ) {
        EntryStack(ResourceLocation p_251350_) {
            this(p_251350_, FallbackResourceManager.getMetadataLocation(p_251350_), new ArrayList<>(), new Object2ObjectArrayMap<>());
        }
    }

    static class LeakedResourceWarningInputStream extends FilterInputStream {
        private final Supplier<String> message;
        private boolean closed;

        public LeakedResourceWarningInputStream(InputStream inputStream, ResourceLocation resourceLocation, String packName) {
            super(inputStream);
            Exception exception = new Exception("Stacktrace");
            this.message = () -> {
                StringWriter stringwriter = new StringWriter();
                exception.printStackTrace(new PrintWriter(stringwriter));
                return "Leaked resource: '" + resourceLocation + "' loaded from pack: '" + packName + "'\n" + stringwriter;
            };
        }

        @Override
        public void close() throws IOException {
            super.close();
            this.closed = true;
        }

        @Override
        protected void finalize() throws Throwable {
            if (!this.closed) {
                FallbackResourceManager.LOGGER.warn("{}", this.message.get());
            }

            super.finalize();
        }
    }

    static record PackEntry(String name, @Nullable PackResources resources, @Nullable Predicate<ResourceLocation> filter) {
        public void filterAll(Collection<ResourceLocation> locations) {
            if (this.filter != null) {
                locations.removeIf(this.filter);
            }
        }

        public boolean isFiltered(ResourceLocation location) {
            return this.filter != null && this.filter.test(location);
        }
    }

    static record ResourceWithSource(PackResources source, IoSupplier<InputStream> resource) {
    }
}
