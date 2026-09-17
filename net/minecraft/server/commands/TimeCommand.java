package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class TimeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("time")
                .requires(p_139076_ -> p_139076_.hasPermission(2))
                .then(
                    Commands.literal("set")
                        .then(Commands.literal("day").executes(p_139101_ -> setTime(p_139101_.getSource(), 1000)))
                        .then(Commands.literal("noon").executes(p_139099_ -> setTime(p_139099_.getSource(), 6000)))
                        .then(Commands.literal("night").executes(p_139097_ -> setTime(p_139097_.getSource(), 13000)))
                        .then(Commands.literal("midnight").executes(p_139095_ -> setTime(p_139095_.getSource(), 18000)))
                        .then(
                            Commands.argument("time", TimeArgument.time())
                                .executes(p_139093_ -> setTime(p_139093_.getSource(), IntegerArgumentType.getInteger(p_139093_, "time")))
                        )
                )
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("time", TimeArgument.time())
                                .executes(p_139091_ -> addTime(p_139091_.getSource(), IntegerArgumentType.getInteger(p_139091_, "time")))
                        )
                )
                .then(
                    Commands.literal("query")
                        .then(Commands.literal("daytime").executes(p_139086_ -> queryTime(p_139086_.getSource(), getDayTime(p_139086_.getSource().getLevel()))))
                        .then(
                            Commands.literal("gametime")
                                .executes(p_340667_ -> queryTime(p_340667_.getSource(), (int)(p_340667_.getSource().getLevel().getGameTime() % 2147483647L)))
                        )
                        .then(
                            Commands.literal("day")
                                .executes(
                                    p_340668_ -> queryTime(p_340668_.getSource(), (int)(p_340668_.getSource().getLevel().getDayTime() / 24000L % 2147483647L))
                                )
                        )
                )
        );
    }

    /**
     * Returns the day time (time wrapped within a day)
     */
    private static int getDayTime(ServerLevel level) {
        return (int)(level.getDayTime() % 24000L);
    }

    private static int queryTime(CommandSourceStack source, int time) {
        source.sendSuccess(() -> Component.translatable("commands.time.query", time), false);
        return time;
    }

    public static int setTime(CommandSourceStack source, int time) {
        for (ServerLevel serverlevel : source.getServer().getAllLevels()) {
            serverlevel.setDayTime((long)time);
        }

        source.sendSuccess(() -> Component.translatable("commands.time.set", time), true);
        return getDayTime(source.getLevel());
    }

    public static int addTime(CommandSourceStack source, int amount) {
        for (ServerLevel serverlevel : source.getServer().getAllLevels()) {
            serverlevel.setDayTime(serverlevel.getDayTime() + (long)amount);
        }

        int i = getDayTime(source.getLevel());
        source.sendSuccess(() -> Component.translatable("commands.time.set", i), true);
        return i;
    }
}
