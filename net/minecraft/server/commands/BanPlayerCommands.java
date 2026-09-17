package net.minecraft.server.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

public class BanPlayerCommands {
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.ban.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ban")
                .requires(p_136563_ -> p_136563_.hasPermission(3))
                .then(
                    Commands.argument("targets", GameProfileArgument.gameProfile())
                        .executes(p_136569_ -> banPlayers(p_136569_.getSource(), GameProfileArgument.getGameProfiles(p_136569_, "targets"), null))
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    p_136561_ -> banPlayers(
                                            p_136561_.getSource(),
                                            GameProfileArgument.getGameProfiles(p_136561_, "targets"),
                                            MessageArgument.getMessage(p_136561_, "reason")
                                        )
                                )
                        )
                )
        );
    }

    private static int banPlayers(CommandSourceStack source, Collection<GameProfile> gameProfiles, @Nullable Component reason) throws CommandSyntaxException {
        UserBanList userbanlist = source.getServer().getPlayerList().getBans();
        int i = 0;

        for (GameProfile gameprofile : gameProfiles) {
            if (!userbanlist.isBanned(gameprofile)) {
                UserBanListEntry userbanlistentry = new UserBanListEntry(
                    gameprofile, null, source.getTextName(), null, reason == null ? null : reason.getString()
                );
                userbanlist.add(userbanlistentry);
                i++;
                source.sendSuccess(
                    () -> Component.translatable("commands.ban.success", Component.literal(gameprofile.getName()), userbanlistentry.getReason()), true
                );
                ServerPlayer serverplayer = source.getServer().getPlayerList().getPlayer(gameprofile.getId());
                if (serverplayer != null) {
                    serverplayer.connection.disconnect(Component.translatable("multiplayer.disconnect.banned"));
                }
            }
        }

        if (i == 0) {
            throw ERROR_ALREADY_BANNED.create();
        } else {
            return i;
        }
    }
}
