package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class TriggerCommand {
    private static final SimpleCommandExceptionType ERROR_NOT_PRIMED = new SimpleCommandExceptionType(
        Component.translatable("commands.trigger.failed.unprimed")
    );
    private static final SimpleCommandExceptionType ERROR_INVALID_OBJECTIVE = new SimpleCommandExceptionType(
        Component.translatable("commands.trigger.failed.invalid")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("trigger")
                .then(
                    Commands.argument("objective", ObjectiveArgument.objective())
                        .suggests((p_139146_, p_139147_) -> suggestObjectives(p_139146_.getSource(), p_139147_))
                        .executes(
                            p_313576_ -> simpleTrigger(
                                    p_313576_.getSource(), p_313576_.getSource().getPlayerOrException(), ObjectiveArgument.getObjective(p_313576_, "objective")
                                )
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(
                                            p_313577_ -> addValue(
                                                    p_313577_.getSource(),
                                                    p_313577_.getSource().getPlayerOrException(),
                                                    ObjectiveArgument.getObjective(p_313577_, "objective"),
                                                    IntegerArgumentType.getInteger(p_313577_, "value")
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(
                                            p_313581_ -> setValue(
                                                    p_313581_.getSource(),
                                                    p_313581_.getSource().getPlayerOrException(),
                                                    ObjectiveArgument.getObjective(p_313581_, "objective"),
                                                    IntegerArgumentType.getInteger(p_313581_, "value")
                                                )
                                        )
                                )
                        )
                )
        );
    }

    public static CompletableFuture<Suggestions> suggestObjectives(CommandSourceStack source, SuggestionsBuilder builder) {
        ScoreHolder scoreholder = source.getEntity();
        List<String> list = Lists.newArrayList();
        if (scoreholder != null) {
            Scoreboard scoreboard = source.getServer().getScoreboard();

            for (Objective objective : scoreboard.getObjectives()) {
                if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                    ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreholder, objective);
                    if (readonlyscoreinfo != null && !readonlyscoreinfo.isLocked()) {
                        list.add(objective.getName());
                    }
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, builder);
    }

    private static int addValue(CommandSourceStack source, ServerPlayer player, Objective objective, int value) throws CommandSyntaxException {
        ScoreAccess scoreaccess = getScore(source.getServer().getScoreboard(), player, objective);
        int i = scoreaccess.add(value);
        source.sendSuccess(() -> Component.translatable("commands.trigger.add.success", objective.getFormattedDisplayName(), value), true);
        return i;
    }

    private static int setValue(CommandSourceStack source, ServerPlayer player, Objective objective, int value) throws CommandSyntaxException {
        ScoreAccess scoreaccess = getScore(source.getServer().getScoreboard(), player, objective);
        scoreaccess.set(value);
        source.sendSuccess(() -> Component.translatable("commands.trigger.set.success", objective.getFormattedDisplayName(), value), true);
        return value;
    }

    private static int simpleTrigger(CommandSourceStack source, ServerPlayer player, Objective objective) throws CommandSyntaxException {
        ScoreAccess scoreaccess = getScore(source.getServer().getScoreboard(), player, objective);
        int i = scoreaccess.add(1);
        source.sendSuccess(() -> Component.translatable("commands.trigger.simple.success", objective.getFormattedDisplayName()), true);
        return i;
    }

    private static ScoreAccess getScore(Scoreboard scoreboard, ScoreHolder scoreHolder, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ERROR_INVALID_OBJECTIVE.create();
        } else {
            ReadOnlyScoreInfo readonlyscoreinfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
            if (readonlyscoreinfo != null && !readonlyscoreinfo.isLocked()) {
                ScoreAccess scoreaccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
                scoreaccess.lock();
                return scoreaccess;
            } else {
                throw ERROR_NOT_PRIMED.create();
            }
        }
    }
}
