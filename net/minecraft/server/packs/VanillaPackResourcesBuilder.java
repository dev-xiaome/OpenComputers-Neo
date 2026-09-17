package net.minecraft.server.packs;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.Util;
import org.slf4j.Logger;

public class VanillaPackResourcesBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static Consumer<VanillaPackResourcesBuilder> developmentConfig = p_251787_ -> {
    };
    private static final Map<PackType, Path> ROOT_DIR_BY_TYPE = Util.make(() -> {
        synchronized (VanillaPackResources.class) {
            Builder<PackType, Path> builder = ImmutableMap.builder();

            for (PackType packtype : PackType.values()) {
                String s = "/" + packtype.getDirectory() + "/.mcassetsroot";
                URL url = VanillaPackResources.class.getResource(s);
                if (url == null) {
                    LOGGER.error("File {} does not exist in classpath", s);
                } else {
                    try {
                        URI uri = url.toURI();
                        String s1 = uri.getScheme();
                        if (!"jar".equals(s1) && !"file".equals(s1)) {
                            LOGGER.warn("Assets URL '{}' uses unexpected schema", uri);
                        }

                        Path path = safeGetPath(uri);
                        builder.put(packtype, path.getParent());
                    } catch (Exception exception) {
                        LOGGER.error("Couldn't resolve path to vanilla assets", (Throwable)exception);
                    }
                }
            }

            return builder.build();
        }
    });
    private final Set<Path> rootPaths = new LinkedHashSet<>();
    private final Map<PackType, Set<Path>> pathsForType = new EnumMap<>(PackType.class);
    private BuiltInMetadata metadata = BuiltInMetadata.of();
    private final Set<String> namespaces = new HashSet<>();

    private static Path safeGetPath(URI uri) throws IOException {
        try {
            return Paths.get(uri);
        } catch (FileSystemNotFoundException filesystemnotfoundexception) {
        } catch (Throwable throwable) {
            LOGGER.warn("Unable to get path for: {}", uri, throwable);
        }

        try {
            FileSystems.newFileSystem(uri, Collections.emptyMap());
        } catch (FileSystemAlreadyExistsException filesystemalreadyexistsexception) {
        }

        return Paths.get(uri);
    }

    private boolean validateDirPath(Path path) {
        if (!Files.exists(path)) {
            return false;
        } else if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path " + path.toAbsolutePath() + " is not directory");
        } else {
            return true;
        }
    }

    private void pushRootPath(Path rootPath) {
        if (this.validateDirPath(rootPath)) {
            this.rootPaths.add(rootPath);
        }
    }

    private void pushPathForType(PackType packType, Path path) {
        if (this.validateDirPath(path)) {
            this.pathsForType.computeIfAbsent(packType, p_250639_ -> new LinkedHashSet<>()).add(path);
        }
    }

    public VanillaPackResourcesBuilder pushJarResources() {
        ROOT_DIR_BY_TYPE.forEach((p_251514_, p_251979_) -> {
            this.pushRootPath(p_251979_.getParent());
            this.pushPathForType(p_251514_, p_251979_);
        });
        return this;
    }

    public VanillaPackResourcesBuilder pushClasspathResources(PackType packType, Class<?> clazz) {
        Enumeration<URL> enumeration = null;

        try {
            enumeration = clazz.getClassLoader().getResources(packType.getDirectory() + "/");
        } catch (IOException ioexception) {
        }

        while (enumeration != null && enumeration.hasMoreElements()) {
            URL url = enumeration.nextElement();

            try {
                URI uri = url.toURI();
                if ("file".equals(uri.getScheme())) {
                    Path path = Paths.get(uri);
                    this.pushRootPath(path.getParent());
                    this.pushPathForType(packType, path);
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to extract path from {}", url, exception);
            }
        }

        return this;
    }

    public VanillaPackResourcesBuilder applyDevelopmentConfig() {
        developmentConfig.accept(this);
        return this;
    }

    public VanillaPackResourcesBuilder pushUniversalPath(Path path) {
        this.pushRootPath(path);

        for (PackType packtype : PackType.values()) {
            this.pushPathForType(packtype, path.resolve(packtype.getDirectory()));
        }

        return this;
    }

    public VanillaPackResourcesBuilder pushAssetPath(PackType packType, Path path) {
        this.pushRootPath(path);
        this.pushPathForType(packType, path);
        return this;
    }

    public VanillaPackResourcesBuilder setMetadata(BuiltInMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public VanillaPackResourcesBuilder exposeNamespace(String... namespaces) {
        this.namespaces.addAll(Arrays.asList(namespaces));
        return this;
    }

    public VanillaPackResources build(PackLocationInfo location) {
        Map<PackType, List<Path>> map = new EnumMap<>(PackType.class);

        for (PackType packtype : PackType.values()) {
            List<Path> list = copyAndReverse(this.pathsForType.getOrDefault(packtype, Set.of()));
            map.put(packtype, list);
        }

        return new VanillaPackResources(location, this.metadata, Set.copyOf(this.namespaces), copyAndReverse(this.rootPaths), map);
    }

    private static List<Path> copyAndReverse(Collection<Path> paths) {
        List<Path> list = new ArrayList<>(paths);
        Collections.reverse(list);
        return List.copyOf(list);
    }
}
