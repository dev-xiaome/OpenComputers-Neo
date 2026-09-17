package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ColorArgument;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public class TeamCommand {
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EXISTS = new SimpleCommandExceptionType(
        Component.translatable("commands.team.add.duplicate")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_EMPTY = new SimpleCommandExceptionType(
        Component.translatable("commands.team.empty.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_NAME = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.name.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_COLOR = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.color.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.friendlyfire.alreadyEnabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.friendlyfire.alreadyDisabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.seeFriendlyInvisibles.alreadyEnabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.seeFriendlyInvisibles.alreadyDisabled")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.nametagVisibility.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.deathMessageVisibility.unchanged")
    );
    private static final SimpleCommandExceptionType ERROR_TEAM_COLLISION_UNCHANGED = new SimpleCommandExceptionType(
        Component.translatable("commands.team.option.collisionRule.unchanged")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("team")
                .requires(p_183713_ -> p_183713_.hasPermission(2))
                .then(
                    Commands.literal("list")
                        .executes(p_183711_ -> listTeams(p_183711_.getSource()))
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(p_138876_ -> listMembers(p_138876_.getSource(), TeamArgument.getTeam(p_138876_, "team")))
                        )
                )
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("team", StringArgumentType.word())
                                .executes(p_138995_ -> createTeam(p_138995_.getSource(), StringArgumentType.getString(p_138995_, "team")))
                                .then(
                                    Commands.argument("displayName", ComponentArgument.textComponent(context))
                                        .executes(
                                            p_138993_ -> createTeam(
                                                    p_138993_.getSource(),
                                                    StringArgumentType.getString(p_138993_, "team"),
                                                    ComponentArgument.getComponent(p_138993_, "displayName")
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(p_138991_ -> deleteTeam(p_138991_.getSource(), TeamArgument.getTeam(p_138991_, "team")))
                        )
                )
                .then(
                    Commands.literal("empty")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(p_138989_ -> emptyTeam(p_138989_.getSource(), TeamArgument.getTeam(p_138989_, "team")))
                        )
                )
                .then(
                    Commands.literal("join")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .executes(
                                    p_313570_ -> joinTeam(
                                            p_313570_.getSource(),
                                            TeamArgument.getTeam(p_313570_, "team"),
                                            Collections.singleton(p_313570_.getSource().getEntityOrException())
                                        )
                                )
                                .then(
                                    Commands.argument("members", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(
                                            p_138985_ -> joinTeam(
                                                    p_138985_.getSource(),
                                                    TeamArgument.getTeam(p_138985_, "team"),
                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138985_, "members")
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("leave")
                        .then(
                            Commands.argument("members", ScoreHolderArgument.scoreHolders())
                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                .executes(p_138983_ -> leaveTeam(p_138983_.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(p_138983_, "members")))
                        )
                )
                .then(
                    Commands.literal("modify")
                        .then(
                            Commands.argument("team", TeamArgument.team())
                                .then(
                                    Commands.literal("displayName")
                                        .then(
                                            Commands.argument("displayName", ComponentArgument.textComponent(context))
                                                .executes(
                                                    p_138981_ -> setDisplayName(
                                                            p_138981_.getSource(),
                                                            TeamArgument.getTeam(p_138981_, "team"),
                                                            ComponentArgument.getComponent(p_138981_, "displayName")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("color")
                                        .then(
                                            Commands.argument("value", ColorArgument.color())
                                                .executes(
                                                    p_138979_ -> setColor(
                                                            p_138979_.getSource(),
                                                            TeamArgument.getTeam(p_138979_, "team"),
                                                            ColorArgument.getColor(p_138979_, "value")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("friendlyFire")
                                        .then(
                                            Commands.argument("allowed", BoolArgumentType.bool())
                                                .executes(
                                                    p_138977_ -> setFriendlyFire(
                                                            p_138977_.getSource(),
                                                            TeamArgument.getTeam(p_138977_, "team"),
                                                            BoolArgumentType.getBool(p_138977_, "allowed")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("seeFriendlyInvisibles")
                                        .then(
                                            Commands.argument("allowed", BoolArgumentType.bool())
                                                .executes(
                                                    p_138975_ -> setFriendlySight(
                                                            p_138975_.getSource(),
                                                            TeamArgument.getTeam(p_138975_, "team"),
                                                            BoolArgumentType.getBool(p_138975_, "allowed")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("nametagVisibility")
                                        .then(
                                            Commands.literal("never")
                                                .executes(
                                                    p_138973_ -> setNametagVisibility(
                                                            p_138973_.getSource(), TeamArgument.getTeam(p_138973_, "team"), Team.Visibility.NEVER
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOtherTeams")
                                                .executes(
                                                    p_138971_ -> setNametagVisibility(
                                                            p_138971_.getSource(),
                                                            TeamArgument.getTeam(p_138971_, "team"),
                                                            Team.Visibility.HIDE_FOR_OTHER_TEAMS
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOwnTeam")
                                                .executes(
                                                    p_138969_ -> setNametagVisibility(
                                                            p_138969_.getSource(), TeamArgument.getTeam(p_138969_, "team"), Team.Visibility.HIDE_FOR_OWN_TEAM
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("always")
                                                .executes(
                                                    p_138967_ -> setNametagVisibility(
                                                            p_138967_.getSource(), TeamArgument.getTeam(p_138967_, "team"), Team.Visibility.ALWAYS
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("deathMessageVisibility")
                                        .then(
                                            Commands.literal("never")
                                                .executes(
                                                    p_138965_ -> setDeathMessageVisibility(
                                                            p_138965_.getSource(), TeamArgument.getTeam(p_138965_, "team"), Team.Visibility.NEVER
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOtherTeams")
                                                .executes(
                                                    p_138963_ -> setDeathMessageVisibility(
                                                            p_138963_.getSource(),
                                                            TeamArgument.getTeam(p_138963_, "team"),
                                                            Team.Visibility.HIDE_FOR_OTHER_TEAMS
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hideForOwnTeam")
                                                .executes(
                                                    p_138961_ -> setDeathMessageVisibility(
                                                            p_138961_.getSource(), TeamArgument.getTeam(p_138961_, "team"), Team.Visibility.HIDE_FOR_OWN_TEAM
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("always")
                                                .executes(
                                                    p_138959_ -> setDeathMessageVisibility(
                                                            p_138959_.getSource(), TeamArgument.getTeam(p_138959_, "team"), Team.Visibility.ALWAYS
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("collisionRule")
                                        .then(
                                            Commands.literal("never")
                                                .executes(
                                                    p_138957_ -> setCollision(
                                                            p_138957_.getSource(), TeamArgument.getTeam(p_138957_, "team"), Team.CollisionRule.NEVER
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("pushOwnTeam")
                                                .executes(
                                                    p_138955_ -> setCollision(
                                                            p_138955_.getSource(), TeamArgument.getTeam(p_138955_, "team"), Team.CollisionRule.PUSH_OWN_TEAM
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("pushOtherTeams")
                                                .executes(
                                                    p_138953_ -> setCollision(
                                                            p_138953_.getSource(), TeamArgument.getTeam(p_138953_, "team"), Team.CollisionRule.PUSH_OTHER_TEAMS
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("always")
                                                .executes(
                                                    p_138951_ -> setCollision(
                                                            p_138951_.getSource(), TeamArgument.getTeam(p_138951_, "team"), Team.CollisionRule.ALWAYS
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("prefix")
                                        .then(
                                            Commands.argument("prefix", ComponentArgument.textComponent(context))
                                                .executes(
                                                    p_138942_ -> setPrefix(
                                                            p_138942_.getSource(),
                                                            TeamArgument.getTeam(p_138942_, "team"),
                                                            ComponentArgument.getComponent(p_138942_, "prefix")
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("suffix")
                                        .then(
                                            Commands.argument("suffix", ComponentArgument.textComponent(context))
                                                .executes(
                                                    p_138923_ -> setSuffix(
                                                            p_138923_.getSource(),
                                                            TeamArgument.getTeam(p_138923_, "team"),
                                                            ComponentArgument.getComponent(p_138923_, "suffix")
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static Component getFirstMemberName(Collection<ScoreHolder> scores) {
        return scores.iterator().next().getFeedbackDisplayName();
    }

    /**
     * Removes the listed players from their teams.
     */
    private static int leaveTeam(CommandSourceStack source, Collection<ScoreHolder> players) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : players) {
            scoreboard.removePlayerFromTeam(scoreholder.getScoreboardName());
        }

        if (players.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.team.leave.success.single", getFirstMemberName(players)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.team.leave.success.multiple", players.size()), true);
        }

        return players.size();
    }

    private static int joinTeam(CommandSourceStack source, PlayerTeam team, Collection<ScoreHolder> players) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : players) {
            scoreboard.addPlayerToTeam(scoreholder.getScoreboardName(), team);
        }

        if (players.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.team.join.success.single", getFirstMemberName(players), team.getFormattedDisplayName()), true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.team.join.success.multiple", players.size(), team.getFormattedDisplayName()), true
            );
        }

        return players.size();
    }

    private static int setNametagVisibility(CommandSourceStack source, PlayerTeam team, Team.Visibility visibility) throws CommandSyntaxException {
        if (team.getNameTagVisibility() == visibility) {
            throw ERROR_TEAM_NAMETAG_VISIBLITY_UNCHANGED.create();
        } else {
            team.setNameTagVisibility(visibility);
            source.sendSuccess(
                () -> Component.translatable("commands.team.option.nametagVisibility.success", team.getFormattedDisplayName(), visibility.getDisplayName()),
                true
            );
            return 0;
        }
    }

    private static int setDeathMessageVisibility(CommandSourceStack source, PlayerTeam team, Team.Visibility visibility) throws CommandSyntaxException {
        if (team.getDeathMessageVisibility() == visibility) {
            throw ERROR_TEAM_DEATH_MESSAGE_VISIBLITY_UNCHANGED.create();
        } else {
            team.setDeathMessageVisibility(visibility);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.team.option.deathMessageVisibility.success", team.getFormattedDisplayName(), visibility.getDisplayName()
                    ),
                true
            );
            return 0;
        }
    }

    private static int setCollision(CommandSourceStack source, PlayerTeam team, Team.CollisionRule rule) throws CommandSyntaxException {
        if (team.getCollisionRule() == rule) {
            throw ERROR_TEAM_COLLISION_UNCHANGED.create();
        } else {
            team.setCollisionRule(rule);
            source.sendSuccess(
                () -> Component.translatable("commands.team.option.collisionRule.success", team.getFormattedDisplayName(), rule.getDisplayName()),
                true
            );
            return 0;
        }
    }

    private static int setFriendlySight(CommandSourceStack source, PlayerTeam team, boolean value) throws CommandSyntaxException {
        if (team.canSeeFriendlyInvisibles() == value) {
            if (value) {
                throw ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_ENABLED.create();
            } else {
                throw ERROR_TEAM_ALREADY_FRIENDLYINVISIBLES_DISABLED.create();
            }
        } else {
            team.setSeeFriendlyInvisibles(value);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.team.option.seeFriendlyInvisibles." + (value ? "enabled" : "disabled"), team.getFormattedDisplayName()
                    ),
                true
            );
            return 0;
        }
    }

    private static int setFriendlyFire(CommandSourceStack source, PlayerTeam team, boolean value) throws CommandSyntaxException {
        if (team.isAllowFriendlyFire() == value) {
            if (value) {
                throw ERROR_TEAM_ALREADY_FRIENDLYFIRE_ENABLED.create();
            } else {
                throw ERROR_TEAM_ALREADY_FRIENDLYFIRE_DISABLED.create();
            }
        } else {
            team.setAllowFriendlyFire(value);
            source.sendSuccess(
                () -> Component.translatable("commands.team.option.friendlyfire." + (value ? "enabled" : "disabled"), team.getFormattedDisplayName()),
                true
            );
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, PlayerTeam team, Component value) throws CommandSyntaxException {
        if (team.getDisplayName().equals(value)) {
            throw ERROR_TEAM_ALREADY_NAME.create();
        } else {
            team.setDisplayName(value);
            source.sendSuccess(() -> Component.translatable("commands.team.option.name.success", team.getFormattedDisplayName()), true);
            return 0;
        }
    }

    private static int setColor(CommandSourceStack source, PlayerTeam team, ChatFormatting value) throws CommandSyntaxException {
        if (team.getColor() == value) {
            throw ERROR_TEAM_ALREADY_COLOR.create();
        } else {
            team.setColor(value);
            source.sendSuccess(
                () -> Component.translatable("commands.team.option.color.success", team.getFormattedDisplayName(), value.getName()), true
            );
            return 0;
        }
    }

    private static int emptyTeam(CommandSourceStack source, PlayerTeam team) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        Collection<String> collection = Lists.newArrayList(team.getPlayers());
        if (collection.isEmpty()) {
            throw ERROR_TEAM_ALREADY_EMPTY.create();
        } else {
            for (String s : collection) {
                scoreboard.removePlayerFromTeam(s, team);
            }

            source.sendSuccess(() -> Component.translatable("commands.team.empty.success", collection.size(), team.getFormattedDisplayName()), true);
            return collection.size();
        }
    }

    private static int deleteTeam(CommandSourceStack source, PlayerTeam team) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        scoreboard.removePlayerTeam(team);
        source.sendSuccess(() -> Component.translatable("commands.team.remove.success", team.getFormattedDisplayName()), true);
        return scoreboard.getPlayerTeams().size();
    }

    private static int createTeam(CommandSourceStack source, String name) throws CommandSyntaxException {
        return createTeam(source, name, Component.literal(name));
    }

    private static int createTeam(CommandSourceStack source, String name, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getPlayerTeam(name) != null) {
            throw ERROR_TEAM_ALREADY_EXISTS.create();
        } else {
            PlayerTeam playerteam = scoreboard.addPlayerTeam(name);
            playerteam.setDisplayName(displayName);
            source.sendSuccess(() -> Component.translatable("commands.team.add.success", playerteam.getFormattedDisplayName()), true);
            return scoreboard.getPlayerTeams().size();
        }
    }

    private static int listMembers(CommandSourceStack source, PlayerTeam team) {
        Collection<String> collection = team.getPlayers();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.team.list.members.empty", team.getFormattedDisplayName()), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.team.list.members.success", team.getFormattedDisplayName(), collection.size(), ComponentUtils.formatList(collection)
                    ),
                false
            );
        }

        return collection.size();
    }

    private static int listTeams(CommandSourceStack source) {
        Collection<PlayerTeam> collection = source.getServer().getScoreboard().getPlayerTeams();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.team.list.teams.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.team.list.teams.success", collection.size(), ComponentUtils.formatList(collection, PlayerTeam::getFormattedDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    private static int setPrefix(CommandSourceStack source, PlayerTeam team, Component prefix) {
        team.setPlayerPrefix(prefix);
        source.sendSuccess(() -> Component.translatable("commands.team.option.prefix.success", prefix), false);
        return 1;
    }

    private static int setSuffix(CommandSourceStack source, PlayerTeam team, Component suffix) {
        team.setPlayerSuffix(suffix);
        source.sendSuccess(() -> Component.translatable("commands.team.option.suffix.success", suffix), false);
        return 1;
    }
}
