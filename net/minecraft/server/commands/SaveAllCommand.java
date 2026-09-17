package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public class SaveAllCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.save.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("save-all")
                .requires(p_138276_ -> p_138276_.hasPermission(4))
                .executes(p_138281_ -> saveAll(p_138281_.getSource(), false))
                .then(Commands.literal("flush").executes(p_138274_ -> saveAll(p_138274_.getSource(), true)))
        );
    }

    private static int saveAll(CommandSourceStack source, boolean flush) throws CommandSyntaxException {
        source.sendSuccess(() -> Component.translatable("commands.save.saving"), false);
        MinecraftServer minecraftserver = source.getServer();
        boolean flag = minecraftserver.saveEverything(true, flush, true);
        if (!flag) {
            throw ERROR_FAILED.create();
        } else {
            source.sendSuccess(() -> Component.translatable("commands.save.success"), true);
            return 1;
        }
    }
}
