package net.minecraft.world.level;

import com.mojang.serialization.Dynamic;
import net.minecraft.world.Difficulty;

public final class LevelSettings {
    private final String levelName;
    private final GameType gameType;
    private final boolean hardcore;
    private final Difficulty difficulty;
    private final boolean allowCommands;
    private final GameRules gameRules;
    private final WorldDataConfiguration dataConfiguration;
    private final com.mojang.serialization.Lifecycle lifecycle;

    public LevelSettings(
        String levelName, GameType gameType, boolean hardcore, Difficulty difficulty, boolean allowCommands, GameRules gameRules, WorldDataConfiguration dataConfiguration
    ) {
        this(levelName, gameType, hardcore, difficulty, allowCommands, gameRules, dataConfiguration, com.mojang.serialization.Lifecycle.stable());
    }
    public LevelSettings(String levelName, GameType gameType, boolean hardcore, Difficulty difficulty, boolean allowCommands, GameRules gameRules, WorldDataConfiguration dataConfiguration, com.mojang.serialization.Lifecycle lifecycle) {
        this.levelName = levelName;
        this.gameType = gameType;
        this.hardcore = hardcore;
        this.difficulty = difficulty;
        this.allowCommands = allowCommands;
        this.gameRules = gameRules;
        this.dataConfiguration = dataConfiguration;
        this.lifecycle = lifecycle;
    }

    public static LevelSettings parse(Dynamic<?> levelData, WorldDataConfiguration dataConfiguration) {
        GameType gametype = GameType.byId(levelData.get("GameType").asInt(0));
        return new LevelSettings(
            levelData.get("LevelName").asString(""),
            gametype,
            levelData.get("hardcore").asBoolean(false),
            levelData.get("Difficulty").asNumber().map(p_46928_ -> Difficulty.byId(p_46928_.byteValue())).result().orElse(Difficulty.NORMAL),
            levelData.get("allowCommands").asBoolean(gametype == GameType.CREATIVE),
            new GameRules(levelData.get("GameRules")),
            dataConfiguration,
            net.neoforged.neoforge.common.CommonHooks.parseLifecycle(levelData.get("forgeLifecycle").asString("stable"))
        );
    }

    public String levelName() {
        return this.levelName;
    }

    public GameType gameType() {
        return this.gameType;
    }

    public boolean hardcore() {
        return this.hardcore;
    }

    public Difficulty difficulty() {
        return this.difficulty;
    }

    public boolean allowCommands() {
        return this.allowCommands;
    }

    public GameRules gameRules() {
        return this.gameRules;
    }

    public WorldDataConfiguration getDataConfiguration() {
        return this.dataConfiguration;
    }

    public LevelSettings withGameType(GameType gameType) {
        return new LevelSettings(this.levelName, gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, this.dataConfiguration, this.lifecycle);
    }

    public LevelSettings withDifficulty(Difficulty difficulty) {
        net.neoforged.neoforge.common.CommonHooks.onDifficultyChange(difficulty, this.difficulty);
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, difficulty, this.allowCommands, this.gameRules, this.dataConfiguration, this.lifecycle);
    }

    public LevelSettings withDataConfiguration(WorldDataConfiguration dataConfiguration) {
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, dataConfiguration, this.lifecycle);
    }

    public LevelSettings copy() {
        return new LevelSettings(
            this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules.copy(), this.dataConfiguration, this.lifecycle
        );
    }
    public LevelSettings withLifecycle(com.mojang.serialization.Lifecycle lifecycle) {
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, this.dataConfiguration, lifecycle);
    }
    public com.mojang.serialization.Lifecycle getLifecycle() {
        return this.lifecycle;
    }
}
