package net.minecraft.client.gui.screens.worldselection;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WorldCreationUiState {
    private static final Component DEFAULT_WORLD_NAME = Component.translatable("selectWorld.newWorld");
    private final List<Consumer<WorldCreationUiState>> listeners = new ArrayList<>();
    private String name = DEFAULT_WORLD_NAME.getString();
    private WorldCreationUiState.SelectedGameMode gameMode = WorldCreationUiState.SelectedGameMode.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;
    @Nullable
    private Boolean allowCommands;
    private String seed;
    private boolean generateStructures;
    private boolean bonusChest;
    private final Path savesFolder;
    private String targetFolder;
    private WorldCreationContext settings;
    private WorldCreationUiState.WorldTypeEntry worldType;
    private final List<WorldCreationUiState.WorldTypeEntry> normalPresetList = new ArrayList<>();
    private final List<WorldCreationUiState.WorldTypeEntry> altPresetList = new ArrayList<>();
    private GameRules gameRules = new GameRules();

    public WorldCreationUiState(Path savesFolder, WorldCreationContext settings, Optional<ResourceKey<WorldPreset>> preset, OptionalLong seed) {
        this.savesFolder = savesFolder;
        this.settings = settings;
        this.worldType = new WorldCreationUiState.WorldTypeEntry(findPreset(settings, preset).orElse(null));
        this.updatePresetLists();
        this.seed = seed.isPresent() ? Long.toString(seed.getAsLong()) : "";
        this.generateStructures = settings.options().generateStructures();
        this.bonusChest = settings.options().generateBonusChest();
        this.targetFolder = this.findResultFolder(this.name);
    }

    public void addListener(Consumer<WorldCreationUiState> listener) {
        this.listeners.add(listener);
    }

    public void onChanged() {
        boolean flag = this.isBonusChest();
        if (flag != this.settings.options().generateBonusChest()) {
            this.settings = this.settings.withOptions(p_268360_ -> p_268360_.withBonusChest(flag));
        }

        boolean flag1 = this.isGenerateStructures();
        if (flag1 != this.settings.options().generateStructures()) {
            this.settings = this.settings.withOptions(p_267945_ -> p_267945_.withStructures(flag1));
        }

        for (Consumer<WorldCreationUiState> consumer : this.listeners) {
            consumer.accept(this);
        }
    }

    public void setName(String name) {
        this.name = name;
        this.targetFolder = this.findResultFolder(name);
        this.onChanged();
    }

    private String findResultFolder(String name) {
        String s = name.trim();

        try {
            return FileUtil.findAvailableName(this.savesFolder, !s.isEmpty() ? s : DEFAULT_WORLD_NAME.getString(), "");
        } catch (Exception exception) {
            try {
                return FileUtil.findAvailableName(this.savesFolder, "World", "");
            } catch (IOException ioexception) {
                throw new RuntimeException("Could not create save folder", ioexception);
            }
        }
    }

    public String getName() {
        return this.name;
    }

    public String getTargetFolder() {
        return this.targetFolder;
    }

    public void setGameMode(WorldCreationUiState.SelectedGameMode gameMode) {
        this.gameMode = gameMode;
        this.onChanged();
    }

    public WorldCreationUiState.SelectedGameMode getGameMode() {
        return this.isDebug() ? WorldCreationUiState.SelectedGameMode.DEBUG : this.gameMode;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.onChanged();
    }

    public Difficulty getDifficulty() {
        return this.isHardcore() ? Difficulty.HARD : this.difficulty;
    }

    public boolean isHardcore() {
        return this.getGameMode() == WorldCreationUiState.SelectedGameMode.HARDCORE;
    }

    public void setAllowCommands(boolean allowCommands) {
        this.allowCommands = allowCommands;
        this.onChanged();
    }

    public boolean isAllowCommands() {
        if (this.isDebug()) {
            return true;
        } else if (this.isHardcore()) {
            return false;
        } else {
            return this.allowCommands == null ? this.getGameMode() == WorldCreationUiState.SelectedGameMode.CREATIVE : this.allowCommands;
        }
    }

    public void setSeed(String seed) {
        this.seed = seed;
        this.settings = this.settings.withOptions(p_267957_ -> p_267957_.withSeed(WorldOptions.parseSeed(this.getSeed())));
        this.onChanged();
    }

    public String getSeed() {
        return this.seed;
    }

    public void setGenerateStructures(boolean generateStructures) {
        this.generateStructures = generateStructures;
        this.onChanged();
    }

    public boolean isGenerateStructures() {
        return this.isDebug() ? false : this.generateStructures;
    }

    public void setBonusChest(boolean bonusChest) {
        this.bonusChest = bonusChest;
        this.onChanged();
    }

    public boolean isBonusChest() {
        return !this.isDebug() && !this.isHardcore() ? this.bonusChest : false;
    }

    public void setSettings(WorldCreationContext settings) {
        this.settings = settings;
        this.updatePresetLists();
        this.onChanged();
    }

    public WorldCreationContext getSettings() {
        return this.settings;
    }

    public void updateDimensions(WorldCreationContext.DimensionsUpdater dimensionsUpdater) {
        this.settings = this.settings.withDimensions(dimensionsUpdater);
        this.onChanged();
    }

    protected boolean tryUpdateDataConfiguration(WorldDataConfiguration worldDataConfiguration) {
        WorldDataConfiguration worlddataconfiguration = this.settings.dataConfiguration();
        if (worlddataconfiguration.dataPacks().getEnabled().equals(worldDataConfiguration.dataPacks().getEnabled())
            && worlddataconfiguration.enabledFeatures().equals(worldDataConfiguration.enabledFeatures())) {
            this.settings = new WorldCreationContext(
                this.settings.options(),
                this.settings.datapackDimensions(),
                this.settings.selectedDimensions(),
                this.settings.worldgenRegistries(),
                this.settings.dataPackResources(),
                worldDataConfiguration
            );
            return true;
        } else {
            return false;
        }
    }

    public boolean isDebug() {
        return this.settings.selectedDimensions().isDebug();
    }

    public void setWorldType(WorldCreationUiState.WorldTypeEntry worldType) {
        this.worldType = worldType;
        Holder<WorldPreset> holder = worldType.preset();
        if (holder != null) {
            this.updateDimensions((p_268134_, p_268035_) -> holder.value().createWorldDimensions());
        }
    }

    public WorldCreationUiState.WorldTypeEntry getWorldType() {
        return this.worldType;
    }

    @Nullable
    public PresetEditor getPresetEditor() {
        Holder<WorldPreset> holder = this.getWorldType().preset();
        return holder != null ? holder.unwrapKey().map(net.neoforged.neoforge.client.PresetEditorManager::get).orElse(null) : null; // FORGE: redirect lookup to expanded map
    }

    public List<WorldCreationUiState.WorldTypeEntry> getNormalPresetList() {
        return this.normalPresetList;
    }

    public List<WorldCreationUiState.WorldTypeEntry> getAltPresetList() {
        return this.altPresetList;
    }

    private void updatePresetLists() {
        Registry<WorldPreset> registry = this.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
        this.normalPresetList.clear();
        this.normalPresetList
            .addAll(
                getNonEmptyList(registry, WorldPresetTags.NORMAL).orElseGet(() -> registry.holders().map(WorldCreationUiState.WorldTypeEntry::new).toList())
            );
        this.altPresetList.clear();
        this.altPresetList.addAll(getNonEmptyList(registry, WorldPresetTags.EXTENDED).orElse(this.normalPresetList));
        Holder<WorldPreset> holder = this.worldType.preset();
        if (holder != null) {
            this.worldType = findPreset(this.getSettings(), holder.unwrapKey())
                .map(WorldCreationUiState.WorldTypeEntry::new)
                .orElse(this.normalPresetList.get(0));
        }
    }

    private static Optional<Holder<WorldPreset>> findPreset(WorldCreationContext context, Optional<ResourceKey<WorldPreset>> preset) {
        return preset.flatMap(
            p_267974_ -> context.worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET).getHolder((ResourceKey<WorldPreset>)p_267974_)
        );
    }

    private static Optional<List<WorldCreationUiState.WorldTypeEntry>> getNonEmptyList(Registry<WorldPreset> registry, TagKey<WorldPreset> key) {
        return registry.getTag(key)
            .map(p_268149_ -> p_268149_.stream().map(WorldCreationUiState.WorldTypeEntry::new).toList())
            .filter(p_268066_ -> !p_268066_.isEmpty());
    }

    public void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
        this.onChanged();
    }

    public GameRules getGameRules() {
        return this.gameRules;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum SelectedGameMode {
        SURVIVAL("survival", GameType.SURVIVAL),
        HARDCORE("hardcore", GameType.SURVIVAL),
        CREATIVE("creative", GameType.CREATIVE),
        DEBUG("spectator", GameType.SPECTATOR);

        public final GameType gameType;
        public final Component displayName;
        private final Component info;

        private SelectedGameMode(String id, GameType gameType) {
            this.gameType = gameType;
            this.displayName = Component.translatable("selectWorld.gameMode." + id);
            this.info = Component.translatable("selectWorld.gameMode." + id + ".info");
        }

        public Component getInfo() {
            return this.info;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record WorldTypeEntry(@Nullable Holder<WorldPreset> preset) {
        private static final Component CUSTOM_WORLD_DESCRIPTION = Component.translatable("generator.custom");

        public Component describePreset() {
            return Optional.ofNullable(this.preset)
                .flatMap(Holder::unwrapKey)
                .map(p_268048_ -> (Component) Component.translatable(p_268048_.location().toLanguageKey("generator")))
                .orElse(CUSTOM_WORLD_DESCRIPTION);
        }

        public boolean isAmplified() {
            return Optional.ofNullable(this.preset).flatMap(Holder::unwrapKey).filter(p_268224_ -> p_268224_.equals(WorldPresets.AMPLIFIED)).isPresent();
        }
    }
}
