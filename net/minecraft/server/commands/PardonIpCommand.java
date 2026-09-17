package net.minecraft.server.commands;

import com.google.common.net.InetAddresses;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.IpBanList;

public class PardonIpCommand {
    private static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("commands.pardonip.invalid"));
    private static final SimpleCommandExceptionType ERROR_NOT_BANNED = new SimpleCommandExceptionType(Component.translatable("commands.pardonip.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("pardon-ip")
                .requires(p_138116_ -> p_138116_.hasPermission(3))
                .then(
                    Commands.argument("target", StringArgumentType.word())
                        .suggests(
                            (p_138113_, p_138114_) -> SharedSuggestionProvider.suggest(
                                    p_138113_.getSource().getServer().getPlayerList().getIpBans().getUserList(), p_138114_
                                )
                        )
                        .executes(p_138111_ -> unban(p_138111_.getSource(), StringArgumentType.getString(p_138111_, "target")))
                )
        );
    }

    private static int unban(CommandSourceStack source, String ipAddress) throws CommandSyntaxException {
        if (!InetAddresses.isInetAddress(ipAddress)) {
            throw ERROR_INVALID.create();
        } else {
            IpBanList ipbanlist = source.getServer().getPlayerList().getIpBans();
            if (!ipbanlist.isBanned(ipAddress)) {
                throw ERROR_NOT_BANNED.create();
            } else {
                ipbanlist.remove(ipAddress);
                source.sendSuccess(() -> Component.translatable("commands.pardonip.success", ipAddress), true);
                return 1;
            }
        }
    }
}
