package net.minecraft.server.packs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class FilePackResources extends AbstractPackResources {
    static final Logger LOGGER = LogUtils.getLogger();
    private final FilePackResources.SharedZipFileAccess zipFileAccess;
    private final String prefix;

    public FilePackResources(PackLocationInfo location, FilePackResources.SharedZipFileAccess zipFileAccess, String prefix) {
        super(location);
        this.zipFileAccess = zipFileAccess;
        this.prefix = prefix;
    }

    private static String getPathFromLocation(PackType packType, ResourceLocation location) {
        return String.format(Locale.ROOT, "%s/%s/%s", packType.getDirectory(), location.getNamespace(), location.getPath());
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return this.getResource(String.join("/", elements));
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        return this.getResource(getPathFromLocation(packType, location));
    }

    private String addPrefix(String resourcePath) {
        return this.prefix.isEmpty() ? resourcePath : this.prefix + "/" + resourcePath;
    }

    @Nullable
    private IoSupplier<InputStream> getResource(String resourcePath) {
        ZipFile zipfile = this.zipFileAccess.getOrCreateZipFile();
        if (zipfile == null) {
            return null;
        } else {
            ZipEntry zipentry = zipfile.getEntry(this.addPrefix(resourcePath));
            return zipentry == null ? null : IoSupplier.create(zipfile, zipentry);
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        ZipFile zipfile = this.zipFileAccess.getOrCreateZipFile();
        if (zipfile == null) {
            return Set.of();
        } else {
            Enumeration<? extends ZipEntry> enumeration = zipfile.entries();
            Set<String> set = Sets.newHashSet();
            String s = this.addPrefix(type.getDirectory() + "/");

            while (enumeration.hasMoreElements()) {
                ZipEntry zipentry = enumeration.nextElement();
                String s1 = zipentry.getName();
                String s2 = extractNamespace(s, s1);
                if (!s2.isEmpty()) {
                    if (ResourceLocation.isValidNamespace(s2)) {
                        set.add(s2);
                    } else {
                        LOGGER.warn("Non [a-z0-9_.-] character in namespace {} in pack {}, ignoring", s2, this.zipFileAccess.file);
                    }
                }
            }

            return set;
        }
    }

    @VisibleForTesting
    public static String extractNamespace(String directory, String name) {
        if (!name.startsWith(directory)) {
            return "";
        } else {
            int i = directory.length();
            int j = name.indexOf(47, i);
            return j == -1 ? name.substring(i) : name.substring(i, j);
        }
    }

    @Override
    public void close() {
        this.zipFileAccess.close();
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        ZipFile zipfile = this.zipFileAccess.getOrCreateZipFile();
        if (zipfile != null) {
            Enumeration<? extends ZipEntry> enumeration = zipfile.entries();
            String s = this.addPrefix(packType.getDirectory() + "/" + namespace + "/");
            String s1 = s + path + "/";

            while (enumeration.hasMoreElements()) {
                ZipEntry zipentry = enumeration.nextElement();
                if (!zipentry.isDirectory()) {
                    String s2 = zipentry.getName();
                    if (s2.startsWith(s1)) {
                        String s3 = s2.substring(s.length());
                        ResourceLocation resourcelocation = ResourceLocation.tryBuild(namespace, s3);
                        if (resourcelocation != null) {
                            resourceOutput.accept(resourcelocation, IoSupplier.create(zipfile, zipentry));
                        } else {
                            LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, s3);
                        }
                    }
                }
            }
        }
    }

    public static class FileResourcesSupplier implements Pack.ResourcesSupplier {
        private final File content;

        public FileResourcesSupplier(Path content) {
            this(content.toFile());
        }

        public FileResourcesSupplier(File content) {
            this.content = content;
        }

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            FilePackResources.SharedZipFileAccess filepackresources$sharedzipfileaccess = new FilePackResources.SharedZipFileAccess(this.content);
            return new FilePackResources(location, filepackresources$sharedzipfileaccess, "");
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            FilePackResources.SharedZipFileAccess filepackresources$sharedzipfileaccess = new FilePackResources.SharedZipFileAccess(this.content);
            PackResources packresources = new FilePackResources(location, filepackresources$sharedzipfileaccess, "");
            List<String> list = metadata.overlays();
            if (list.isEmpty()) {
                return packresources;
            } else {
                List<PackResources> list1 = new ArrayList<>(list.size());

                for (String s : list) {
                    list1.add(new FilePackResources(location, filepackresources$sharedzipfileaccess, s));
                }

                return new CompositePackResources(packresources, list1);
            }
        }
    }

    public static class SharedZipFileAccess implements AutoCloseable {
        final File file;
        @Nullable
        private ZipFile zipFile;
        private boolean failedToLoad;

        public SharedZipFileAccess(File file) {
            this.file = file;
        }

        @Nullable
        ZipFile getOrCreateZipFile() {
            if (this.failedToLoad) {
                return null;
            } else {
                if (this.zipFile == null) {
                    try {
                        this.zipFile = new ZipFile(this.file);
                    } catch (IOException ioexception) {
                        FilePackResources.LOGGER.error("Failed to open pack {}", this.file, ioexception);
                        this.failedToLoad = true;
                        return null;
                    }
                }

                return this.zipFile;
            }
        }

        @Override
        public void close() {
            if (this.zipFile != null) {
                IOUtils.closeQuietly(this.zipFile);
                this.zipFile = null;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            this.close();
            super.finalize();
        }
    }
}
