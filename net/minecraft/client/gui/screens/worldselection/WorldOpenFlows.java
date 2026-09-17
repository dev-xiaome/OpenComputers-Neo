package net.minecraft.client.gui.screens.worldselection;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DatapackLoadFailureScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.RecoverWorldDataScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.util.MemoryReserve;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.validation.ContentValidationException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class WorldOpenFlows { // TODO 1.20.3 PORTING: re-add the autoconfirm code
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID WORLD_PACK_ID = UUID.fromString("640a6a92-b6cb-48a0-b391-831586500359");
    private final Minecraft minecraft;
    private final LevelStorageSource levelSource;

    public WorldOpenFlows(Minecraft minecraft, LevelStorageSource levelSource) {
        this.minecraft = minecraft;
        this.levelSource = levelSource;
    }

    public void createFreshLevel(
        String levelName, LevelSettings levelSettings, WorldOptions worldOptions, Function<RegistryAccess, WorldDimensions> dimensionGetter, Screen lastScreen
    ) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        LevelStorageSource.LevelStorageAccess levelstoragesource$levelstorageaccess = this.createWorldAccess(levelName);
        if (levelstoragesource$levelstorageaccess != null) {
            PackRepository packrepository = ServerPacksSource.createPackRepository(levelstoragesource$levelstorageaccess);
            WorldDataConfiguration worlddataconfiguration = levelSettings.getDataConfiguration();

            try {
                WorldLoader.PackConfig worldloader$packconfig = new WorldLoader.PackConfig(packrepository, worlddataconfiguration, false, false);
                WorldStem worldstem = this.loadWorldDataBlocking(
                    worldloader$packconfig,
                    p_258145_ -> {
                        WorldDimensions.Complete worlddimensions$complete = dimensionGetter.apply(p_258145_.datapackWorldgen())
                            .bake(p_258145_.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
                        return new WorldLoader.DataLoadOutput<>(
                            new PrimaryLevelData(levelSettings, worldOptions, worlddimensions$complete.specialWorldProperty(), worlddimensions$complete.lifecycle()),
                            worlddimensions$complete.dimensionsRegistryAccess()
                        );
                    },
                    WorldStem::new
                );
                this.minecraft.doWorldLoad(levelstoragesource$levelstorageaccess, packrepository, worldstem, true);
            } catch (Exception exception) {
                LOGGER.warn("Failed to load datapacks, can't proceed with server load", (Throwable)exception);
                levelstoragesource$levelstorageaccess.safeClose();
                this.minecraft.setScreen(lastScreen);
            }
        }
    }

    @Nullable
    private LevelStorageSource.LevelStorageAccess createWorldAccess(String levelName) {
        try {
            return this.levelSource.validateAndCreateAccess(levelName);
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to read level {} data", levelName, ioexception);
            SystemToast.onWorldAccessFailure(this.minecraft, levelName);
            this.minecraft.setScreen(null);
            return null;
        } catch (ContentValidationException contentvalidationexception) {
            LOGGER.warn("{}", contentvalidationexception.getMessage());
            this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(null)));
            return null;
        }
    }

    public void createLevelFromExistingSettings(
        LevelStorageSource.LevelStorageAccess levelStorage,
        ReloadableServerResources resources,
        LayeredRegistryAccess<RegistryLayer> registries,
        WorldData worldData
    ) {
        PackRepository packrepository = ServerPacksSource.createPackRepository(levelStorage);
        CloseableResourceManager closeableresourcemanager = new WorldLoader.PackConfig(packrepository, worldData.getDataConfiguration(), false, false)
            .createResourceManager()
            .getSecond();
        this.minecraft.doWorldLoad(levelStorage, packrepository, new WorldStem(closeableresourcemanager, resources, registries, worldData), true);
    }

    public WorldStem loadWorldStem(Dynamic<?> dynamic, boolean safeMode, PackRepository packRepository) throws Exception {
        WorldLoader.PackConfig worldloader$packconfig = LevelStorageSource.getPackConfig(dynamic, packRepository, safeMode);
        return this.loadWorldDataBlocking(
            worldloader$packconfig,
            p_307082_ -> {
                Registry<LevelStem> registry = p_307082_.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
                LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dynamic, p_307082_.dataConfiguration(), registry, p_307082_.datapackWorldgen()
                );
                return new WorldLoader.DataLoadOutput<>(leveldataanddimensions.worldData(), leveldataanddimensions.dimensions().dimensionsRegistryAccess());
            },
            WorldStem::new
        );
    }

    public Pair<LevelSettings, WorldCreationContext> recreateWorldData(LevelStorageSource.LevelStorageAccess levelStorage) throws Exception {
        PackRepository packrepository = ServerPacksSource.createPackRepository(levelStorage);
        Dynamic<?> dynamic = levelStorage.getDataTag();
        WorldLoader.PackConfig worldloader$packconfig = LevelStorageSource.getPackConfig(dynamic, packrepository, false);

        @OnlyIn(Dist.CLIENT)
        record Data(LevelSettings levelSettings, WorldOptions options, Registry<LevelStem> existingDimensions) {
        }

        return this.<Data, Pair<LevelSettings, WorldCreationContext>>loadWorldDataBlocking(
            worldloader$packconfig,
            p_307097_ -> {
                Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
                LevelDataAndDimensions leveldataanddimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dynamic, p_307097_.dataConfiguration(), registry, p_307097_.datapackWorldgen()
                );
                return new WorldLoader.DataLoadOutput<>(
                    new Data(
                        leveldataanddimensions.worldData().getLevelSettings(),
                        leveldataanddimensions.worldData().worldGenOptions(),
                        leveldataanddimensions.dimensions().dimensions()
                    ),
                    p_307097_.datapackDimensions()
                );
            },
            (p_247840_, p_247841_, p_247842_, p_247843_) -> {
                p_247840_.close();
                return Pair.of(
                    p_247843_.levelSettings,
                    new WorldCreationContext(
                        p_247843_.options,
                        new WorldDimensions(p_247843_.existingDimensions),
                        p_247842_,
                        p_247841_,
                        p_247843_.levelSettings.getDataConfiguration()
                    )
                );
            }
        );
    }

    private <D, R> R loadWorldDataBlocking(
        WorldLoader.PackConfig packConfig, WorldLoader.WorldDataSupplier<D> worldDataSupplier, WorldLoader.ResultFactory<D, R> resultFactory
    ) throws Exception {
        WorldLoader.InitConfig worldloader$initconfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED, 2);
        CompletableFuture<R> completablefuture = WorldLoader.load(worldloader$initconfig, worldDataSupplier, resultFactory, Util.backgroundExecutor(), this.minecraft);
        this.minecraft.managedBlock(completablefuture::isDone);
        return completablefuture.get();
    }

    private void askForBackup(LevelStorageSource.LevelStorageAccess levelStorage, boolean customized, Runnable loadLevel, Runnable onCancel) {
        Component component;
        Component component1;
        if (customized) {
            component = Component.translatable("selectWorld.backupQuestion.customized");
            component1 = Component.translatable("selectWorld.backupWarning.customized");
        } else {
            component = Component.translatable("selectWorld.backupQuestion.experimental");
            // Neo: Add a line saying that the message won't show again.
            component1 = Component.translatable("selectWorld.backupWarning.experimental")
                    .append("\n\n")
                    .append(Component.translatable("neoforge.selectWorld.backupWarning.experimental.additional"));
        }

        this.minecraft.setScreen(new BackupConfirmScreen(onCancel, (p_307085_, p_307086_) -> {
            if (p_307085_) {
                EditWorldScreen.makeBackupAndShowToast(levelStorage);
            }

            loadLevel.run();
        }, component, component1, false));
    }

    public static void confirmWorldCreation(Minecraft minecraft, CreateWorldScreen screen, Lifecycle lifecycle, Runnable loadWorld, boolean skipWarnings) {
        BooleanConsumer booleanconsumer = p_233154_ -> {
            if (p_233154_) {
                loadWorld.run();
            } else {
                minecraft.setScreen(screen);
            }
        };
        if (skipWarnings || lifecycle == Lifecycle.stable()) {
            loadWorld.run();
        } else if (lifecycle == Lifecycle.experimental()) {
            minecraft.setScreen(
                new ConfirmScreen(
                    booleanconsumer,
                    Component.translatable("selectWorld.warning.experimental.title"),
                    Component.translatable("selectWorld.warning.experimental.question")
                )
            );
        } else {
            minecraft.setScreen(
                new ConfirmScreen(
                    booleanconsumer,
                    Component.translatable("selectWorld.warning.deprecated.title"),
                    Component.translatable("selectWorld.warning.deprecated.question")
                )
            );
        }
    }

    public void openWorld(String worldName, Runnable onFail) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        LevelStorageSource.LevelStorageAccess levelstoragesource$levelstorageaccess = this.createWorldAccess(worldName);
        if (levelstoragesource$levelstorageaccess != null) {
            this.openWorldLoadLevelData(levelstoragesource$levelstorageaccess, onFail);
        }
    }

    private void openWorldLoadLevelData(LevelStorageSource.LevelStorageAccess levelStorage, Runnable onFail) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));

        Dynamic<?> dynamic;
        LevelSummary levelsummary;
        try {
            dynamic = levelStorage.getDataTag();
            levelsummary = levelStorage.getSummary(dynamic);
            levelStorage.readAdditionalLevelSaveData(false);
        } catch (NbtException | ReportedNbtException | IOException ioexception) {
            this.minecraft.setScreen(new RecoverWorldDataScreen(this.minecraft, p_329781_ -> {
                if (p_329781_) {
                    this.openWorldLoadLevelData(levelStorage, onFail);
                } else {
                    levelStorage.safeClose();
                    onFail.run();
                }
            }, levelStorage));
            return;
        } catch (OutOfMemoryError outofmemoryerror1) {
            MemoryReserve.release();
            System.gc();
            String s = "Ran out of memory trying to read level data of world folder \"" + levelStorage.getLevelId() + "\"";
            LOGGER.error(LogUtils.FATAL_MARKER, s);
            OutOfMemoryError outofmemoryerror = new OutOfMemoryError("Ran out of memory reading level data");
            outofmemoryerror.initCause(outofmemoryerror1);
            CrashReport crashreport = CrashReport.forThrowable(outofmemoryerror, s);
            CrashReportCategory crashreportcategory = crashreport.addCategory("World details");
            crashreportcategory.setDetail("World folder", levelStorage.getLevelId());
            throw new ReportedException(crashreport);
        }

        this.openWorldCheckVersionCompatibility(levelStorage, levelsummary, dynamic, onFail);
    }

    private void openWorldCheckVersionCompatibility(
        LevelStorageSource.LevelStorageAccess levelStorage, LevelSummary levelSummary, Dynamic<?> levelData, Runnable onFail
    ) {
        if (!levelSummary.isCompatible()) {
            levelStorage.safeClose();
            this.minecraft
                .setScreen(
                    new AlertScreen(
                        onFail,
                        Component.translatable("selectWorld.incompatible.title").withColor(-65536),
                        Component.translatable("selectWorld.incompatible.description", levelSummary.getWorldVersionName())
                    )
                );
        } else {
            LevelSummary.BackupStatus levelsummary$backupstatus = levelSummary.backupStatus();
            if (levelsummary$backupstatus.shouldBackup()) {
                String s = "selectWorld.backupQuestion." + levelsummary$backupstatus.getTranslationKey();
                String s1 = "selectWorld.backupWarning." + levelsummary$backupstatus.getTranslationKey();
                MutableComponent mutablecomponent = Component.translatable(s);
                if (levelsummary$backupstatus.isSevere()) {
                    mutablecomponent.withColor(-2142128);
                }

                Component component = Component.translatable(s1, levelSummary.getWorldVersionName(), SharedConstants.getCurrentVersion().getName());
                this.minecraft.setScreen(new BackupConfirmScreen(() -> {
                    levelStorage.safeClose();
                    onFail.run();
                }, (p_329770_, p_329771_) -> {
                    if (p_329770_) {
                        EditWorldScreen.makeBackupAndShowToast(levelStorage);
                    }

                    this.openWorldLoadLevelStem(levelStorage, levelData, false, onFail);
                }, mutablecomponent, component, false));
            } else {
                this.openWorldLoadLevelStem(levelStorage, levelData, false, onFail);
            }
        }
    }

    private void openWorldLoadLevelStem(LevelStorageSource.LevelStorageAccess levelStorage, Dynamic<?> levelData, boolean safeMode, Runnable onFail) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.resource_load")));
        PackRepository packrepository = ServerPacksSource.createPackRepository(levelStorage);

        WorldStem worldstem;
        try {
            worldstem = this.loadWorldStem(levelData, safeMode, packrepository);

            for (LevelStem levelstem : worldstem.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM)) {
                levelstem.generator().validate();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load level data or datapacks, can't proceed with server load", (Throwable)exception);
            if (!safeMode) {
                this.minecraft.setScreen(new DatapackLoadFailureScreen(() -> {
                    levelStorage.safeClose();
                    onFail.run();
                }, () -> this.openWorldLoadLevelStem(levelStorage, levelData, true, onFail)));
            } else {
                levelStorage.safeClose();
                this.minecraft
                    .setScreen(
                        new AlertScreen(
                            onFail,
                            Component.translatable("datapackFailure.safeMode.failed.title"),
                            Component.translatable("datapackFailure.safeMode.failed.description"),
                            CommonComponents.GUI_BACK,
                            true
                        )
                    );
            }

            return;
        }

        this.openWorldCheckWorldStemCompatibility(levelStorage, worldstem, packrepository, onFail);
    }

    private void openWorldCheckWorldStemCompatibility(
        LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, PackRepository packRepository, Runnable onFail
    ) {
        WorldData worlddata = worldStem.worldData();
        boolean flag = worlddata.worldGenOptions().isOldCustomizedWorld();
        boolean flag1 = worlddata.worldGenSettingsLifecycle() != Lifecycle.stable();
        boolean skipConfirmation = worlddata instanceof PrimaryLevelData pld && pld.hasConfirmedExperimentalWarning();
        if (skipConfirmation || (!flag && !flag1)) {
            this.openWorldLoadBundledResourcePack(levelStorage, worldStem, packRepository, onFail);
        } else {
            this.askForBackup(levelStorage, flag, () -> {
                if (!flag) {
                    // Neo: Prevent the message from showing again
                    if (worldStem.worldData() instanceof PrimaryLevelData pld) {
                        pld.withConfirmedWarning(true);
                    }
                }
                this.openWorldLoadBundledResourcePack(levelStorage, worldStem, packRepository, onFail);
            }, () -> {
                worldStem.close();
                levelStorage.safeClose();
                onFail.run();
            });
        }
    }

    private void openWorldLoadBundledResourcePack(
        LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, PackRepository packRepository, Runnable onFail
    ) {
        DownloadedPackSource downloadedpacksource = this.minecraft.getDownloadedPackSource();
        this.loadBundledResourcePack(downloadedpacksource, levelStorage).thenApply(p_233177_ -> true).exceptionallyComposeAsync(p_233183_ -> {
            LOGGER.warn("Failed to load pack: ", p_233183_);
            return this.promptBundledPackLoadFailure();
        }, this.minecraft).thenAcceptAsync(p_329766_ -> {
            if (p_329766_) {
                this.openWorldCheckDiskSpace(levelStorage, worldStem, downloadedpacksource, packRepository, onFail);
            } else {
                downloadedpacksource.popAll();
                worldStem.close();
                levelStorage.safeClose();
                onFail.run();
            }
        }, this.minecraft).exceptionally(p_233175_ -> {
            this.minecraft.delayCrash(CrashReport.forThrowable(p_233175_, "Load world"));
            return null;
        });
    }

    private void openWorldCheckDiskSpace(
        LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, DownloadedPackSource packSource, PackRepository packRepository, Runnable onFail
    ) {
        if (levelStorage.checkForLowDiskSpace()) {
            this.minecraft
                .setScreen(
                    new ConfirmScreen(
                        p_329757_ -> {
                            if (p_329757_) {
                                this.openWorldDoLoad(levelStorage, worldStem, packRepository);
                            } else {
                                packSource.popAll();
                                worldStem.close();
                                levelStorage.safeClose();
                                onFail.run();
                            }
                        },
                        Component.translatable("selectWorld.warning.lowDiskSpace.title").withStyle(ChatFormatting.RED),
                        Component.translatable("selectWorld.warning.lowDiskSpace.description"),
                        CommonComponents.GUI_CONTINUE,
                        CommonComponents.GUI_BACK
                    )
                );
        } else {
            this.openWorldDoLoad(levelStorage, worldStem, packRepository);
        }
    }

    private void openWorldDoLoad(LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, PackRepository packRepository) {
        this.minecraft.doWorldLoad(levelStorage, packRepository, worldStem, false);
    }

    private CompletableFuture<Void> loadBundledResourcePack(DownloadedPackSource packSource, LevelStorageSource.LevelStorageAccess level) {
        Path path = level.getLevelPath(LevelResource.MAP_RESOURCE_FILE);
        if (Files.exists(path) && !Files.isDirectory(path)) {
            packSource.configureForLocalWorld();
            CompletableFuture<Void> completablefuture = packSource.waitForPackFeedback(WORLD_PACK_ID);
            packSource.pushLocalPack(WORLD_PACK_ID, path);
            return completablefuture;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Boolean> promptBundledPackLoadFailure() {
        CompletableFuture<Boolean> completablefuture = new CompletableFuture<>();
        this.minecraft
            .setScreen(
                new ConfirmScreen(
                    completablefuture::complete,
                    Component.translatable("multiplayer.texturePrompt.failure.line1"),
                    Component.translatable("multiplayer.texturePrompt.failure.line2"),
                    CommonComponents.GUI_PROCEED,
                    CommonComponents.GUI_CANCEL
                )
            );
        return completablefuture;
    }
}
