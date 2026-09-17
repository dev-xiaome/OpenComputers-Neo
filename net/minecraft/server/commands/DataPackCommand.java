package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class DataPackCommand {
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PACK = new DynamicCommandExceptionType(
        p_304196_ -> Component.translatableEscape("commands.datapack.unknown", p_304196_)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_ENABLED = new DynamicCommandExceptionType(
        p_304199_ -> Component.translatableEscape("commands.datapack.enable.failed", p_304199_)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_DISABLED = new DynamicCommandExceptionType(
        p_304200_ -> Component.translatableEscape("commands.datapack.disable.failed", p_304200_)
    );
    private static final DynamicCommandExceptionType ERROR_CANNOT_DISABLE_FEATURE = new DynamicCommandExceptionType(
        p_333496_ -> Component.translatableEscape("commands.datapack.disable.failed.feature", p_333496_)
    );
    private static final Dynamic2CommandExceptionType ERROR_PACK_FEATURES_NOT_ENABLED = new Dynamic2CommandExceptionType(
        (p_304197_, p_304198_) -> Component.translatableEscape("commands.datapack.enable.failed.no_flags", p_304197_, p_304198_)
    );
    private static final SuggestionProvider<CommandSourceStack> SELECTED_PACKS = (p_136848_, p_136849_) -> SharedSuggestionProvider.suggest(
            p_136848_.getSource().getServer().getPackRepository().getSelectedIds().stream().map(StringArgumentType::escapeIfRequired), p_136849_
        );
    private static final SuggestionProvider<CommandSourceStack> UNSELECTED_PACKS = (p_248113_, p_248114_) -> {
        PackRepository packrepository = p_248113_.getSource().getServer().getPackRepository();
        Collection<String> collection = packrepository.getSelectedIds();
        FeatureFlagSet featureflagset = p_248113_.getSource().enabledFeatures();
        return SharedSuggestionProvider.suggest(
            packrepository.getAvailablePacks()
                .stream()
                .filter(p_248116_ -> p_248116_.getRequestedFeatures().isSubsetOf(featureflagset))
                .map(Pack::getId)
                .filter(p_250072_ -> !collection.contains(p_250072_))
                .map(StringArgumentType::escapeIfRequired),
            p_248114_
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("datapack")
                .requires(p_136872_ -> p_136872_.hasPermission(2))
                .then(
                    Commands.literal("enable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(UNSELECTED_PACKS)
                                .executes(
                                    p_136876_ -> enablePack(
                                            p_136876_.getSource(),
                                            getPack(p_136876_, "name", true),
                                            (p_180059_, p_180060_) -> p_180060_.getDefaultPosition().insert(p_180059_, p_180060_, Pack::selectionConfig, false)
                                        )
                                )
                                .then(
                                    Commands.literal("after")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    p_136880_ -> enablePack(
                                                            p_136880_.getSource(),
                                                            getPack(p_136880_, "name", true),
                                                            (p_180056_, p_180057_) -> p_180056_.add(
                                                                    p_180056_.indexOf(getPack(p_136880_, "existing", false)) + 1, p_180057_
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("before")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    p_136878_ -> enablePack(
                                                            p_136878_.getSource(),
                                                            getPack(p_136878_, "name", true),
                                                            (p_180046_, p_180047_) -> p_180046_.add(
                                                                    p_180046_.indexOf(getPack(p_136878_, "existing", false)), p_180047_
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("last")
                                        .executes(p_136874_ -> enablePack(p_136874_.getSource(), getPack(p_136874_, "name", true), List::add))
                                )
                                .then(
                                    Commands.literal("first")
                                        .executes(
                                            p_136882_ -> enablePack(
                                                    p_136882_.getSource(),
                                                    getPack(p_136882_, "name", true),
                                                    (p_180052_, p_180053_) -> p_180052_.add(0, p_180053_)
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("disable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(SELECTED_PACKS)
                                .executes(p_136870_ -> disablePack(p_136870_.getSource(), getPack(p_136870_, "name", false)))
                        )
                )
                .then(
                    Commands.literal("list")
                        .executes(p_136864_ -> listPacks(p_136864_.getSource()))
                        .then(Commands.literal("available").executes(p_136846_ -> listAvailablePacks(p_136846_.getSource())))
                        .then(Commands.literal("enabled").executes(p_136811_ -> listEnabledPacks(p_136811_.getSource())))
                )
        );
    }

    /**
     * Enables the given pack.
     *
     * @return The number of packs that are loaded after this operation.
     */
    private static int enablePack(CommandSourceStack source, Pack pack, DataPackCommand.Inserter priorityCallback) throws CommandSyntaxException {
        PackRepository packrepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packrepository.getSelectedPacks());
        priorityCallback.apply(list, pack);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.enable", pack.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    /**
     * Disables the given pack.
     *
     * @return The number of packs that are loaded after this operation.
     */
    private static int disablePack(CommandSourceStack source, Pack pack) {
        PackRepository packrepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packrepository.getSelectedPacks());
        list.remove(pack);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.disable", pack.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    /**
     * Sends a list of both enabled and available packs to the user.
     *
     * @return The total number of packs.
     */
    private static int listPacks(CommandSourceStack source) {
        return listEnabledPacks(source) + listAvailablePacks(source);
    }

    /**
     * Sends a list of available packs to the user.
     *
     * @return The number of available packs.
     */
    private static int listAvailablePacks(CommandSourceStack source) {
        PackRepository packrepository = source.getServer().getPackRepository();
        packrepository.reload();
        Collection<Pack> collection = packrepository.getSelectedPacks();
        Collection<Pack> collection1 = packrepository.getAvailablePacks();
        FeatureFlagSet featureflagset = source.enabledFeatures();
        List<Pack> list = collection1.stream()
            .filter(p_248121_ -> !collection.contains(p_248121_) && p_248121_.getRequestedFeatures().isSubsetOf(featureflagset))
            .toList();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.available.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.datapack.list.available.success", list.size(), ComponentUtils.formatList(list, p_136844_ -> p_136844_.getChatLink(false))
                    ),
                false
            );
        }

        return list.size();
    }

    /**
     * Sends a list of enabled packs to the user.
     *
     * @return The number of enabled packs.
     */
    private static int listEnabledPacks(CommandSourceStack source) {
        PackRepository packrepository = source.getServer().getPackRepository();
        packrepository.reload();
        Collection<? extends Pack> collection = packrepository.getSelectedPacks().stream().filter(p -> !p.isHidden()).toList();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.enabled.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.datapack.list.enabled.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, p_136807_ -> p_136807_.getChatLink(true))
                    ),
                false
            );
        }

        return collection.size();
    }

    private static Pack getPack(CommandContext<CommandSourceStack> context, String name, boolean enabling) throws CommandSyntaxException {
        String s = StringArgumentType.getString(context, name);
        PackRepository packrepository = context.getSource().getServer().getPackRepository();
        Pack pack = packrepository.getPack(s);
        if (pack == null) {
            throw ERROR_UNKNOWN_PACK.create(s);
        } else {
            boolean flag = packrepository.getSelectedPacks().contains(pack);
            if (enabling && flag) {
                throw ERROR_PACK_ALREADY_ENABLED.create(s);
            } else if (!enabling && !flag) {
                throw ERROR_PACK_ALREADY_DISABLED.create(s);
            } else {
                FeatureFlagSet featureflagset = context.getSource().enabledFeatures();
                FeatureFlagSet featureflagset1 = pack.getRequestedFeatures();
                if (!enabling && !featureflagset1.isEmpty() && pack.getPackSource() == PackSource.FEATURE) {
                    throw ERROR_CANNOT_DISABLE_FEATURE.create(s);
                } else if (!featureflagset1.isSubsetOf(featureflagset)) {
                    throw ERROR_PACK_FEATURES_NOT_ENABLED.create(s, FeatureFlags.printMissingFlags(featureflagset, featureflagset1));
                } else {
                    return pack;
                }
            }
        }
    }

    interface Inserter {
        void apply(List<Pack> currentPacks, Pack pack) throws CommandSyntaxException;
    }
}
