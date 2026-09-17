package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public abstract class BuiltInPackSource implements RepositorySource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String VANILLA_ID = "vanilla";
    public static final KnownPack CORE_PACK_INFO = KnownPack.vanilla("core");
    private final PackType packType;
    private final VanillaPackResources vanillaPack;
    private final ResourceLocation packDir;
    private final DirectoryValidator validator;

    public BuiltInPackSource(PackType packType, VanillaPackResources vanillaPack, ResourceLocation packDir, DirectoryValidator validator) {
        this.packType = packType;
        this.vanillaPack = vanillaPack;
        this.packDir = packDir;
        this.validator = validator;
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        Pack pack = this.createVanillaPack(this.vanillaPack);
        if (pack != null) {
            onLoad.accept(pack);
        }

        this.listBundledPacks(onLoad);
    }

    @Nullable
    protected abstract Pack createVanillaPack(PackResources resources);

    protected abstract Component getPackTitle(String id);

    public VanillaPackResources getVanillaPack() {
        return this.vanillaPack;
    }

    private void listBundledPacks(Consumer<Pack> packConsumer) {
        Map<String, Function<String, Pack>> map = new HashMap<>();
        this.populatePackList(map::put);
        map.forEach((p_250371_, p_250946_) -> {
            Pack pack = p_250946_.apply(p_250371_);
            if (pack != null) {
                packConsumer.accept(pack);
            }
        });
    }

    protected void populatePackList(BiConsumer<String, Function<String, Pack>> populator) {
        this.vanillaPack.listRawPaths(this.packType, this.packDir, p_250248_ -> this.discoverPacksInPath(p_250248_, populator));
    }

    protected void discoverPacksInPath(@Nullable Path directoryPath, BiConsumer<String, Function<String, Pack>> packGetter) {
        if (directoryPath != null && Files.isDirectory(directoryPath)) {
            try {
                FolderRepositorySource.discoverPacks(
                    directoryPath,
                    this.validator,
                    (p_252012_, p_249772_) -> packGetter.accept(
                            pathToId(p_252012_), p_250601_ -> this.createBuiltinPack(p_250601_, p_249772_, this.getPackTitle(p_250601_))
                        )
                );
            } catch (IOException ioexception) {
                LOGGER.warn("Failed to discover packs in {}", directoryPath, ioexception);
            }
        }
    }

    private static String pathToId(Path path) {
        return StringUtils.removeEnd(path.getFileName().toString(), ".zip");
    }

    @Nullable
    protected abstract Pack createBuiltinPack(String id, Pack.ResourcesSupplier resources, Component title);

    public static Pack.ResourcesSupplier fixedResources(final PackResources resources) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo p_326084_) {
                return resources;
            }

            @Override
            public PackResources openFull(PackLocationInfo p_326285_, Pack.Metadata p_326055_) {
                return resources;
            }
        };
    }

    public static Pack.ResourcesSupplier fromName(final Function<PackLocationInfo, PackResources> onName) {
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo p_294636_) {
                return onName.apply(p_294636_);
            }

            @Override
            public PackResources openFull(PackLocationInfo p_251717_, Pack.Metadata p_294956_) {
                return onName.apply(p_251717_);
            }
        };
    }
}
