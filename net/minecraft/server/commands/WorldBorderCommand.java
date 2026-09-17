package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec2;

public class WorldBorderCommand {
    private static final SimpleCommandExceptionType ERROR_SAME_CENTER = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.center.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.nochange")
    );
    private static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.small")
    );
    private static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.big", 5.999997E7F)
    );
    private static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.set.failed.far", 2.9999984E7)
    );
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_TIME = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.warning.time.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_WARNING_DISTANCE = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.warning.distance.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_BUFFER = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.damage.buffer.failed")
    );
    private static final SimpleCommandExceptionType ERROR_SAME_DAMAGE_AMOUNT = new SimpleCommandExceptionType(
        Component.translatable("commands.worldborder.damage.amount.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("worldborder")
                .requires(p_139268_ -> p_139268_.hasPermission(2))
                .then(
                    Commands.literal("add")
                        .then(
                            Commands.argument("distance", DoubleArgumentType.doubleArg(-5.999997E7F, 5.999997E7F))
                                .executes(
                                    p_325624_ -> setSize(
                                            p_325624_.getSource(),
                                            p_325624_.getSource().getLevel().getWorldBorder().getSize() + DoubleArgumentType.getDouble(p_325624_, "distance"),
                                            0L
                                        )
                                )
                                .then(
                                    Commands.argument("time", IntegerArgumentType.integer(0))
                                        .executes(
                                            p_325625_ -> setSize(
                                                    p_325625_.getSource(),
                                                    p_325625_.getSource().getLevel().getWorldBorder().getSize()
                                                        + DoubleArgumentType.getDouble(p_325625_, "distance"),
                                                    p_325625_.getSource().getLevel().getWorldBorder().getLerpRemainingTime()
                                                        + (long)IntegerArgumentType.getInteger(p_325625_, "time") * 1000L
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("distance", DoubleArgumentType.doubleArg(-5.999997E7F, 5.999997E7F))
                                .executes(p_139286_ -> setSize(p_139286_.getSource(), DoubleArgumentType.getDouble(p_139286_, "distance"), 0L))
                                .then(
                                    Commands.argument("time", IntegerArgumentType.integer(0))
                                        .executes(
                                            p_139284_ -> setSize(
                                                    p_139284_.getSource(),
                                                    DoubleArgumentType.getDouble(p_139284_, "distance"),
                                                    (long)IntegerArgumentType.getInteger(p_139284_, "time") * 1000L
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("center")
                        .then(
                            Commands.argument("pos", Vec2Argument.vec2())
                                .executes(p_139282_ -> setCenter(p_139282_.getSource(), Vec2Argument.getVec2(p_139282_, "pos")))
                        )
                )
                .then(
                    Commands.literal("damage")
                        .then(
                            Commands.literal("amount")
                                .then(
                                    Commands.argument("damagePerBlock", FloatArgumentType.floatArg(0.0F))
                                        .executes(p_139280_ -> setDamageAmount(p_139280_.getSource(), FloatArgumentType.getFloat(p_139280_, "damagePerBlock")))
                                )
                        )
                        .then(
                            Commands.literal("buffer")
                                .then(
                                    Commands.argument("distance", FloatArgumentType.floatArg(0.0F))
                                        .executes(p_139278_ -> setDamageBuffer(p_139278_.getSource(), FloatArgumentType.getFloat(p_139278_, "distance")))
                                )
                        )
                )
                .then(Commands.literal("get").executes(p_139276_ -> getSize(p_139276_.getSource())))
                .then(
                    Commands.literal("warning")
                        .then(
                            Commands.literal("distance")
                                .then(
                                    Commands.argument("distance", IntegerArgumentType.integer(0))
                                        .executes(p_139266_ -> setWarningDistance(p_139266_.getSource(), IntegerArgumentType.getInteger(p_139266_, "distance")))
                                )
                        )
                        .then(
                            Commands.literal("time")
                                .then(
                                    Commands.argument("time", IntegerArgumentType.integer(0))
                                        .executes(p_139249_ -> setWarningTime(p_139249_.getSource(), IntegerArgumentType.getInteger(p_139249_, "time")))
                                )
                        )
                )
        );
    }

    private static int setDamageBuffer(CommandSourceStack source, float distance) throws CommandSyntaxException {
        WorldBorder worldborder = source.getServer().overworld().getWorldBorder();
        if (worldborder.getDamageSafeZone() == (double)distance) {
            throw ERROR_SAME_DAMAGE_BUFFER.create();
        } else {
            worldborder.setDamageSafeZone((double)distance);
            source.sendSuccess(
                () -> Component.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance)), true
            );
            return (int)distance;
        }
    }

    private static int setDamageAmount(CommandSourceStack source, float damagePerBlock) throws CommandSyntaxException {
        WorldBorder worldborder = source.getServer().overworld().getWorldBorder();
        if (worldborder.getDamagePerBlock() == (double)damagePerBlock) {
            throw ERROR_SAME_DAMAGE_AMOUNT.create();
        } else {
            worldborder.setDamagePerBlock((double)damagePerBlock);
            source.sendSuccess(
                () -> Component.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", damagePerBlock)), true
            );
            return (int)damagePerBlock;
        }
    }

    private static int setWarningTime(CommandSourceStack source, int time) throws CommandSyntaxException {
        WorldBorder worldborder = source.getServer().overworld().getWorldBorder();
        if (worldborder.getWarningTime() == time) {
            throw ERROR_SAME_WARNING_TIME.create();
        } else {
            worldborder.setWarningTime(time);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.warning.time.success", time), true);
            return time;
        }
    }

    private static int setWarningDistance(CommandSourceStack source, int distance) throws CommandSyntaxException {
        WorldBorder worldborder = source.getServer().overworld().getWorldBorder();
        if (worldborder.getWarningBlocks() == distance) {
            throw ERROR_SAME_WARNING_DISTANCE.create();
        } else {
            worldborder.setWarningBlocks(distance);
            source.sendSuccess(() -> Component.translatable("commands.worldborder.warning.distance.success", distance), true);
            return distance;
        }
    }

    private static int getSize(CommandSourceStack source) {
        double d0 = source.getServer().overworld().getWorldBorder().getSize();
        source.sendSuccess(() -> Component.translatable("commands.worldborder.get", String.format(Locale.ROOT, "%.0f", d0)), false);
        return Mth.floor(d0 + 0.5);
    }

    private static int setCenter(CommandSourceStack source, Vec2 pos) throws CommandSyntaxException {
        WorldBorder worldborder = source.getServer().overworld().getWorldBorder();
        if (worldborder.getCenterX() == (double)pos.x && worldborder.getCenterZ() == (double)pos.y) {
            throw ERROR_SAME_CENTER.create();
        } else if (!((double)Math.abs(pos.x) > 2.9999984E7) && !((double)Math.abs(pos.y) > 2.9999984E7)) {
            worldborder.setCenter((double)pos.x, (double)pos.y);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.worldborder.center.success", String.format(Locale.ROOT, "%.2f", pos.x), String.format(Locale.ROOT, "%.2f", pos.y)
                    ),
                true
            );
            return 0;
        } else {
            throw ERROR_TOO_FAR_OUT.create();
        }
    }

    private static int setSize(CommandSourceStack source, double newSize, long time) throws CommandSyntaxException {
        WorldBorder worldborder = source.getServer().overworld().getWorldBorder();
        double d0 = worldborder.getSize();
        if (d0 == newSize) {
            throw ERROR_SAME_SIZE.create();
        } else if (newSize < 1.0) {
            throw ERROR_TOO_SMALL.create();
        } else if (newSize > 5.999997E7F) {
            throw ERROR_TOO_BIG.create();
        } else {
            if (time > 0L) {
                worldborder.lerpSizeBetween(d0, newSize, time);
                if (newSize > d0) {
                    source.sendSuccess(
                        () -> Component.translatable(
                                "commands.worldborder.set.grow", String.format(Locale.ROOT, "%.1f", newSize), Long.toString(time / 1000L)
                            ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable(
                                "commands.worldborder.set.shrink", String.format(Locale.ROOT, "%.1f", newSize), Long.toString(time / 1000L)
                            ),
                        true
                    );
                }
            } else {
                worldborder.setSize(newSize);
                source.sendSuccess(() -> Component.translatable("commands.worldborder.set.immediate", String.format(Locale.ROOT, "%.1f", newSize)), true);
            }

            return (int)(newSize - d0);
        }
    }
}
