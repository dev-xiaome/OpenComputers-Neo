package net.minecraft.server.commands;

import com.google.common.net.InetAddresses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;

public class BanIpCommands {
    private static final SimpleCommandExceptionType ERROR_INVALID_IP = new SimpleCommandExceptionType(Component.translatable("commands.banip.invalid"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.banip.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ban-ip")
                .requires(p_136532_ -> p_136532_.hasPermission(3))
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .executes(p_136538_ -> banIpOrName(p_136538_.getSource(), StringArgumentType.getString(p_136538_, "target"), null))
                        .then(
                            Commands.argument("reason", MessageArgument.message())
                                .executes(
                                    p_136530_ -> banIpOrName(
                                            p_136530_.getSource(),
                                            StringArgumentType.getString(p_136530_, "target"),
                                            MessageArgument.getMessage(p_136530_, "reason")
                                        )
                                )
                        )
                )
        );
    }

    private static int banIpOrName(CommandSourceStack source, String username, @Nullable Component reason) throws CommandSyntaxException {
        if (InetAddresses.isInetAddress(username)) {
            return banIp(source, username, reason);
        } else {
            ServerPlayer serverplayer = source.getServer().getPlayerList().getPlayerByName(username);
            if (serverplayer != null) {
                return banIp(source, serverplayer.getIpAddress(), reason);
            } else {
                throw ERROR_INVALID_IP.create();
            }
        }
    }

    private static int banIp(CommandSourceStack source, String ip, @Nullable Component reason) throws CommandSyntaxException {
        IpBanList ipbanlist = source.getServer().getPlayerList().getIpBans();
        if (ipbanlist.isBanned(ip)) {
            throw ERROR_ALREADY_BANNED.create();
        } else {
            List<ServerPlayer> list = source.getServer().getPlayerList().getPlayersWithAddress(ip);
            IpBanListEntry ipbanlistentry = new IpBanListEntry(ip, null, source.getTextName(), null, reason == null ? null : reason.getString());
            ipbanlist.add(ipbanlistentry);
            source.sendSuccess(() -> Component.translatable("commands.banip.success", ip, ipbanlistentry.getReason()), true);
            if (!list.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.banip.info", list.size(), EntitySelector.joinNames(list)), true);
            }

            for (ServerPlayer serverplayer : list) {
                serverplayer.connection.disconnect(Component.translatable("multiplayer.disconnect.ip_banned"));
            }

            return list.size();
        }
    }
}
