package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;

public class WhitelistCommand {
    private static final SimpleCommandExceptionType ERROR_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.alreadyOn")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_DISABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.alreadyOff")
    );
    private static final SimpleCommandExceptionType ERROR_ALREADY_WHITELISTED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.add.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_WHITELISTED = new SimpleCommandExceptionType(
        Component.translatable("commands.whitelist.remove.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("whitelist")
                .requires(p_139234_ -> p_139234_.hasPermission(3))
                .then(Commands.literal("on").executes(p_139236_ -> enableWhitelist(p_139236_.getSource())))
                .then(Commands.literal("off").executes(p_139232_ -> disableWhitelist(p_139232_.getSource())))
                .then(Commands.literal("list").executes(p_139228_ -> showList(p_139228_.getSource())))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests(
                                    (p_139216_, p_139217_) -> {
                                        PlayerList playerlist = p_139216_.getSource().getServer().getPlayerList();
                                        return SharedSuggestionProvider.suggest(
                                            playerlist.getPlayers()
                                                .stream()
                                                .filter(p_352696_ -> !playerlist.getWhiteList().isWhiteListed(p_352696_.getGameProfile()))
                                                .map(p_352694_ -> p_352694_.getGameProfile().getName()),
                                            p_139217_
                                        );
                                    }
                                )
                                .executes(p_139224_ -> addPlayers(p_139224_.getSource(), GameProfileArgument.getGameProfiles(p_139224_, "targets")))
                        )
                )
                .then(
                    Commands.literal("remove")
                        .then(
                            Commands.argument("targets", GameProfileArgument.gameProfile())
                                .suggests(
                                    (p_139206_, p_139207_) -> SharedSuggestionProvider.suggest(
                                            p_139206_.getSource().getServer().getPlayerList().getWhiteListNames(), p_139207_
                                        )
                                )
                                .executes(p_139214_ -> removePlayers(p_139214_.getSource(), GameProfileArgument.getGameProfiles(p_139214_, "targets")))
                        )
                )
                .then(Commands.literal("reload").executes(p_139204_ -> reload(p_139204_.getSource())))
        );
    }

    private static int reload(CommandSourceStack source) {
        source.getServer().getPlayerList().reloadWhiteList();
        source.sendSuccess(() -> Component.translatable("commands.whitelist.reloaded"), true);
        source.getServer().kickUnlistedPlayers(source);
        return 1;
    }

    private static int addPlayers(CommandSourceStack source, Collection<GameProfile> players) throws CommandSyntaxException {
        UserWhiteList userwhitelist = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (GameProfile gameprofile : players) {
            if (!userwhitelist.isWhiteListed(gameprofile)) {
                UserWhiteListEntry userwhitelistentry = new UserWhiteListEntry(gameprofile);
                userwhitelist.add(userwhitelistentry);
                source.sendSuccess(() -> Component.translatable("commands.whitelist.add.success", Component.literal(gameprofile.getName())), true);
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_WHITELISTED.create();
        } else {
            return i;
        }
    }

    private static int removePlayers(CommandSourceStack source, Collection<GameProfile> players) throws CommandSyntaxException {
        UserWhiteList userwhitelist = source.getServer().getPlayerList().getWhiteList();
        int i = 0;

        for (GameProfile gameprofile : players) {
            if (userwhitelist.isWhiteListed(gameprofile)) {
                UserWhiteListEntry userwhitelistentry = new UserWhiteListEntry(gameprofile);
                userwhitelist.remove(userwhitelistentry);
                source.sendSuccess(() -> Component.translatable("commands.whitelist.remove.success", Component.literal(gameprofile.getName())), true);
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_NOT_WHITELISTED.create();
        } else {
            source.getServer().kickUnlistedPlayers(source);
            return i;
        }
    }

    private static int enableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        PlayerList playerlist = source.getServer().getPlayerList();
        if (playerlist.isUsingWhitelist()) {
            throw ERROR_ALREADY_ENABLED.create();
        } else {
            playerlist.setUsingWhiteList(true);
            source.sendSuccess(() -> Component.translatable("commands.whitelist.enabled"), true);
            source.getServer().kickUnlistedPlayers(source);
            return 1;
        }
    }

    private static int disableWhitelist(CommandSourceStack source) throws CommandSyntaxException {
        PlayerList playerlist = source.getServer().getPlayerList();
        if (!playerlist.isUsingWhitelist()) {
            throw ERROR_ALREADY_DISABLED.create();
        } else {
            playerlist.setUsingWhiteList(false);
            source.sendSuccess(() -> Component.translatable("commands.whitelist.disabled"), true);
            return 1;
        }
    }

    private static int showList(CommandSourceStack source) {
        String[] astring = source.getServer().getPlayerList().getWhiteListNames();
        if (astring.length == 0) {
            source.sendSuccess(() -> Component.translatable("commands.whitelist.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.whitelist.list", astring.length, String.join(", ", astring)), false);
        }

        return astring.length;
    }
}
