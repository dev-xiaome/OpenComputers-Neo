package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.ScoreboardSlotArgument;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ScoreboardCommand {
    private static final SimpleCommandExceptionType ERROR_OBJECTIVE_ALREADY_EXISTS = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.add.duplicate")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_EMPTY = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadyEmpty")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_SET = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadySet")
    );
    private static final SimpleCommandExceptionType ERROR_TRIGGER_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_TRIGGER = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.invalid")
    );
    private static final Dynamic2CommandExceptionType ERROR_NO_VALUE = new Dynamic2CommandExceptionType(
        (p_304296_, p_304297_) -> Component.translatableEscape("commands.scoreboard.players.get.null", p_304296_, p_304297_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("scoreboard")
                .requires(p_138552_ -> p_138552_.hasPermission(2))
                .then(
                    Commands.literal("objectives")
                        .then(Commands.literal("list").executes(p_138585_ -> listObjectives(p_138585_.getSource())))
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("objective", StringArgumentType.word())
                                        .then(
                                            Commands.argument("criteria", ObjectiveCriteriaArgument.criteria())
                                                .executes(
                                                    p_138583_ -> addObjective(
                                                            p_138583_.getSource(),
                                                            StringArgumentType.getString(p_138583_, "objective"),
                                                            ObjectiveCriteriaArgument.getCriteria(p_138583_, "criteria"),
                                                            Component.literal(StringArgumentType.getString(p_138583_, "objective"))
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(context))
                                                        .executes(
                                                            p_138581_ -> addObjective(
                                                                    p_138581_.getSource(),
                                                                    StringArgumentType.getString(p_138581_, "objective"),
                                                                    ObjectiveCriteriaArgument.getCriteria(p_138581_, "criteria"),
                                                                    ComponentArgument.getComponent(p_138581_, "displayName")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("modify")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .then(
                                            Commands.literal("displayname")
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(context))
                                                        .executes(
                                                            p_138579_ -> setDisplayName(
                                                                    p_138579_.getSource(),
                                                                    ObjectiveArgument.getObjective(p_138579_, "objective"),
                                                                    ComponentArgument.getComponent(p_138579_, "displayName")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(createRenderTypeModify())
                                        .then(
                                            Commands.literal("displayautoupdate")
                                                .then(
                                                    Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(
                                                            p_313527_ -> setDisplayAutoUpdate(
                                                                    p_313527_.getSource(),
                                                                    ObjectiveArgument.getObjective(p_313527_, "objective"),
                                                                    BoolArgumentType.getBool(p_313527_, "value")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            addNumberFormats(
                                                context,
                                                Commands.literal("numberformat"),
                                                (p_313531_, p_313532_) -> setObjectiveFormat(
                                                        p_313531_.getSource(), ObjectiveArgument.getObjective(p_313531_, "objective"), p_313532_
                                                    )
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .executes(p_138577_ -> removeObjective(p_138577_.getSource(), ObjectiveArgument.getObjective(p_138577_, "objective")))
                                )
                        )
                        .then(
                            Commands.literal("setdisplay")
                                .then(
                                    Commands.argument("slot", ScoreboardSlotArgument.displaySlot())
                                        .executes(
                                            p_293788_ -> clearDisplaySlot(p_293788_.getSource(), ScoreboardSlotArgument.getDisplaySlot(p_293788_, "slot"))
                                        )
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    p_293785_ -> setDisplaySlot(
                                                            p_293785_.getSource(),
                                                            ScoreboardSlotArgument.getDisplaySlot(p_293785_, "slot"),
                                                            ObjectiveArgument.getObjective(p_293785_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("players")
                        .then(
                            Commands.literal("list")
                                .executes(p_138571_ -> listTrackedPlayers(p_138571_.getSource()))
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(p_313550_ -> listTrackedPlayerScores(p_313550_.getSource(), ScoreHolderArgument.getName(p_313550_, "target")))
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer())
                                                        .executes(
                                                            p_138567_ -> setScore(
                                                                    p_138567_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138567_, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(p_138567_, "objective"),
                                                                    IntegerArgumentType.getInteger(p_138567_, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("get")
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    p_313543_ -> getScore(
                                                            p_313543_.getSource(),
                                                            ScoreHolderArgument.getName(p_313543_, "target"),
                                                            ObjectiveArgument.getObjective(p_313543_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            p_138563_ -> addScore(
                                                                    p_138563_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138563_, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(p_138563_, "objective"),
                                                                    IntegerArgumentType.getInteger(p_138563_, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            p_138561_ -> removeScore(
                                                                    p_138561_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138561_, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(p_138561_, "objective"),
                                                                    IntegerArgumentType.getInteger(p_138561_, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("reset")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(
                                            p_138559_ -> resetScores(
                                                    p_138559_.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(p_138559_, "targets")
                                                )
                                        )
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    p_138550_ -> resetScore(
                                                            p_138550_.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_138550_, "targets"),
                                                            ObjectiveArgument.getObjective(p_138550_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("enable")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .suggests(
                                                    (p_138473_, p_138474_) -> suggestTriggers(
                                                            p_138473_.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_138473_, "targets"),
                                                            p_138474_
                                                        )
                                                )
                                                .executes(
                                                    p_138537_ -> enableTrigger(
                                                            p_138537_.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_138537_, "targets"),
                                                            ObjectiveArgument.getObjective(p_138537_, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("display")
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    Commands.argument("objective", ObjectiveArgument.objective())
                                                        .then(
                                                            Commands.argument("name", ComponentArgument.textComponent(context))
                                                                .executes(
                                                                    p_313517_ -> setScoreDisplay(
                                                                            p_313517_.getSource(),
                                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(p_313517_, "targets"),
                                                                            ObjectiveArgument.getObjective(p_313517_, "objective"),
                                                                            ComponentArgument.getComponent(p_313517_, "name")
                                                                        )
                                                                )
                                                        )
                                                        .executes(
                                                            p_313555_ -> setScoreDisplay(
                                                                    p_313555_.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_313555_, "targets"),
                                                                    ObjectiveArgument.getObjective(p_313555_, "objective"),
                                                                    null
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("numberformat")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    addNumberFormats(
                                                        context,
                                                        Commands.argument("objective", ObjectiveArgument.objective()),
                                                        (p_313512_, p_313513_) -> setScoreNumberFormat(
                                                                p_313512_.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(p_313512_, "targets"),
                                                                ObjectiveArgument.getObjective(p_313512_, "objective"),
                                                                p_313513_
                                                            )
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("operation")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("targetObjective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("operation", OperationArgument.operation())
                                                        .then(
                                                            Commands.argument("source", ScoreHolderArgument.scoreHolders())
                                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                                .then(
                                                                    Commands.argument("sourceObjective", ObjectiveArgument.objective())
                                                                        .executes(
                                                                            p_138471_ -> performOperation(
                                                                                    p_138471_.getSource(),
                                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138471_, "targets"),
                                                                                    ObjectiveArgument.getWritableObjective(p_138471_, "targetObjective"),
                                                                                    OperationArgument.getOperation(p_138471_, "operation"),
                                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(p_138471_, "source"),
                                                                                    ObjectiveArgument.getObjective(p_138471_, "sourceObjective")
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> addNumberFormats(
        CommandBuildContext context, ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, ScoreboardCommand.NumberFormatCommandExecutor executor
    ) {
        return argumentBuilder.then(Commands.literal("blank").executes(p_313547_ -> executor.run(p_313547_, BlankFormat.INSTANCE)))
            .then(Commands.literal("fixed").then(Commands.argument("contents", ComponentArgument.textComponent(context)).executes(p_313560_ -> {
                Component component = ComponentArgument.getComponent(p_313560_, "contents");
                return executor.run(p_313560_, new FixedFormat(component));
            })))
            .then(Commands.literal("styled").then(Commands.argument("style", StyleArgument.style(context)).executes(p_313511_ -> {
                Style style = StyleArgument.getStyle(p_313511_, "style");
                return executor.run(p_313511_, new StyledFormat(style));
            })))
            .executes(p_313549_ -> executor.run(p_313549_, null));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRenderTypeModify() {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("rendertype");

        for (ObjectiveCriteria.RenderType objectivecriteria$rendertype : ObjectiveCriteria.RenderType.values()) {
            literalargumentbuilder.then(
                Commands.literal(objectivecriteria$rendertype.getId())
                    .executes(
                        p_138532_ -> setRenderType(p_138532_.getSource(), ObjectiveArgument.getObjective(p_138532_, "objective"), objectivecriteria$rendertype)
                    )
            );
        }

        return literalargumentbuilder;
    }

    private static CompletableFuture<Suggestions> suggestTriggers(CommandSourceStack source, Collection<ScoreHolder> targets, SuggestionsBuilder suggestions) {
        List<String> list = Lists.newArrayList();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (Objective objective : scoreboard.getObjectives()) {
            if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                boolean flag = false;

                for (ScoreHolder scoreholder : targets) {
                    ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
                    if (readonlyscoreinfo == null || readonlyscoreinfo.isLocked()) {
                        flag = true;
                        break;
                    }
                }

                if (flag) {
                    list.add(objective.getName());
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, suggestions);
    }

    private static int getScore(CommandSourceStack source, ScoreHolder scoreHolder, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
        if (readonlyscoreinfo == null) {
            throw ERROR_NO_VALUE.create(objective.getName(), scoreHolder.getFeedbackDisplayName());
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.get.success",
                        scoreHolder.getFeedbackDisplayName(),
                        readonlyscoreinfo.value(),
                        objective.getFormattedDisplayName()
                    ),
                false
            );
            return readonlyscoreinfo.value();
        }
    }

    private static Component getFirstTargetName(Collection<ScoreHolder> scores) {
        return scores.iterator().next().getFeedbackDisplayName();
    }

    private static int performOperation(
        CommandSourceStack source,
        Collection<ScoreHolder> targets,
        Objective targetObjectives,
        OperationArgument.Operation operation,
        Collection<ScoreHolder> sourceEntities,
        Objective sourceObjective
    ) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : targets) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, targetObjectives);

            for (ScoreHolder scoreholder1 : sourceEntities) {
                ScoreAccess scoreaccess1 = scoreboard.getOrCreatePlayerScore(scoreholder1, sourceObjective);
                operation.apply(scoreaccess, scoreaccess1);
            }

            i += scoreaccess.get();
        }

        if (targets.size() == 1) {
            int j = i;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.operation.success.single", targetObjectives.getFormattedDisplayName(), getFirstTargetName(targets), j
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.operation.success.multiple", targetObjectives.getFormattedDisplayName(), targets.size()),
                true
            );
        }

        return i;
    }

    private static int enableTrigger(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ERROR_NOT_TRIGGER.create();
        } else {
            Scoreboard scoreboard = source.getServer().getScoreboard();
            int i = 0;

            for (ScoreHolder scoreholder : targets) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);
                if (scoreaccess.locked()) {
                    scoreaccess.unlock();
                    i++;
                }
            }

            if (i == 0) {
                throw ERROR_TRIGGER_ALREADY_ENABLED.create();
            } else {
                if (targets.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.enable.success.single", objective.getFormattedDisplayName(), getFirstTargetName(targets)
                            ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.enable.success.multiple", objective.getFormattedDisplayName(), targets.size()
                            ),
                        true
                    );
                }

                return i;
            }
        }
    }

    private static int resetScores(CommandSourceStack source, Collection<ScoreHolder> targets) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : targets) {
            scoreboard.resetAllPlayerScores(scoreholder);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.single", getFirstTargetName(targets)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int resetScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : targets) {
            scoreboard.resetSinglePlayerScore(scoreholder, objective);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.reset.specific.single", objective.getFormattedDisplayName(), getFirstTargetName(targets)
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.reset.specific.multiple", objective.getFormattedDisplayName(), targets.size()),
                true
            );
        }

        return targets.size();
    }

    private static int setScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int newValue) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreholder, objective).set(newValue);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.set.success.single", objective.getFormattedDisplayName(), getFirstTargetName(targets), newValue
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.set.success.multiple", objective.getFormattedDisplayName(), targets.size(), newValue
                    ),
                true
            );
        }

        return newValue * targets.size();
    }

    private static int setScoreDisplay(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, @Nullable Component displayName) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreholder, objective).display(displayName);
        }

        if (displayName == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.name.clear.success.single", getFirstTargetName(targets), objective.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.name.clear.success.multiple", targets.size(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            }
        } else if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.name.set.success.single",
                        displayName,
                        getFirstTargetName(targets),
                        objective.getFormattedDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.name.set.success.multiple", displayName, targets.size(), objective.getFormattedDisplayName()
                    ),
                true
            );
        }

        return targets.size();
    }

    private static int setScoreNumberFormat(
        CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, @Nullable NumberFormat numberFormat
    ) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreholder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreholder, objective).numberFormatOverride(numberFormat);
        }

        if (numberFormat == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.numberFormat.clear.success.single",
                            getFirstTargetName(targets),
                            objective.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.numberFormat.clear.success.multiple", targets.size(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            }
        } else if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.set.success.single",
                        getFirstTargetName(targets),
                        objective.getFormattedDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.set.success.multiple", targets.size(), objective.getFormattedDisplayName()
                    ),
                true
            );
        }

        return targets.size();
    }

    private static int addScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int amount) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : targets) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);
            scoreaccess.set(scoreaccess.get() + amount);
            i += scoreaccess.get();
        }

        if (targets.size() == 1) {
            int j = i;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.add.success.single", amount, objective.getFormattedDisplayName(), getFirstTargetName(targets), j
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.add.success.multiple", amount, objective.getFormattedDisplayName(), targets.size()
                    ),
                true
            );
        }

        return i;
    }

    private static int removeScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int amount) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreholder : targets) {
            ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreholder, objective);
            scoreaccess.set(scoreaccess.get() - amount);
            i += scoreaccess.get();
        }

        if (targets.size() == 1) {
            int j = i;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.remove.success.single", amount, objective.getFormattedDisplayName(), getFirstTargetName(targets), j
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.remove.success.multiple", amount, objective.getFormattedDisplayName(), targets.size()
                    ),
                true
            );
        }

        return i;
    }

    private static int listTrackedPlayers(CommandSourceStack source) {
        Collection<ScoreHolder> collection = source.getServer().getScoreboard().getTrackedPlayers();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.list.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, ScoreHolder::getFeedbackDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    private static int listTrackedPlayerScores(CommandSourceStack source, ScoreHolder score) {
        Object2IntMap<Objective> object2intmap = source.getServer().getScoreboard().listPlayerScores(score);
        if (object2intmap.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.entity.empty", score.getFeedbackDisplayName()), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.list.entity.success", score.getFeedbackDisplayName(), object2intmap.size()),
                false
            );
            Object2IntMaps.fastForEach(
                object2intmap,
                p_313504_ -> source.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.list.entity.entry",
                                ((Objective)p_313504_.getKey()).getFormattedDisplayName(),
                                p_313504_.getIntValue()
                            ),
                        false
                    )
            );
        }

        return object2intmap.size();
    }

    private static int clearDisplaySlot(CommandSourceStack source, DisplaySlot slot) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(slot) == null) {
            throw ERROR_DISPLAY_SLOT_ALREADY_EMPTY.create();
        } else {
            scoreboard.setDisplayObjective(slot, null);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.display.cleared", slot.getSerializedName()), true);
            return 0;
        }
    }

    private static int setDisplaySlot(CommandSourceStack source, DisplaySlot slot, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(slot) == objective) {
            throw ERROR_DISPLAY_SLOT_ALREADY_SET.create();
        } else {
            scoreboard.setDisplayObjective(slot, objective);
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.display.set", slot.getSerializedName(), objective.getDisplayName()), true
            );
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, Objective objective, Component displayName) {
        if (!objective.getDisplayName().equals(displayName)) {
            objective.setDisplayName(displayName);
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.modify.displayname", objective.getName(), objective.getFormattedDisplayName()),
                true
            );
        }

        return 0;
    }

    private static int setDisplayAutoUpdate(CommandSourceStack source, Objective objective, boolean displayAutoUpdate) {
        if (objective.displayAutoUpdate() != displayAutoUpdate) {
            objective.setDisplayAutoUpdate(displayAutoUpdate);
            if (displayAutoUpdate) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.objectives.modify.displayAutoUpdate.enable", objective.getName(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.objectives.modify.displayAutoUpdate.disable", objective.getName(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            }
        }

        return 0;
    }

    private static int setObjectiveFormat(CommandSourceStack source, Objective objective, @Nullable NumberFormat format) {
        objective.setNumberFormat(format);
        if (format != null) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.set", objective.getName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.clear", objective.getName()), true);
        }

        return 0;
    }

    private static int setRenderType(CommandSourceStack source, Objective objective, ObjectiveCriteria.RenderType renderType) {
        if (objective.getRenderType() != renderType) {
            objective.setRenderType(renderType);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.rendertype", objective.getFormattedDisplayName()), true);
        }

        return 0;
    }

    private static int removeObjective(CommandSourceStack source, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        scoreboard.removeObjective(objective);
        source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.remove.success", objective.getFormattedDisplayName()), true);
        return scoreboard.getObjectives().size();
    }

    private static int addObjective(CommandSourceStack source, String name, ObjectiveCriteria criteria, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getObjective(name) != null) {
            throw ERROR_OBJECTIVE_ALREADY_EXISTS.create();
        } else {
            scoreboard.addObjective(name, criteria, displayName, criteria.getDefaultRenderType(), false, null);
            Objective objective = scoreboard.getObjective(name);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.add.success", objective.getFormattedDisplayName()), true);
            return scoreboard.getObjectives().size();
        }
    }

    private static int listObjectives(CommandSourceStack source) {
        Collection<Objective> collection = source.getServer().getScoreboard().getObjectives();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.list.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.objectives.list.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, Objective::getFormattedDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    @FunctionalInterface
    public interface NumberFormatCommandExecutor {
        int run(CommandContext<CommandSourceStack> context, @Nullable NumberFormat format) throws CommandSyntaxException;
    }
}
