package net.minecraft.server.packs.resources;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface ResourceProvider {
    ResourceProvider EMPTY = p_325641_ -> Optional.empty();

    Optional<Resource> getResource(ResourceLocation location);

    default Resource getResourceOrThrow(ResourceLocation location) throws FileNotFoundException {
        return this.getResource(location).orElseThrow(() -> new FileNotFoundException(location.toString()));
    }

    default InputStream open(ResourceLocation location) throws IOException {
        return this.getResourceOrThrow(location).open();
    }

    default BufferedReader openAsReader(ResourceLocation location) throws IOException {
        return this.getResourceOrThrow(location).openAsReader();
    }

    static ResourceProvider fromMap(Map<ResourceLocation, Resource> resources) {
        return p_248274_ -> Optional.ofNullable(resources.get(p_248274_));
    }
}
