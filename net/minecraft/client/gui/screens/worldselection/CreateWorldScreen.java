package net.minecraft.client.gui.screens.worldselection;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class CreateWorldScreen extends Screen {
    private static final int GROUP_BOTTOM = 1;
    private static final int TAB_COLUMN_WIDTH = 210;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TEMP_WORLD_PREFIX = "mcworld-";
    static final Component GAME_MODEL_LABEL = Component.translatable("selectWorld.gameMode");
    static final Component NAME_LABEL = Component.translatable("selectWorld.enterName");
    static final Component EXPERIMENTS_LABEL = Component.translatable("selectWorld.experiments");
    static final Component ALLOW_COMMANDS_INFO = Component.translatable("selectWorld.allowCommands.info");
    private static final Component PREPARING_WORLD_DATA = Component.translatable("createWorld.preparing");
    private static final int HORIZONTAL_BUTTON_SPACING = 10;
    private static final int VERTICAL_BUTTON_SPACING = 8;
    public static final ResourceLocation TAB_HEADER_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/tab_header_background.png");
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    final WorldCreationUiState uiState;
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, p_321370_ -> this.removeWidget(p_321370_));
    private boolean recreated;
    private final DirectoryValidator packValidator;
    @Nullable
    private final Screen lastScreen;
    @Nullable
    private Path tempDataPackDir;
    @Nullable
    private PackRepository tempDataPackRepository;
    @Nullable
    private TabNavigationBar tabNavigationBar;

    public static void openFresh(Minecraft minecraft, @Nullable Screen lastScreen) {
        queueLoadScreen(minecraft, PREPARING_WORLD_DATA);
        PackRepository packrepository = new PackRepository(new ServerPacksSource(minecraft.directoryValidator()));
        net.neoforged.neoforge.resource.ResourcePackLoader.populatePackRepository(packrepository, net.minecraft.server.packs.PackType.SERVER_DATA, false);
        WorldLoader.InitConfig worldloader$initconfig = createDefaultLoadConfig(packrepository, WorldDataConfiguration.DEFAULT);
        CompletableFuture<WorldCreationContext> completablefuture = WorldLoader.load(
            worldloader$initconfig,
            p_247792_ -> new WorldLoader.DataLoadOutput<>(
                    new CreateWorldScreen.DataPackReloadCookie(
                        new WorldGenSettings(WorldOptions.defaultWithRandomSeed(), WorldPresets.createNormalWorldDimensions(p_247792_.datapackWorldgen())),
                        p_247792_.dataConfiguration()
                    ),
                    p_247792_.datapackDimensions()
                ),
            (p_247798_, p_247799_, p_247800_, p_247801_) -> {
                p_247798_.close();
                return new WorldCreationContext(p_247801_.worldGenSettings(), p_247800_, p_247799_, p_247801_.dataConfiguration());
            },
            Util.backgroundExecutor(),
            minecraft
        );
        minecraft.managedBlock(completablefuture::isDone);
        minecraft.setScreen(new CreateWorldScreen(minecraft, lastScreen, completablefuture.join(), Optional.of(WorldPresets.NORMAL), OptionalLong.empty()));
    }

    public static CreateWorldScreen createFromExisting(
        Minecraft minecraft, @Nullable Screen lastScreen, LevelSettings levelSettings, WorldCreationContext settings, @Nullable Path tempDataPackDir
    ) {
        CreateWorldScreen createworldscreen = new CreateWorldScreen(
            minecraft, lastScreen, settings, WorldPresets.fromSettings(settings.selectedDimensions()), OptionalLong.of(settings.options().seed())
        );
        createworldscreen.recreated = true;
        createworldscreen.uiState.setName(levelSettings.levelName());
        createworldscreen.uiState.setAllowCommands(levelSettings.allowCommands());
        createworldscreen.uiState.setDifficulty(levelSettings.difficulty());
        createworldscreen.uiState.getGameRules().assignFrom(levelSettings.gameRules(), null);
        if (levelSettings.hardcore()) {
            createworldscreen.uiState.setGameMode(WorldCreationUiState.SelectedGameMode.HARDCORE);
        } else if (levelSettings.gameType().isSurvival()) {
            createworldscreen.uiState.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
        } else if (levelSettings.gameType().isCreative()) {
            createworldscreen.uiState.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        }

        createworldscreen.tempDataPackDir = tempDataPackDir;
        return createworldscreen;
    }

    private CreateWorldScreen(
        Minecraft minecraft, @Nullable Screen lastScreen, WorldCreationContext settings, Optional<ResourceKey<WorldPreset>> preset, OptionalLong seed
    ) {
        super(Component.translatable("selectWorld.create"));
        this.lastScreen = lastScreen;
        this.packValidator = minecraft.directoryValidator();
        this.uiState = new WorldCreationUiState(minecraft.getLevelSource().getBaseDir(), settings, preset, seed);
    }

    public WorldCreationUiState getUiState() {
        return this.uiState;
    }

    @Override
    protected void init() {
        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
            .addTabs(new CreateWorldScreen.GameTab(), new CreateWorldScreen.WorldTab(), new CreateWorldScreen.MoreTab())
            .build();
        this.addRenderableWidget(this.tabNavigationBar);
        LinearLayout linearlayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        linearlayout.addChild(Button.builder(Component.translatable("selectWorld.create"), p_232938_ -> this.onCreate()).build());
        linearlayout.addChild(Button.builder(CommonComponents.GUI_CANCEL, p_232903_ -> this.popScreen()).build());
        this.layout.visitWidgets(p_267851_ -> {
            p_267851_.setTabOrderGroup(1);
            this.addRenderableWidget(p_267851_);
        });
        this.tabNavigationBar.selectTab(0, false);
        this.uiState.onChanged();
        this.repositionElements();
    }

    @Override
    protected void setInitialFocus() {
    }

    @Override
    public void repositionElements() {
        if (this.tabNavigationBar != null) {
            this.tabNavigationBar.setWidth(this.width);
            this.tabNavigationBar.arrangeElements();
            int i = this.tabNavigationBar.getRectangle().bottom();
            ScreenRectangle screenrectangle = new ScreenRectangle(0, i, this.width, this.height - this.layout.getFooterHeight() - i);
            this.tabManager.setTabArea(screenrectangle);
            this.layout.setHeaderHeight(i);
            this.layout.arrangeElements();
        }
    }

    private static void queueLoadScreen(Minecraft minecraft, Component title) {
        minecraft.forceSetScreen(new GenericMessageScreen(title));
    }

    private void onCreate() {
        WorldCreationContext worldcreationcontext = this.uiState.getSettings();
        WorldDimensions.Complete worlddimensions$complete = worldcreationcontext.selectedDimensions().bake(worldcreationcontext.datapackDimensions());
        LayeredRegistryAccess<RegistryLayer> layeredregistryaccess = worldcreationcontext.worldgenRegistries()
            .replaceFrom(RegistryLayer.DIMENSIONS, worlddimensions$complete.dimensionsRegistryAccess());
        Lifecycle lifecycle = FeatureFlags.isExperimental(worldcreationcontext.dataConfiguration().enabledFeatures())
            ? Lifecycle.experimental()
            : Lifecycle.stable();
        Lifecycle lifecycle1 = layeredregistryaccess.compositeAccess().allRegistriesLifecycle();
        Lifecycle lifecycle2 = lifecycle1.add(lifecycle);
        boolean flag = !this.recreated && lifecycle1 == Lifecycle.stable();
        WorldOpenFlows.confirmWorldCreation(
            this.minecraft,
            this,
            lifecycle2,
            () -> this.createNewWorld(worlddimensions$complete.specialWorldProperty(), layeredregistryaccess, lifecycle2),
            flag
        );
    }

    private void createNewWorld(PrimaryLevelData.SpecialWorldProperty specialWorldProperty, LayeredRegistryAccess<RegistryLayer> p_249152_, Lifecycle worldGenSettingsLifecycle) {
        queueLoadScreen(this.minecraft, PREPARING_WORLD_DATA);
        Optional<LevelStorageSource.LevelStorageAccess> optional = this.createNewWorldDirectory();
        if (!optional.isEmpty()) {
            this.removeTempDataPackDir();
            boolean flag = specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.DEBUG;
            WorldCreationContext worldcreationcontext = this.uiState.getSettings();
            LevelSettings levelsettings = this.createLevelSettings(flag);
            WorldData worlddata = new PrimaryLevelData(levelsettings, worldcreationcontext.options(), specialWorldProperty, worldGenSettingsLifecycle);
            if(worlddata.worldGenSettingsLifecycle() != Lifecycle.stable()) {
                // Neo: set experimental settings confirmation flag so user is not shown warning on next open
                ((PrimaryLevelData)worlddata).withConfirmedWarning(true);
            }
            this.minecraft
                .createWorldOpenFlows()
                .createLevelFromExistingSettings(optional.get(), worldcreationcontext.dataPackResources(), p_249152_, worlddata);
        }
    }

    private LevelSettings createLevelSettings(boolean debug) {
        String s = this.uiState.getName().trim();
        if (debug) {
            GameRules gamerules = new GameRules();
            gamerules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            return new LevelSettings(s, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, gamerules, WorldDataConfiguration.DEFAULT);
        } else {
            return new LevelSettings(
                s,
                this.uiState.getGameMode().gameType,
                this.uiState.isHardcore(),
                this.uiState.getDifficulty(),
                this.uiState.isAllowCommands(),
                this.uiState.getGameRules(),
                this.uiState.getSettings().dataConfiguration()
            );
        }
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.tabNavigationBar.keyPressed(keyCode)) {
            return true;
        } else if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (keyCode != 257 && keyCode != 335) {
            return false;
        } else {
            this.onCreate();
            return true;
        }
    }

    @Override
    public void onClose() {
        this.popScreen();
    }

    public void popScreen() {
        this.minecraft.setScreen(this.lastScreen);
        this.removeTempDataPackDir();
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        guiGraphics.blit(Screen.FOOTER_SEPARATOR, 0, this.height - this.layout.getFooterHeight() - 2, 0.0F, 0.0F, this.width, 2, 32, 2);
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderMenuBackground(GuiGraphics partialTick) {
        partialTick.blit(TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.layout.getHeaderHeight(), 16, 16);
        this.renderMenuBackground(partialTick, 0, this.layout.getHeaderHeight(), this.width, this.height);
    }

    @Override
    protected <T extends GuiEventListener & NarratableEntry> T addWidget(T listener) {
        return super.addWidget(listener);
    }

    @Override
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        return super.addRenderableWidget(widget);
    }

    @Nullable
    private Path getTempDataPackDir() {
        if (this.tempDataPackDir == null) {
            try {
                this.tempDataPackDir = Files.createTempDirectory("mcworld-");
            } catch (IOException ioexception) {
                LOGGER.warn("Failed to create temporary dir", (Throwable)ioexception);
                SystemToast.onPackCopyFailure(this.minecraft, this.uiState.getTargetFolder());
                this.popScreen();
            }
        }

        return this.tempDataPackDir;
    }

    void openExperimentsScreen(WorldDataConfiguration worldDataConfiguration) {
        Pair<Path, PackRepository> pair = this.getDataPackSelectionSettings(worldDataConfiguration);
        if (pair != null) {
            this.minecraft
                .setScreen(new ExperimentsScreen(this, pair.getSecond(), p_269636_ -> this.tryApplyNewDataPacks(p_269636_, false, this::openExperimentsScreen)));
        }
    }

    void openDataPackSelectionScreen(WorldDataConfiguration worldDataConfiguration) {
        Pair<Path, PackRepository> pair = this.getDataPackSelectionSettings(worldDataConfiguration);
        if (pair != null) {
            this.minecraft
                .setScreen(
                    new PackSelectionScreen(
                        pair.getSecond(),
                        p_269637_ -> this.tryApplyNewDataPacks(p_269637_, true, this::openDataPackSelectionScreen),
                        pair.getFirst(),
                        Component.translatable("dataPack.title")
                    )
                );
        }
    }

    private void tryApplyNewDataPacks(PackRepository packRepository, boolean shouldConfirm, Consumer<WorldDataConfiguration> consumer) {
        List<String> list = ImmutableList.copyOf(packRepository.getSelectedIds());
        List<String> list1 = packRepository.getAvailableIds().stream().filter(p_232927_ -> !list.contains(p_232927_)).collect(ImmutableList.toImmutableList());
        WorldDataConfiguration worlddataconfiguration = new WorldDataConfiguration(
            new DataPackConfig(list, list1), this.uiState.getSettings().dataConfiguration().enabledFeatures()
        );
        if (this.uiState.tryUpdateDataConfiguration(worlddataconfiguration)) {
            this.minecraft.setScreen(this);
        } else {
            FeatureFlagSet featureflagset = packRepository.getRequestedFeatureFlags();
            if (FeatureFlags.isExperimental(featureflagset) && shouldConfirm) {
                this.minecraft.setScreen(new ConfirmExperimentalFeaturesScreen(packRepository.getSelectedPacks(), p_269635_ -> {
                    if (p_269635_) {
                        this.applyNewPackConfig(packRepository, worlddataconfiguration, consumer);
                    } else {
                        consumer.accept(this.uiState.getSettings().dataConfiguration());
                    }
                }));
            } else {
                this.applyNewPackConfig(packRepository, worlddataconfiguration, consumer);
            }
        }
    }

    private void applyNewPackConfig(PackRepository packRepository, WorldDataConfiguration worldDataConfiguration, Consumer<WorldDataConfiguration> consumer) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("dataPack.validation.working")));
        WorldLoader.InitConfig worldloader$initconfig = createDefaultLoadConfig(packRepository, worldDataConfiguration);
        WorldLoader.<CreateWorldScreen.DataPackReloadCookie, WorldCreationContext>load(
                worldloader$initconfig,
                p_326721_ -> {
                    if (p_326721_.datapackWorldgen().registryOrThrow(Registries.WORLD_PRESET).size() == 0) {
                        throw new IllegalStateException("Needs at least one world preset to continue");
                    } else if (p_326721_.datapackWorldgen().registryOrThrow(Registries.BIOME).size() == 0) {
                        throw new IllegalStateException("Needs at least one biome continue");
                    } else {
                        WorldCreationContext worldcreationcontext = this.uiState.getSettings();
                        DynamicOps<JsonElement> dynamicops = worldcreationcontext.worldgenLoadContext().createSerializationContext(JsonOps.INSTANCE);
                        DataResult<JsonElement> dataresult = WorldGenSettings.encode(
                                dynamicops, worldcreationcontext.options(), worldcreationcontext.selectedDimensions()
                            )
                            .setLifecycle(Lifecycle.stable());
                        DynamicOps<JsonElement> dynamicops1 = p_326721_.datapackWorldgen().createSerializationContext(JsonOps.INSTANCE);
                        WorldGenSettings worldgensettings = dataresult.<WorldGenSettings>flatMap(
                                p_232895_ -> WorldGenSettings.CODEC.parse(dynamicops1, p_232895_)
                            )
                            .getOrThrow(p_337413_ -> new IllegalStateException("Error parsing worldgen settings after loading data packs: " + p_337413_));
                        return new WorldLoader.DataLoadOutput<>(
                            new CreateWorldScreen.DataPackReloadCookie(worldgensettings, p_326721_.dataConfiguration()), p_326721_.datapackDimensions()
                        );
                    }
                },
                (p_247788_, p_247789_, p_247790_, p_247791_) -> {
                    p_247788_.close();
                    return new WorldCreationContext(p_247791_.worldGenSettings(), p_247790_, p_247789_, p_247791_.dataConfiguration());
                },
                Util.backgroundExecutor(),
                this.minecraft
            )
            .thenApply(p_344162_ -> {
                p_344162_.validate();
                return (WorldCreationContext)p_344162_;
            })
            .thenAcceptAsync(this.uiState::setSettings, this.minecraft)
            .handleAsync(
                (p_280900_, p_280901_) -> {
                    if (p_280901_ != null) {
                        LOGGER.warn("Failed to validate datapack", p_280901_);
                        this.minecraft
                            .setScreen(
                                new ConfirmScreen(
                                    p_269627_ -> {
                                        if (p_269627_) {
                                            consumer.accept(this.uiState.getSettings().dataConfiguration());
                                        } else {
                                            consumer.accept(new WorldDataConfiguration(new DataPackConfig(ImmutableList.of("vanilla"), ImmutableList.of()), FeatureFlags.VANILLA_SET)); // FORGE: Revert to *actual* vanilla data
                                        }
                                    },
                                    Component.translatable("dataPack.validation.failed"),
                                    CommonComponents.EMPTY,
                                    Component.translatable("dataPack.validation.back"),
                                    Component.translatable("dataPack.validation.reset")
                                )
                            );
                    } else {
                        this.minecraft.setScreen(this);
                    }

                    return null;
                },
                this.minecraft
            );
    }

    private static WorldLoader.InitConfig createDefaultLoadConfig(PackRepository packRepository, WorldDataConfiguration initialDataConfig) {
        WorldLoader.PackConfig worldloader$packconfig = new WorldLoader.PackConfig(packRepository, initialDataConfig, false, true);
        return new WorldLoader.InitConfig(worldloader$packconfig, Commands.CommandSelection.INTEGRATED, 2);
    }

    private void removeTempDataPackDir() {
        if (this.tempDataPackDir != null) {
            try (Stream<Path> stream = Files.walk(this.tempDataPackDir)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p_232942_ -> {
                    try {
                        Files.delete(p_232942_);
                    } catch (IOException ioexception1) {
                        LOGGER.warn("Failed to remove temporary file {}", p_232942_, ioexception1);
                    }
                });
            } catch (IOException ioexception) {
                LOGGER.warn("Failed to list temporary dir {}", this.tempDataPackDir);
            }

            this.tempDataPackDir = null;
        }
    }

    private static void copyBetweenDirs(Path fromDir, Path toDir, Path filePath) {
        try {
            Util.copyBetweenDirs(fromDir, toDir, filePath);
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to copy datapack file from {} to {}", filePath, toDir);
            throw new UncheckedIOException(ioexception);
        }
    }

    private Optional<LevelStorageSource.LevelStorageAccess> createNewWorldDirectory() {
        String s = this.uiState.getTargetFolder();

        try {
            LevelStorageSource.LevelStorageAccess levelstoragesource$levelstorageaccess = this.minecraft.getLevelSource().createAccess(s);
            if (this.tempDataPackDir == null) {
                return Optional.of(levelstoragesource$levelstorageaccess);
            }

            try {
                Optional optional;
                try (Stream<Path> stream = Files.walk(this.tempDataPackDir)) {
                    Path path = levelstoragesource$levelstorageaccess.getLevelPath(LevelResource.DATAPACK_DIR);
                    FileUtil.createDirectoriesSafe(path);
                    stream.filter(p_232921_ -> !p_232921_.equals(this.tempDataPackDir))
                        .forEach(p_232945_ -> copyBetweenDirs(this.tempDataPackDir, path, p_232945_));
                    optional = Optional.of(levelstoragesource$levelstorageaccess);
                }

                return optional;
            } catch (UncheckedIOException | IOException ioexception) {
                LOGGER.warn("Failed to copy datapacks to world {}", s, ioexception);
                levelstoragesource$levelstorageaccess.close();
            }
        } catch (UncheckedIOException | IOException ioexception1) {
            LOGGER.warn("Failed to create access for {}", s, ioexception1);
        }

        SystemToast.onPackCopyFailure(this.minecraft, s);
        this.popScreen();
        return Optional.empty();
    }

    @Nullable
    public static Path createTempDataPackDirFromExistingWorld(Path datapackDir, Minecraft minecraft) {
        MutableObject<Path> mutableobject = new MutableObject<>();

        try (Stream<Path> stream = Files.walk(datapackDir)) {
            stream.filter(p_232924_ -> !p_232924_.equals(datapackDir)).forEach(p_232933_ -> {
                Path path = mutableobject.getValue();
                if (path == null) {
                    try {
                        path = Files.createTempDirectory("mcworld-");
                    } catch (IOException ioexception1) {
                        LOGGER.warn("Failed to create temporary dir");
                        throw new UncheckedIOException(ioexception1);
                    }

                    mutableobject.setValue(path);
                }

                copyBetweenDirs(datapackDir, path, p_232933_);
            });
        } catch (UncheckedIOException | IOException ioexception) {
            LOGGER.warn("Failed to copy datapacks from world {}", datapackDir, ioexception);
            SystemToast.onPackCopyFailure(minecraft, datapackDir.toString());
            return null;
        }

        return mutableobject.getValue();
    }

    @Nullable
    private Pair<Path, PackRepository> getDataPackSelectionSettings(WorldDataConfiguration worldDataConfiguration) {
        Path path = this.getTempDataPackDir();
        if (path != null) {
            if (this.tempDataPackRepository == null) {
                this.tempDataPackRepository = ServerPacksSource.createPackRepository(path, this.packValidator);
                net.neoforged.neoforge.resource.ResourcePackLoader.populatePackRepository(this.tempDataPackRepository, net.minecraft.server.packs.PackType.SERVER_DATA, false);
                this.tempDataPackRepository.reload();
            }

            this.tempDataPackRepository.setSelected(worldDataConfiguration.dataPacks().getEnabled());
            return Pair.of(path, this.tempDataPackRepository);
        } else {
            return null;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record DataPackReloadCookie(WorldGenSettings worldGenSettings, WorldDataConfiguration dataConfiguration) {
    }

    @OnlyIn(Dist.CLIENT)
    class GameTab extends GridLayoutTab {
        private static final Component TITLE = Component.translatable("createWorld.tab.game.title");
        private static final Component ALLOW_COMMANDS = Component.translatable("selectWorld.allowCommands.new");
        private final EditBox nameEdit;

        GameTab() {
            super(TITLE);
            GridLayout.RowHelper gridlayout$rowhelper = this.layout.rowSpacing(8).createRowHelper(1);
            LayoutSettings layoutsettings = gridlayout$rowhelper.newCellSettings();
            this.nameEdit = new EditBox(CreateWorldScreen.this.font, 208, 20, Component.translatable("selectWorld.enterName"));
            this.nameEdit.setValue(CreateWorldScreen.this.uiState.getName());
            this.nameEdit.setResponder(CreateWorldScreen.this.uiState::setName);
            CreateWorldScreen.this.uiState
                .addListener(
                    p_275871_ -> this.nameEdit
                            .setTooltip(
                                Tooltip.create(
                                    Component.translatable(
                                        "selectWorld.targetFolder", Component.literal(p_275871_.getTargetFolder()).withStyle(ChatFormatting.ITALIC)
                                    )
                                )
                            )
                );
            CreateWorldScreen.this.setInitialFocus(this.nameEdit);
            gridlayout$rowhelper.addChild(
                CommonLayouts.labeledElement(CreateWorldScreen.this.font, this.nameEdit, CreateWorldScreen.NAME_LABEL),
                gridlayout$rowhelper.newCellSettings().alignHorizontallyCenter()
            );
            CycleButton<WorldCreationUiState.SelectedGameMode> cyclebutton = gridlayout$rowhelper.addChild(
                CycleButton.<WorldCreationUiState.SelectedGameMode>builder(p_268080_ -> p_268080_.displayName)
                    .withValues(
                        WorldCreationUiState.SelectedGameMode.SURVIVAL,
                        WorldCreationUiState.SelectedGameMode.HARDCORE,
                        WorldCreationUiState.SelectedGameMode.CREATIVE
                    )
                    .create(0, 0, 210, 20, CreateWorldScreen.GAME_MODEL_LABEL, (p_268266_, p_268208_) -> CreateWorldScreen.this.uiState.setGameMode(p_268208_)),
                layoutsettings
            );
            CreateWorldScreen.this.uiState.addListener(p_280907_ -> {
                cyclebutton.setValue(p_280907_.getGameMode());
                cyclebutton.active = !p_280907_.isDebug();
                cyclebutton.setTooltip(Tooltip.create(p_280907_.getGameMode().getInfo()));
            });
            CycleButton<Difficulty> cyclebutton1 = gridlayout$rowhelper.addChild(
                CycleButton.builder(Difficulty::getDisplayName)
                    .withValues(Difficulty.values())
                    .create(
                        0,
                        0,
                        210,
                        20,
                        Component.translatable("options.difficulty"),
                        (p_267962_, p_268338_) -> CreateWorldScreen.this.uiState.setDifficulty(p_268338_)
                    ),
                layoutsettings
            );
            CreateWorldScreen.this.uiState.addListener(p_280905_ -> {
                cyclebutton1.setValue(CreateWorldScreen.this.uiState.getDifficulty());
                cyclebutton1.active = !CreateWorldScreen.this.uiState.isHardcore();
                cyclebutton1.setTooltip(Tooltip.create(CreateWorldScreen.this.uiState.getDifficulty().getInfo()));
            });
            CycleButton<Boolean> cyclebutton2 = gridlayout$rowhelper.addChild(
                CycleButton.onOffBuilder()
                    .withTooltip(p_321371_ -> Tooltip.create(CreateWorldScreen.ALLOW_COMMANDS_INFO))
                    .create(0, 0, 210, 20, ALLOW_COMMANDS, (p_321372_, p_321373_) -> CreateWorldScreen.this.uiState.setAllowCommands(p_321373_))
            );
            CreateWorldScreen.this.uiState.addListener(p_321375_ -> {
                cyclebutton2.setValue(CreateWorldScreen.this.uiState.isAllowCommands());
                cyclebutton2.active = !CreateWorldScreen.this.uiState.isDebug() && !CreateWorldScreen.this.uiState.isHardcore();
            });
            if (!SharedConstants.getCurrentVersion().isStable()) {
                gridlayout$rowhelper.addChild(
                    Button.builder(
                            CreateWorldScreen.EXPERIMENTS_LABEL,
                            p_269641_ -> CreateWorldScreen.this.openExperimentsScreen(CreateWorldScreen.this.uiState.getSettings().dataConfiguration())
                        )
                        .width(210)
                        .build()
                );
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    class MoreTab extends GridLayoutTab {
        private static final Component TITLE = Component.translatable("createWorld.tab.more.title");
        private static final Component GAME_RULES_LABEL = Component.translatable("selectWorld.gameRules");
        private static final Component DATA_PACKS_LABEL = Component.translatable("selectWorld.dataPacks");

        MoreTab() {
            super(TITLE);
            GridLayout.RowHelper gridlayout$rowhelper = this.layout.rowSpacing(8).createRowHelper(1);
            gridlayout$rowhelper.addChild(Button.builder(GAME_RULES_LABEL, p_268028_ -> this.openGameRulesScreen()).width(210).build());
            gridlayout$rowhelper.addChild(
                Button.builder(
                        CreateWorldScreen.EXPERIMENTS_LABEL,
                        p_269642_ -> CreateWorldScreen.this.openExperimentsScreen(CreateWorldScreen.this.uiState.getSettings().dataConfiguration())
                    )
                    .width(210)
                    .build()
            );
            gridlayout$rowhelper.addChild(
                Button.builder(
                        DATA_PACKS_LABEL,
                        p_268345_ -> CreateWorldScreen.this.openDataPackSelectionScreen(CreateWorldScreen.this.uiState.getSettings().dataConfiguration())
                    )
                    .width(210)
                    .build()
            );
        }

        private void openGameRulesScreen() {
            CreateWorldScreen.this.minecraft.setScreen(new EditGameRulesScreen(CreateWorldScreen.this.uiState.getGameRules().copy(), p_268107_ -> {
                CreateWorldScreen.this.minecraft.setScreen(CreateWorldScreen.this);
                p_268107_.ifPresent(CreateWorldScreen.this.uiState::setGameRules);
            }));
        }
    }

    @OnlyIn(Dist.CLIENT)
    class WorldTab extends GridLayoutTab {
        private static final Component TITLE = Component.translatable("createWorld.tab.world.title");
        private static final Component AMPLIFIED_HELP_TEXT = Component.translatable("generator.minecraft.amplified.info");
        private static final Component GENERATE_STRUCTURES = Component.translatable("selectWorld.mapFeatures");
        private static final Component GENERATE_STRUCTURES_INFO = Component.translatable("selectWorld.mapFeatures.info");
        private static final Component BONUS_CHEST = Component.translatable("selectWorld.bonusItems");
        private static final Component SEED_LABEL = Component.translatable("selectWorld.enterSeed");
        static final Component SEED_EMPTY_HINT = Component.translatable("selectWorld.seedInfo").withStyle(ChatFormatting.DARK_GRAY);
        private static final int WORLD_TAB_WIDTH = 310;
        private final EditBox seedEdit;
        private final Button customizeTypeButton;

        WorldTab() {
            super(TITLE);
            GridLayout.RowHelper gridlayout$rowhelper = this.layout.columnSpacing(10).rowSpacing(8).createRowHelper(2);
            CycleButton<WorldCreationUiState.WorldTypeEntry> cyclebutton = gridlayout$rowhelper.addChild(
                CycleButton.builder(WorldCreationUiState.WorldTypeEntry::describePreset)
                    .withValues(this.createWorldTypeValueSupplier())
                    .withCustomNarration(CreateWorldScreen.WorldTab::createTypeButtonNarration)
                    .create(
                        0,
                        0,
                        150,
                        20,
                        Component.translatable("selectWorld.mapType"),
                        (p_268242_, p_267954_) -> CreateWorldScreen.this.uiState.setWorldType(p_267954_)
                    )
            );
            cyclebutton.setValue(CreateWorldScreen.this.uiState.getWorldType());
            CreateWorldScreen.this.uiState.addListener(p_280909_ -> {
                WorldCreationUiState.WorldTypeEntry worldcreationuistate$worldtypeentry = p_280909_.getWorldType();
                cyclebutton.setValue(worldcreationuistate$worldtypeentry);
                if (worldcreationuistate$worldtypeentry.isAmplified()) {
                    cyclebutton.setTooltip(Tooltip.create(AMPLIFIED_HELP_TEXT));
                } else {
                    cyclebutton.setTooltip(null);
                }

                cyclebutton.active = CreateWorldScreen.this.uiState.getWorldType().preset() != null;
            });
            this.customizeTypeButton = gridlayout$rowhelper.addChild(
                Button.builder(Component.translatable("selectWorld.customizeType"), p_268355_ -> this.openPresetEditor()).build()
            );
            CreateWorldScreen.this.uiState
                .addListener(p_280910_ -> this.customizeTypeButton.active = !p_280910_.isDebug() && p_280910_.getPresetEditor() != null);
            this.seedEdit = new EditBox(CreateWorldScreen.this.font, 308, 20, Component.translatable("selectWorld.enterSeed")) {
                @Override
                protected MutableComponent createNarrationMessage() {
                    return super.createNarrationMessage().append(CommonComponents.NARRATION_SEPARATOR).append(CreateWorldScreen.WorldTab.SEED_EMPTY_HINT);
                }
            };
            this.seedEdit.setHint(SEED_EMPTY_HINT);
            this.seedEdit.setValue(CreateWorldScreen.this.uiState.getSeed());
            this.seedEdit.setResponder(p_268342_ -> CreateWorldScreen.this.uiState.setSeed(this.seedEdit.getValue()));
            gridlayout$rowhelper.addChild(CommonLayouts.labeledElement(CreateWorldScreen.this.font, this.seedEdit, SEED_LABEL), 2);
            SwitchGrid.Builder switchgrid$builder = SwitchGrid.builder(310);
            switchgrid$builder.addSwitch(
                    GENERATE_STRUCTURES, CreateWorldScreen.this.uiState::isGenerateStructures, CreateWorldScreen.this.uiState::setGenerateStructures
                )
                .withIsActiveCondition(() -> !CreateWorldScreen.this.uiState.isDebug())
                .withInfo(GENERATE_STRUCTURES_INFO);
            switchgrid$builder.addSwitch(BONUS_CHEST, CreateWorldScreen.this.uiState::isBonusChest, CreateWorldScreen.this.uiState::setBonusChest)
                .withIsActiveCondition(() -> !CreateWorldScreen.this.uiState.isHardcore() && !CreateWorldScreen.this.uiState.isDebug());
            SwitchGrid switchgrid = switchgrid$builder.build(p_267961_ -> gridlayout$rowhelper.addChild(p_267961_, 2));
            CreateWorldScreen.this.uiState.addListener(p_268209_ -> switchgrid.refreshStates());
        }

        private void openPresetEditor() {
            PresetEditor preseteditor = CreateWorldScreen.this.uiState.getPresetEditor();
            if (preseteditor != null) {
                CreateWorldScreen.this.minecraft.setScreen(preseteditor.createEditScreen(CreateWorldScreen.this, CreateWorldScreen.this.uiState.getSettings()));
            }
        }

        private CycleButton.ValueListSupplier<WorldCreationUiState.WorldTypeEntry> createWorldTypeValueSupplier() {
            return new CycleButton.ValueListSupplier<WorldCreationUiState.WorldTypeEntry>() {
                @Override
                public List<WorldCreationUiState.WorldTypeEntry> getSelectedList() {
                    return CycleButton.DEFAULT_ALT_LIST_SELECTOR.getAsBoolean()
                        ? CreateWorldScreen.this.uiState.getAltPresetList()
                        : CreateWorldScreen.this.uiState.getNormalPresetList();
                }

                @Override
                public List<WorldCreationUiState.WorldTypeEntry> getDefaultList() {
                    return CreateWorldScreen.this.uiState.getNormalPresetList();
                }
            };
        }

        private static MutableComponent createTypeButtonNarration(CycleButton<WorldCreationUiState.WorldTypeEntry> button) {
            return button.getValue().isAmplified()
                ? CommonComponents.joinForNarration(button.createDefaultNarrationMessage(), AMPLIFIED_HELP_TEXT)
                : button.createDefaultNarrationMessage();
        }
    }
}
