package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class KillCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("kill")
                .requires(p_137812_ -> p_137812_.hasPermission(2))
                .executes(p_137817_ -> kill(p_137817_.getSource(), ImmutableList.of(p_137817_.getSource().getEntityOrException())))
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .executes(p_137810_ -> kill(p_137810_.getSource(), EntityArgument.getEntities(p_137810_, "targets")))
                )
        );
    }

    private static int kill(CommandSourceStack source, Collection<? extends Entity> targets) {
        for (Entity entity : targets) {
            entity.kill();
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.kill.success.single", targets.iterator().next().getDisplayName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.kill.success.multiple", targets.size()), true);
        }

        return targets.size();
    }
}
