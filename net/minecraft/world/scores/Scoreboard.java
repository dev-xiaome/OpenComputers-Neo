package net.minecraft.world.scores;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public class Scoreboard {
    public static final String HIDDEN_SCORE_PREFIX = "#";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Object2ObjectMap<String, Objective> objectivesByName = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Reference2ObjectMap<ObjectiveCriteria, List<Objective>> objectivesByCriteria = new Reference2ObjectOpenHashMap<>();
    private final Map<String, PlayerScores> playerScores = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Map<DisplaySlot, Objective> displayObjectives = new EnumMap<>(DisplaySlot.class);
    private final Object2ObjectMap<String, PlayerTeam> teamsByName = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, PlayerTeam> teamsByPlayer = new Object2ObjectOpenHashMap<>();

    /**
     * Returns a ScoreObjective for the objective name
     */
    @Nullable
    public Objective getObjective(@Nullable String name) {
        return this.objectivesByName.get(name);
    }

    public Objective addObjective(
        String name,
        ObjectiveCriteria criteria,
        Component displayName,
        ObjectiveCriteria.RenderType renderType,
        boolean displayAutoUpdate,
        @Nullable NumberFormat numberFormat
    ) {
        if (this.objectivesByName.containsKey(name)) {
            throw new IllegalArgumentException("An objective with the name '" + name + "' already exists!");
        } else {
            Objective objective = new Objective(this, name, criteria, displayName, renderType, displayAutoUpdate, numberFormat);
            this.objectivesByCriteria.computeIfAbsent(criteria, p_314722_ -> Lists.newArrayList()).add(objective);
            this.objectivesByName.put(name, objective);
            this.onObjectiveAdded(objective);
            return objective;
        }
    }

    public final void forAllObjectives(ObjectiveCriteria criteria, ScoreHolder scoreHolder, Consumer<ScoreAccess> action) {
        this.objectivesByCriteria
            .getOrDefault(criteria, Collections.emptyList())
            .forEach(p_313676_ -> action.accept(this.getOrCreatePlayerScore(scoreHolder, p_313676_, true)));
    }

    private PlayerScores getOrCreatePlayerInfo(String username) {
        return this.playerScores.computeIfAbsent(username, p_313683_ -> new PlayerScores());
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder scoreHolder, Objective objective) {
        return this.getOrCreatePlayerScore(scoreHolder, objective, false);
    }

    public ScoreAccess getOrCreatePlayerScore(final ScoreHolder scoreHolder, final Objective objective, boolean readOnly) {
        final boolean flag = readOnly || !objective.getCriteria().isReadOnly();
        PlayerScores playerscores = this.getOrCreatePlayerInfo(scoreHolder.getScoreboardName());
        final MutableBoolean mutableboolean = new MutableBoolean();
        final Score score = playerscores.getOrCreate(objective, p_313682_ -> mutableboolean.setTrue());
        return new ScoreAccess() {
            @Override
            public int get() {
                return score.value();
            }

            @Override
            public void set(int value) {
                if (!flag) {
                    throw new IllegalStateException("Cannot modify read-only score");
                } else {
                    boolean flag1 = mutableboolean.isTrue();
                    if (objective.displayAutoUpdate()) {
                        Component component = scoreHolder.getDisplayName();
                        if (component != null && !component.equals(score.display())) {
                            score.display(component);
                            flag1 = true;
                        }
                    }

                    if (value != score.value()) {
                        score.value(value);
                        flag1 = true;
                    }

                    if (flag1) {
                        this.sendScoreToPlayers();
                    }
                }
            }

            @Nullable
            @Override
            public Component display() {
                return score.display();
            }

            @Override
            public void display(@Nullable Component value) {
                if (mutableboolean.isTrue() || !Objects.equals(value, score.display())) {
                    score.display(value);
                    this.sendScoreToPlayers();
                }
            }

            @Override
            public void numberFormatOverride(@Nullable NumberFormat format) {
                score.numberFormat(format);
                this.sendScoreToPlayers();
            }

            @Override
            public boolean locked() {
                return score.isLocked();
            }

            @Override
            public void unlock() {
                this.setLocked(false);
            }

            @Override
            public void lock() {
                this.setLocked(true);
            }

            private void setLocked(boolean locked) {
                score.setLocked(locked);
                if (mutableboolean.isTrue()) {
                    this.sendScoreToPlayers();
                }

                Scoreboard.this.onScoreLockChanged(scoreHolder, objective);
            }

            private void sendScoreToPlayers() {
                Scoreboard.this.onScoreChanged(scoreHolder, objective, score);
                mutableboolean.setFalse();
            }
        };
    }

    @Nullable
    public ReadOnlyScoreInfo getPlayerScoreInfo(ScoreHolder scoreHolder, Objective objective) {
        PlayerScores playerscores = this.playerScores.get(scoreHolder.getScoreboardName());
        return playerscores != null ? playerscores.get(objective) : null;
    }

    public Collection<PlayerScoreEntry> listPlayerScores(Objective objective) {
        List<PlayerScoreEntry> list = new ArrayList<>();
        this.playerScores.forEach((p_313669_, p_313670_) -> {
            Score score = p_313670_.get(objective);
            if (score != null) {
                list.add(new PlayerScoreEntry(p_313669_, score.value(), score.display(), score.numberFormat()));
            }
        });
        return list;
    }

    public Collection<Objective> getObjectives() {
        return this.objectivesByName.values();
    }

    public Collection<String> getObjectiveNames() {
        return this.objectivesByName.keySet();
    }

    public Collection<ScoreHolder> getTrackedPlayers() {
        return this.playerScores.keySet().stream().map(ScoreHolder::forNameOnly).toList();
    }

    public void resetAllPlayerScores(ScoreHolder scoreHolder) {
        PlayerScores playerscores = this.playerScores.remove(scoreHolder.getScoreboardName());
        if (playerscores != null) {
            this.onPlayerRemoved(scoreHolder);
        }
    }

    public void resetSinglePlayerScore(ScoreHolder scoreHolder, Objective objective) {
        PlayerScores playerscores = this.playerScores.get(scoreHolder.getScoreboardName());
        if (playerscores != null) {
            boolean flag = playerscores.remove(objective);
            if (!playerscores.hasScores()) {
                PlayerScores playerscores1 = this.playerScores.remove(scoreHolder.getScoreboardName());
                if (playerscores1 != null) {
                    this.onPlayerRemoved(scoreHolder);
                }
            } else if (flag) {
                this.onPlayerScoreRemoved(scoreHolder, objective);
            }
        }
    }

    public Object2IntMap<Objective> listPlayerScores(ScoreHolder scoreHolder) {
        PlayerScores playerscores = this.playerScores.get(scoreHolder.getScoreboardName());
        return playerscores != null ? playerscores.listScores() : Object2IntMaps.emptyMap();
    }

    public void removeObjective(Objective objective) {
        this.objectivesByName.remove(objective.getName());

        for (DisplaySlot displayslot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displayslot) == objective) {
                this.setDisplayObjective(displayslot, null);
            }
        }

        List<Objective> list = this.objectivesByCriteria.get(objective.getCriteria());
        if (list != null) {
            list.remove(objective);
        }

        for (PlayerScores playerscores : this.playerScores.values()) {
            playerscores.remove(objective);
        }

        this.onObjectiveRemoved(objective);
    }

    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        this.displayObjectives.put(slot, objective);
    }

    @Nullable
    public Objective getDisplayObjective(DisplaySlot slot) {
        return this.displayObjectives.get(slot);
    }

    /**
     * Retrieve the ScorePlayerTeam instance identified by the passed team name
     */
    @Nullable
    public PlayerTeam getPlayerTeam(String teamName) {
        return this.teamsByName.get(teamName);
    }

    public PlayerTeam addPlayerTeam(String name) {
        PlayerTeam playerteam = this.getPlayerTeam(name);
        if (playerteam != null) {
            LOGGER.warn("Requested creation of existing team '{}'", name);
            return playerteam;
        } else {
            playerteam = new PlayerTeam(this, name);
            this.teamsByName.put(name, playerteam);
            this.onTeamAdded(playerteam);
            return playerteam;
        }
    }

    /**
     * Removes the team from the scoreboard, updates all player memberships and broadcasts the deletion to all players
     */
    public void removePlayerTeam(PlayerTeam playerTeam) {
        this.teamsByName.remove(playerTeam.getName());

        for (String s : playerTeam.getPlayers()) {
            this.teamsByPlayer.remove(s);
        }

        this.onTeamRemoved(playerTeam);
    }

    public boolean addPlayerToTeam(String playerName, PlayerTeam team) {
        if (this.getPlayersTeam(playerName) != null) {
            this.removePlayerFromTeam(playerName);
        }

        this.teamsByPlayer.put(playerName, team);
        return team.getPlayers().add(playerName);
    }

    public boolean removePlayerFromTeam(String playerName) {
        PlayerTeam playerteam = this.getPlayersTeam(playerName);
        if (playerteam != null) {
            this.removePlayerFromTeam(playerName, playerteam);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Removes the given username from the given ScorePlayerTeam. If the player is not on the team then an IllegalStateException is thrown.
     */
    public void removePlayerFromTeam(String username, PlayerTeam playerTeam) {
        if (this.getPlayersTeam(username) != playerTeam) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + playerTeam.getName() + "'.");
        } else {
            this.teamsByPlayer.remove(username);
            playerTeam.getPlayers().remove(username);
        }
    }

    public Collection<String> getTeamNames() {
        return this.teamsByName.keySet();
    }

    public Collection<PlayerTeam> getPlayerTeams() {
        return this.teamsByName.values();
    }

    /**
     * Gets the ScorePlayerTeam object for the given username.
     */
    @Nullable
    public PlayerTeam getPlayersTeam(String username) {
        return this.teamsByPlayer.get(username);
    }

    public void onObjectiveAdded(Objective objective) {
    }

    public void onObjectiveChanged(Objective objective) {
    }

    public void onObjectiveRemoved(Objective objective) {
    }

    protected void onScoreChanged(ScoreHolder scoreHolder, Objective objective, Score score) {
    }

    protected void onScoreLockChanged(ScoreHolder scoreHolder, Objective objective) {
    }

    public void onPlayerRemoved(ScoreHolder scoreHolder) {
    }

    public void onPlayerScoreRemoved(ScoreHolder scoreHolder, Objective objective) {
    }

    public void onTeamAdded(PlayerTeam playerTeam) {
    }

    public void onTeamChanged(PlayerTeam playerTeam) {
    }

    public void onTeamRemoved(PlayerTeam playerTeam) {
    }

    public void entityRemoved(Entity entity) {
        if (!(entity instanceof Player) && !entity.isAlive()) {
            this.resetAllPlayerScores(entity);
            this.removePlayerFromTeam(entity.getScoreboardName());
        }
    }

    protected ListTag savePlayerScores(HolderLookup.Provider levelRegistry) {
        ListTag listtag = new ListTag();
        this.playerScores.forEach((p_330203_, p_330204_) -> p_330204_.listRawScores().forEach((p_330199_, p_330200_) -> {
                CompoundTag compoundtag = p_330200_.write(levelRegistry);
                compoundtag.putString("Name", p_330203_);
                compoundtag.putString("Objective", p_330199_.getName());
                listtag.add(compoundtag);
            }));
        return listtag;
    }

    protected void loadPlayerScores(ListTag tag, HolderLookup.Provider levelRegistry) {
        for (int i = 0; i < tag.size(); i++) {
            CompoundTag compoundtag = tag.getCompound(i);
            Score score = Score.read(compoundtag, levelRegistry);
            String s = compoundtag.getString("Name");
            String s1 = compoundtag.getString("Objective");
            Objective objective = this.getObjective(s1);
            if (objective == null) {
                LOGGER.error("Unknown objective {} for name {}, ignoring", s1, s);
            } else {
                this.getOrCreatePlayerInfo(s).setScore(objective, score);
            }
        }
    }
}
