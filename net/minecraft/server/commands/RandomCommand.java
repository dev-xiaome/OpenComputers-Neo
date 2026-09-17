package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;

public class RandomCommand {
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_LARGE = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_large")
    );
    private static final SimpleCommandExceptionType ERROR_RANGE_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.random.error.range_too_small")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("random")
                .then(drawRandomValueTree("value", false))
                .then(drawRandomValueTree("roll", true))
                .then(
                    Commands.literal("reset")
                        .requires(p_295133_ -> p_295133_.hasPermission(2))
                        .then(
                            Commands.literal("*")
                                .executes(p_295916_ -> resetAllSequences(p_295916_.getSource()))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            p_294436_ -> resetAllSequencesAndSetNewDefaults(
                                                    p_294436_.getSource(), IntegerArgumentType.getInteger(p_294436_, "seed"), true, true
                                                )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    p_295162_ -> resetAllSequencesAndSetNewDefaults(
                                                            p_295162_.getSource(),
                                                            IntegerArgumentType.getInteger(p_295162_, "seed"),
                                                            BoolArgumentType.getBool(p_295162_, "includeWorldSeed"),
                                                            true
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            p_295871_ -> resetAllSequencesAndSetNewDefaults(
                                                                    p_295871_.getSource(),
                                                                    IntegerArgumentType.getInteger(p_295871_, "seed"),
                                                                    BoolArgumentType.getBool(p_295871_, "includeWorldSeed"),
                                                                    BoolArgumentType.getBool(p_295871_, "includeSequenceId")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("sequence", ResourceLocationArgument.id())
                                .suggests(RandomCommand::suggestRandomSequence)
                                .executes(p_295053_ -> resetSequence(p_295053_.getSource(), ResourceLocationArgument.getId(p_295053_, "sequence")))
                                .then(
                                    Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(
                                            p_295487_ -> resetSequence(
                                                    p_295487_.getSource(),
                                                    ResourceLocationArgument.getId(p_295487_, "sequence"),
                                                    IntegerArgumentType.getInteger(p_295487_, "seed"),
                                                    true,
                                                    true
                                                )
                                        )
                                        .then(
                                            Commands.argument("includeWorldSeed", BoolArgumentType.bool())
                                                .executes(
                                                    p_294832_ -> resetSequence(
                                                            p_294832_.getSource(),
                                                            ResourceLocationArgument.getId(p_294832_, "sequence"),
                                                            IntegerArgumentType.getInteger(p_294832_, "seed"),
                                                            BoolArgumentType.getBool(p_294832_, "includeWorldSeed"),
                                                            true
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("includeSequenceId", BoolArgumentType.bool())
                                                        .executes(
                                                            p_295213_ -> resetSequence(
                                                                    p_295213_.getSource(),
                                                                    ResourceLocationArgument.getId(p_295213_, "sequence"),
                                                                    IntegerArgumentType.getInteger(p_295213_, "seed"),
                                                                    BoolArgumentType.getBool(p_295213_, "includeWorldSeed"),
                                                                    BoolArgumentType.getBool(p_295213_, "includeSequenceId")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> drawRandomValueTree(String subcommand, boolean displayResult) {
        return Commands.literal(subcommand)
            .then(
                Commands.argument("range", RangeArgument.intRange())
                    .executes(p_294702_ -> randomSample(p_294702_.getSource(), RangeArgument.Ints.getRange(p_294702_, "range"), null, displayResult))
                    .then(
                        Commands.argument("sequence", ResourceLocationArgument.id())
                            .suggests(RandomCommand::suggestRandomSequence)
                            .requires(p_296287_ -> p_296287_.hasPermission(2))
                            .executes(
                                p_295226_ -> randomSample(
                                        p_295226_.getSource(),
                                        RangeArgument.Ints.getRange(p_295226_, "range"),
                                        ResourceLocationArgument.getId(p_295226_, "sequence"),
                                        displayResult
                                    )
                            )
                    )
            );
    }

    private static CompletableFuture<Suggestions> suggestRandomSequence(CommandContext<CommandSourceStack> context, SuggestionsBuilder suggestionsBuilder) {
        List<String> list = Lists.newArrayList();
        context.getSource().getLevel().getRandomSequences().forAllSequences((p_294879_, p_294337_) -> list.add(p_294879_.toString()));
        return SharedSuggestionProvider.suggest(list, suggestionsBuilder);
    }

    private static int randomSample(CommandSourceStack source, MinMaxBounds.Ints range, @Nullable ResourceLocation sequence, boolean displayResult) throws CommandSyntaxException {
        RandomSource randomsource;
        if (sequence != null) {
            randomsource = source.getLevel().getRandomSequence(sequence);
        } else {
            randomsource = source.getLevel().getRandom();
        }

        int i = range.min().orElse(Integer.MIN_VALUE);
        int j = range.max().orElse(Integer.MAX_VALUE);
        long k = (long)j - (long)i;
        if (k == 0L) {
            throw ERROR_RANGE_TOO_SMALL.create();
        } else if (k >= 2147483647L) {
            throw ERROR_RANGE_TOO_LARGE.create();
        } else {
            int l = Mth.randomBetweenInclusive(randomsource, i, j);
            if (displayResult) {
                source.getServer()
                    .getPlayerList()
                    .broadcastSystemMessage(Component.translatable("commands.random.roll", source.getDisplayName(), l, i, j), false);
            } else {
                source.sendSuccess(() -> Component.translatable("commands.random.sample.success", l), false);
            }

            return l;
        }
    }

    private static int resetSequence(CommandSourceStack source, ResourceLocation sequence) throws CommandSyntaxException {
        source.getLevel().getRandomSequences().reset(sequence);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetSequence(CommandSourceStack source, ResourceLocation sequence, int seed, boolean includeWorldSeed, boolean includeSequenceId) throws CommandSyntaxException {
        source.getLevel().getRandomSequences().reset(sequence, seed, includeWorldSeed, includeSequenceId);
        source.sendSuccess(() -> Component.translatable("commands.random.reset.success", Component.translationArg(sequence)), false);
        return 1;
    }

    private static int resetAllSequences(CommandSourceStack source) {
        int i = source.getLevel().getRandomSequences().clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }

    private static int resetAllSequencesAndSetNewDefaults(CommandSourceStack source, int seed, boolean includeWorldSeed, boolean includeSequenceId) {
        RandomSequences randomsequences = source.getLevel().getRandomSequences();
        randomsequences.setSeedDefaults(seed, includeWorldSeed, includeSequenceId);
        int i = randomsequences.clear();
        source.sendSuccess(() -> Component.translatable("commands.random.reset.all.success", i), false);
        return i;
    }
}
