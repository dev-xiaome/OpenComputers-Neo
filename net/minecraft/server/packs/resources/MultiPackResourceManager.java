package net.minecraft.server.packs.resources;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

public class MultiPackResourceManager implements CloseableResourceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, FallbackResourceManager> namespacedManagers;
    private final List<PackResources> packs;

    public MultiPackResourceManager(PackType type, List<PackResources> packs) {
        this.packs = List.copyOf(packs);
        Map<String, FallbackResourceManager> map = new HashMap<>();
        List<String> list = packs.stream().flatMap(p_215471_ -> p_215471_.getNamespaces(type).stream()).distinct().toList();

        for (PackResources packresources : packs) {
            ResourceFilterSection resourcefiltersection = this.getPackFilterSection(packresources);
            Set<String> set = packresources.getNamespaces(type);
            Predicate<ResourceLocation> predicate = resourcefiltersection != null
                ? p_215474_ -> resourcefiltersection.isPathFiltered(p_215474_.getPath())
                : null;

            for (String s : list) {
                boolean flag = set.contains(s);
                boolean flag1 = resourcefiltersection != null && resourcefiltersection.isNamespaceFiltered(s);
                if (flag || flag1) {
                    FallbackResourceManager fallbackresourcemanager = map.get(s);
                    if (fallbackresourcemanager == null) {
                        fallbackresourcemanager = new FallbackResourceManager(type, s);
                        map.put(s, fallbackresourcemanager);
                    }

                    if (flag && flag1) {
                        fallbackresourcemanager.push(packresources, predicate);
                    } else if (flag) {
                        fallbackresourcemanager.push(packresources);
                    } else {
                        fallbackresourcemanager.pushFilterOnly(packresources.packId(), predicate);
                    }
                }
            }
        }

        this.namespacedManagers = map;
    }

    @Nullable
    private ResourceFilterSection getPackFilterSection(PackResources packResources) {
        try {
            return packResources.getMetadataSection(ResourceFilterSection.TYPE);
        } catch (IOException ioexception) {
            if (!net.neoforged.neoforge.data.loading.DatagenModLoader.isRunningDataGen())// Neo: Only warn about malformed pack filters outside of datagen in case modders are replacing variables with gradle
            LOGGER.error("Failed to get filter section from pack {}", packResources.packId());
            return null;
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return this.namespacedManagers.keySet();
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        ResourceManager resourcemanager = this.namespacedManagers.get(location.getNamespace());
        return resourcemanager != null ? resourcemanager.getResource(location) : Optional.empty();
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation location) {
        ResourceManager resourcemanager = this.namespacedManagers.get(location.getNamespace());
        return resourcemanager != null ? resourcemanager.getResourceStack(location) : List.of();
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        checkTrailingDirectoryPath(path);
        Map<ResourceLocation, Resource> map = new TreeMap<>();

        for (FallbackResourceManager fallbackresourcemanager : this.namespacedManagers.values()) {
            map.putAll(fallbackresourcemanager.listResources(path, filter));
        }

        return map;
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
        checkTrailingDirectoryPath(path);
        Map<ResourceLocation, List<Resource>> map = new TreeMap<>();

        for (FallbackResourceManager fallbackresourcemanager : this.namespacedManagers.values()) {
            map.putAll(fallbackresourcemanager.listResourceStacks(path, filter));
        }

        return map;
    }

    private static void checkTrailingDirectoryPath(String path) {
        if (path.endsWith("/")) {
            throw new IllegalArgumentException("Trailing slash in path " + path);
        }
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.packs.stream();
    }

    @Override
    public void close() {
        this.packs.forEach(PackResources::close);
    }
}
