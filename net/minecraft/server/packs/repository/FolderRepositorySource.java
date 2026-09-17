package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.linkfs.LinkFileSystem;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import org.slf4j.Logger;

public class FolderRepositorySource implements RepositorySource {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final PackSelectionConfig DISCOVERED_PACK_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
    private final Path folder;
    private final PackType packType;
    private final PackSource packSource;
    private final DirectoryValidator validator;

    public FolderRepositorySource(Path folder, PackType packType, PackSource packSource, DirectoryValidator validator) {
        this.folder = folder;
        this.packType = packType;
        this.packSource = packSource;
        this.validator = validator;
    }

    private static String nameFromPath(Path path) {
        return path.getFileName().toString();
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        try {
            FileUtil.createDirectoriesSafe(this.folder);
            discoverPacks(this.folder, this.validator, (p_325639_, p_325640_) -> {
                PackLocationInfo packlocationinfo = this.createDiscoveredFilePackInfo(p_325639_);
                Pack pack = Pack.readMetaAndCreate(packlocationinfo, p_325640_, this.packType, DISCOVERED_PACK_SELECTION_CONFIG);
                if (pack != null) {
                    onLoad.accept(pack);
                }
            });
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to list packs in {}", this.folder, ioexception);
        }
    }

    private PackLocationInfo createDiscoveredFilePackInfo(Path path) {
        String s = nameFromPath(path);
        return new PackLocationInfo("file/" + s, Component.literal(s), this.packSource, Optional.empty());
    }

    public static void discoverPacks(Path folder, DirectoryValidator validator, BiConsumer<Path, Pack.ResourcesSupplier> output) throws IOException {
        FolderRepositorySource.FolderPackDetector folderrepositorysource$folderpackdetector = new FolderRepositorySource.FolderPackDetector(validator);

        try (DirectoryStream<Path> directorystream = Files.newDirectoryStream(folder)) {
            for (Path path : directorystream) {
                try {
                    List<ForbiddenSymlinkInfo> list = new ArrayList<>();
                    Pack.ResourcesSupplier pack$resourcessupplier = folderrepositorysource$folderpackdetector.detectPackResources(path, list);
                    if (!list.isEmpty()) {
                        LOGGER.warn("Ignoring potential pack entry: {}", ContentValidationException.getMessage(path, list));
                    } else if (pack$resourcessupplier != null) {
                        output.accept(path, pack$resourcessupplier);
                    } else {
                        LOGGER.info("Found non-pack entry '{}', ignoring", path);
                    }
                } catch (IOException ioexception) {
                    LOGGER.warn("Failed to read properties of '{}', ignoring", path, ioexception);
                }
            }
        }
    }

    static class FolderPackDetector extends PackDetector<Pack.ResourcesSupplier> {
        protected FolderPackDetector(DirectoryValidator p_296420_) {
            super(p_296420_);
        }

        @Nullable
        protected Pack.ResourcesSupplier createZipPack(Path p_294522_) {
            FileSystem filesystem = p_294522_.getFileSystem();
            if (filesystem != FileSystems.getDefault() && !(filesystem instanceof LinkFileSystem)) {
                FolderRepositorySource.LOGGER.info("Can't open pack archive at {}", p_294522_);
                return null;
            } else {
                return new FilePackResources.FileResourcesSupplier(p_294522_);
            }
        }

        protected Pack.ResourcesSupplier createDirectoryPack(Path p_295493_) {
            return new PathPackResources.PathResourcesSupplier(p_295493_);
        }
    }
}
