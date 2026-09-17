package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class SetPlayerIdleTimeoutCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("setidletimeout")
                .requires(p_138639_ -> p_138639_.hasPermission(3))
                .then(
                    Commands.argument("minutes", IntegerArgumentType.integer(0))
                        .executes(p_138637_ -> setIdleTimeout(p_138637_.getSource(), IntegerArgumentType.getInteger(p_138637_, "minutes")))
                )
        );
    }

    private static int setIdleTimeout(CommandSourceStack source, int idleTimeout) {
        source.getServer().setPlayerIdleTimeout(idleTimeout);
        source.sendSuccess(() -> Component.translatable("commands.setidletimeout.success", idleTimeout), true);
        return idleTimeout;
    }
}
