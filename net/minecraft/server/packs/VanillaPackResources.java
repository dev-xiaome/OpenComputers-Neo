package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult.Error;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;

public class VanillaPackResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final BuiltInMetadata metadata;
    private final Set<String> namespaces;
    private final List<Path> rootPaths;
    private final Map<PackType, List<Path>> pathsForType;

    VanillaPackResources(
        PackLocationInfo location, BuiltInMetadata metadata, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> pathsForType
    ) {
        this.location = location;
        this.metadata = metadata;
        this.namespaces = namespaces;
        this.rootPaths = rootPaths;
        this.pathsForType = pathsForType;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        FileUtil.validatePath(elements);
        List<String> list = List.of(elements);

        for (Path path : this.rootPaths) {
            Path path1 = FileUtil.resolvePath(path, list);
            if (Files.exists(path1) && PathPackResources.validatePath(path1)) {
                return IoSupplier.create(path1);
            }
        }

        return null;
    }

    public void listRawPaths(PackType packType, ResourceLocation packLocation, Consumer<Path> output) {
        FileUtil.decomposePath(packLocation.getPath()).ifSuccess(p_248238_ -> {
            String s = packLocation.getNamespace();

            for (Path path : this.pathsForType.get(packType)) {
                Path path1 = path.resolve(s);
                output.accept(FileUtil.resolvePath(path1, (List<String>)p_248238_));
            }
        }).ifError(p_337562_ -> LOGGER.error("Invalid path {}: {}", packLocation, p_337562_.message()));
    }

    @Override
    public void listResources(PackType packType, String namespace, String p_path, PackResources.ResourceOutput resourceOutput) {
        FileUtil.decomposePath(p_path).ifSuccess(p_248228_ -> {
            List<Path> list = this.pathsForType.get(packType);
            int i = list.size();
            if (i == 1) {
                getResources(resourceOutput, namespace, list.get(0), (List<String>)p_248228_);
            } else if (i > 1) {
                Map<ResourceLocation, IoSupplier<InputStream>> map = new HashMap<>();

                for (int j = 0; j < i - 1; j++) {
                    getResources(map::putIfAbsent, namespace, list.get(j), (List<String>)p_248228_);
                }

                Path path = list.get(i - 1);
                if (map.isEmpty()) {
                    getResources(resourceOutput, namespace, path, (List<String>)p_248228_);
                } else {
                    getResources(map::putIfAbsent, namespace, path, (List<String>)p_248228_);
                    map.forEach(resourceOutput);
                }
            }
        }).ifError(p_337564_ -> LOGGER.error("Invalid path {}: {}", p_path, p_337564_.message()));
    }

    private static void getResources(PackResources.ResourceOutput resourceOutput, String namespace, Path root, List<String> paths) {
        Path path = root.resolve(namespace);
        PathPackResources.listPath(namespace, path, paths, resourceOutput);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        return FileUtil.decomposePath(location.getPath()).mapOrElse(p_248224_ -> {
            String s = location.getNamespace();

            for (Path path : this.pathsForType.get(packType)) {
                Path path1 = FileUtil.resolvePath(path.resolve(s), (List<String>)p_248224_);
                if (Files.exists(path1) && PathPackResources.validatePath(path1)) {
                    return IoSupplier.create(path1);
                }
            }

            return null;
        }, p_337566_ -> {
            LOGGER.error("Invalid path {}: {}", location, p_337566_.message());
            return null;
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return this.namespaces;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        IoSupplier<InputStream> iosupplier = this.getRootResource("pack.mcmeta");
        if (iosupplier != null) {
            try (InputStream inputstream = iosupplier.get()) {
                T t = AbstractPackResources.getMetadataFromStream(deserializer, inputstream);
                if (t != null) {
                    return t;
                }

                return this.metadata.get(deserializer);
            } catch (IOException ioexception) {
            }
        }

        return this.metadata.get(deserializer);
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }

    public ResourceProvider asProvider() {
        return p_248239_ -> Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, p_248239_))
                .map(p_248221_ -> new Resource(this, (IoSupplier<InputStream>)p_248221_));
    }
}
