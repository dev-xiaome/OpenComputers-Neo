package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;

public class DifficultyCommand {
    private static final DynamicCommandExceptionType ERROR_ALREADY_DIFFICULT = new DynamicCommandExceptionType(
        p_304204_ -> Component.translatableEscape("commands.difficulty.failure", p_304204_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("difficulty");

        for (Difficulty difficulty : Difficulty.values()) {
            literalargumentbuilder.then(Commands.literal(difficulty.getKey()).executes(p_136937_ -> setDifficulty(p_136937_.getSource(), difficulty)));
        }

        dispatcher.register(literalargumentbuilder.requires(p_136943_ -> p_136943_.hasPermission(2)).executes(p_340656_ -> {
            Difficulty difficulty1 = p_340656_.getSource().getLevel().getDifficulty();
            p_340656_.getSource().sendSuccess(() -> Component.translatable("commands.difficulty.query", difficulty1.getDisplayName()), false);
            return difficulty1.getId();
        }));
    }

    public static int setDifficulty(CommandSourceStack source, Difficulty difficulty) throws CommandSyntaxException {
        MinecraftServer minecraftserver = source.getServer();
        if (minecraftserver.getWorldData().getDifficulty() == difficulty) {
            throw ERROR_ALREADY_DIFFICULT.create(difficulty.getKey());
        } else {
            minecraftserver.setDifficulty(difficulty, true);
            source.sendSuccess(() -> Component.translatable("commands.difficulty.success", difficulty.getDisplayName()), true);
            return 0;
        }
    }
}
