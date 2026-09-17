package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;

public class GameRuleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> literalargumentbuilder = Commands.literal("gamerule")
            .requires(p_137750_ -> p_137750_.hasPermission(2));
        GameRules.visitGameRuleTypes(
            new GameRules.GameRuleTypeVisitor() {
                @Override
                public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                    literalargumentbuilder.then(
                        Commands.literal(key.getId())
                            .executes(p_137771_ -> GameRuleCommand.queryRule(p_137771_.getSource(), key))
                            .then(type.createArgument("value").executes(p_137768_ -> GameRuleCommand.setRule(p_137768_, key)))
                    );
                }
            }
        );
        dispatcher.register(literalargumentbuilder);
    }

    static <T extends GameRules.Value<T>> int setRule(CommandContext<CommandSourceStack> source, GameRules.Key<T> gameRule) {
        CommandSourceStack commandsourcestack = source.getSource();
        T t = commandsourcestack.getServer().getGameRules().getRule(gameRule);
        t.setFromArgument(source, "value");
        commandsourcestack.sendSuccess(() -> Component.translatable("commands.gamerule.set", gameRule.getId(), t.toString()), true);
        return t.getCommandResult();
    }

    static <T extends GameRules.Value<T>> int queryRule(CommandSourceStack source, GameRules.Key<T> gameRule) {
        T t = source.getServer().getGameRules().getRule(gameRule);
        source.sendSuccess(() -> Component.translatable("commands.gamerule.query", gameRule.getId(), t.toString()), false);
        return t.getCommandResult();
    }
}
