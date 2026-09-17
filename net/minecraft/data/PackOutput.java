package net.minecraft.data;

import java.nio.file.Path;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class PackOutput {
    private final Path outputFolder;

    public PackOutput(Path outputFolder) {
        this.outputFolder = outputFolder;
    }

    public Path getOutputFolder() {
        return this.outputFolder;
    }

    public Path getOutputFolder(PackOutput.Target target) {
        return this.getOutputFolder().resolve(target.directory);
    }

    public PackOutput.PathProvider createPathProvider(PackOutput.Target target, String kind) {
        return new PackOutput.PathProvider(this, target, kind);
    }

    public PackOutput.PathProvider createRegistryElementsPathProvider(ResourceKey<? extends Registry<?>> registryKey) {
        return this.createPathProvider(PackOutput.Target.DATA_PACK, Registries.elementsDirPath(registryKey));
    }

    public PackOutput.PathProvider createRegistryTagsPathProvider(ResourceKey<? extends Registry<?>> registryKey) {
        return this.createPathProvider(PackOutput.Target.DATA_PACK, Registries.tagsDirPath(registryKey));
    }

    public static class PathProvider {
        private final Path root;
        private final String kind;

        PathProvider(PackOutput output, PackOutput.Target target, String kind) {
            this.root = output.getOutputFolder(target);
            this.kind = kind;
        }

        public Path file(ResourceLocation location, String extension) {
            return this.root.resolve(location.getNamespace()).resolve(this.kind).resolve(location.getPath() + "." + extension);
        }

        public Path json(ResourceLocation location) {
            return this.root.resolve(location.getNamespace()).resolve(this.kind).resolve(location.getPath() + ".json");
        }
    }

    public static enum Target {
        DATA_PACK("data"),
        RESOURCE_PACK("assets"),
        REPORTS("reports");

        final String directory;

        private Target(String directory) {
            this.directory = directory;
        }
    }
}
