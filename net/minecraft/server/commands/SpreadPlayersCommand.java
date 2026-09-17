package net.minecraft.server.commands;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.scores.Team;

public class SpreadPlayersCommand {
    private static final int MAX_ITERATION_COUNT = 10000;
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS = new Dynamic4CommandExceptionType(
        (p_304299_, p_304300_, p_304301_, p_304302_) -> Component.translatableEscape(
                "commands.spreadplayers.failed.teams", p_304299_, p_304300_, p_304301_, p_304302_
            )
    );
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES = new Dynamic4CommandExceptionType(
        (p_304305_, p_304306_, p_304307_, p_304308_) -> Component.translatableEscape(
                "commands.spreadplayers.failed.entities", p_304305_, p_304306_, p_304307_, p_304308_
            )
    );
    private static final Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT = new Dynamic2CommandExceptionType(
        (p_304303_, p_304304_) -> Component.translatableEscape("commands.spreadplayers.failed.invalid.height", p_304303_, p_304304_)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("spreadplayers")
                .requires(p_201852_ -> p_201852_.hasPermission(2))
                .then(
                    Commands.argument("center", Vec2Argument.vec2())
                        .then(
                            Commands.argument("spreadDistance", FloatArgumentType.floatArg(0.0F))
                                .then(
                                    Commands.argument("maxRange", FloatArgumentType.floatArg(1.0F))
                                        .then(
                                            Commands.argument("respectTeams", BoolArgumentType.bool())
                                                .then(
                                                    Commands.argument("targets", EntityArgument.entities())
                                                        .executes(
                                                            p_340666_ -> spreadPlayers(
                                                                    p_340666_.getSource(),
                                                                    Vec2Argument.getVec2(p_340666_, "center"),
                                                                    FloatArgumentType.getFloat(p_340666_, "spreadDistance"),
                                                                    FloatArgumentType.getFloat(p_340666_, "maxRange"),
                                                                    p_340666_.getSource().getLevel().getMaxBuildHeight(),
                                                                    BoolArgumentType.getBool(p_340666_, "respectTeams"),
                                                                    EntityArgument.getEntities(p_340666_, "targets")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("under")
                                                .then(
                                                    Commands.argument("maxHeight", IntegerArgumentType.integer())
                                                        .then(
                                                            Commands.argument("respectTeams", BoolArgumentType.bool())
                                                                .then(
                                                                    Commands.argument("targets", EntityArgument.entities())
                                                                        .executes(
                                                                            p_201850_ -> spreadPlayers(
                                                                                    p_201850_.getSource(),
                                                                                    Vec2Argument.getVec2(p_201850_, "center"),
                                                                                    FloatArgumentType.getFloat(p_201850_, "spreadDistance"),
                                                                                    FloatArgumentType.getFloat(p_201850_, "maxRange"),
                                                                                    IntegerArgumentType.getInteger(p_201850_, "maxHeight"),
                                                                                    BoolArgumentType.getBool(p_201850_, "respectTeams"),
                                                                                    EntityArgument.getEntities(p_201850_, "targets")
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int spreadPlayers(
        CommandSourceStack source,
        Vec2 center,
        float spreadDistance,
        float maxRange,
        int maxHeight,
        boolean respectTeams,
        Collection<? extends Entity> targets
    ) throws CommandSyntaxException {
        ServerLevel serverlevel = source.getLevel();
        int i = serverlevel.getMinBuildHeight();
        if (maxHeight < i) {
            throw ERROR_INVALID_MAX_HEIGHT.create(maxHeight, i);
        } else {
            RandomSource randomsource = RandomSource.create();
            double d0 = (double)(center.x - maxRange);
            double d1 = (double)(center.y - maxRange);
            double d2 = (double)(center.x + maxRange);
            double d3 = (double)(center.y + maxRange);
            SpreadPlayersCommand.Position[] aspreadplayerscommand$position = createInitialPositions(
                randomsource, respectTeams ? getNumberOfTeams(targets) : targets.size(), d0, d1, d2, d3
            );
            spreadPositions(center, (double)spreadDistance, serverlevel, randomsource, d0, d1, d2, d3, maxHeight, aspreadplayerscommand$position, respectTeams);
            double d4 = setPlayerPositions(targets, serverlevel, aspreadplayerscommand$position, maxHeight, respectTeams);
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.spreadplayers.success." + (respectTeams ? "teams" : "entities"),
                        aspreadplayerscommand$position.length,
                        center.x,
                        center.y,
                        String.format(Locale.ROOT, "%.2f", d4)
                    ),
                true
            );
            return aspreadplayerscommand$position.length;
        }
    }

    /**
     * Gets the number of unique teams for the given list of entities.
     */
    private static int getNumberOfTeams(Collection<? extends Entity> entities) {
        Set<Team> set = Sets.newHashSet();

        for (Entity entity : entities) {
            if (entity instanceof Player) {
                set.add(entity.getTeam());
            } else {
                set.add(null);
            }
        }

        return set.size();
    }

    private static void spreadPositions(
        Vec2 center,
        double spreadDistance,
        ServerLevel level,
        RandomSource random,
        double minX,
        double minZ,
        double maxX,
        double maxZ,
        int maxHeight,
        SpreadPlayersCommand.Position[] positions,
        boolean respectTeams
    ) throws CommandSyntaxException {
        boolean flag = true;
        double d0 = Float.MAX_VALUE;

        int i;
        for (i = 0; i < 10000 && flag; i++) {
            flag = false;
            d0 = Float.MAX_VALUE;

            for (int j = 0; j < positions.length; j++) {
                SpreadPlayersCommand.Position spreadplayerscommand$position = positions[j];
                int k = 0;
                SpreadPlayersCommand.Position spreadplayerscommand$position1 = new SpreadPlayersCommand.Position();

                for (int l = 0; l < positions.length; l++) {
                    if (j != l) {
                        SpreadPlayersCommand.Position spreadplayerscommand$position2 = positions[l];
                        double d1 = spreadplayerscommand$position.dist(spreadplayerscommand$position2);
                        d0 = Math.min(d1, d0);
                        if (d1 < spreadDistance) {
                            k++;
                            spreadplayerscommand$position1.x = spreadplayerscommand$position1.x
                                + (spreadplayerscommand$position2.x - spreadplayerscommand$position.x);
                            spreadplayerscommand$position1.z = spreadplayerscommand$position1.z
                                + (spreadplayerscommand$position2.z - spreadplayerscommand$position.z);
                        }
                    }
                }

                if (k > 0) {
                    spreadplayerscommand$position1.x /= (double)k;
                    spreadplayerscommand$position1.z /= (double)k;
                    double d2 = spreadplayerscommand$position1.getLength();
                    if (d2 > 0.0) {
                        spreadplayerscommand$position1.normalize();
                        spreadplayerscommand$position.moveAway(spreadplayerscommand$position1);
                    } else {
                        spreadplayerscommand$position.randomize(random, minX, minZ, maxX, maxZ);
                    }

                    flag = true;
                }

                if (spreadplayerscommand$position.clamp(minX, minZ, maxX, maxZ)) {
                    flag = true;
                }
            }

            if (!flag) {
                for (SpreadPlayersCommand.Position spreadplayerscommand$position3 : positions) {
                    if (!spreadplayerscommand$position3.isSafe(level, maxHeight)) {
                        spreadplayerscommand$position3.randomize(random, minX, minZ, maxX, maxZ);
                        flag = true;
                    }
                }
            }
        }

        if (d0 == Float.MAX_VALUE) {
            d0 = 0.0;
        }

        if (i >= 10000) {
            if (respectTeams) {
                throw ERROR_FAILED_TO_SPREAD_TEAMS.create(positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d0));
            } else {
                throw ERROR_FAILED_TO_SPREAD_ENTITIES.create(positions.length, center.x, center.y, String.format(Locale.ROOT, "%.2f", d0));
            }
        }
    }

    private static double setPlayerPositions(
        Collection<? extends Entity> targets, ServerLevel level, SpreadPlayersCommand.Position[] positions, int maxHeight, boolean respectTeams
    ) {
        double d0 = 0.0;
        int i = 0;
        Map<Team, SpreadPlayersCommand.Position> map = Maps.newHashMap();

        for (Entity entity : targets) {
            SpreadPlayersCommand.Position spreadplayerscommand$position;
            if (respectTeams) {
                Team team = entity instanceof Player ? entity.getTeam() : null;
                if (!map.containsKey(team)) {
                    map.put(team, positions[i++]);
                }

                spreadplayerscommand$position = map.get(team);
            } else {
                spreadplayerscommand$position = positions[i++];
            }

            net.neoforged.neoforge.event.entity.EntityTeleportEvent.SpreadPlayersCommand event = net.neoforged.neoforge.event.EventHooks.onEntityTeleportSpreadPlayersCommand(entity,
                      (double)Mth.floor(spreadplayerscommand$position.x) + 0.5,
                      spreadplayerscommand$position.getSpawnY(level, maxHeight),
                      (double)Mth.floor(spreadplayerscommand$position.z) + 0.5
            );
            if (!event.isCanceled()) {
                entity.teleportTo(
                          level,
                          event.getTargetX(),
                          event.getTargetY(),
                          event.getTargetZ(),
                          Set.of(),
                          entity.getYRot(),
                          entity.getXRot()
                );
            }
            double d2 = Double.MAX_VALUE;

            for (SpreadPlayersCommand.Position spreadplayerscommand$position1 : positions) {
                if (spreadplayerscommand$position != spreadplayerscommand$position1) {
                    double d1 = spreadplayerscommand$position.dist(spreadplayerscommand$position1);
                    d2 = Math.min(d1, d2);
                }
            }

            d0 += d2;
        }

        return targets.size() < 2 ? 0.0 : d0 / (double)targets.size();
    }

    private static SpreadPlayersCommand.Position[] createInitialPositions(
        RandomSource random, int count, double minX, double minZ, double maxX, double maxZ
    ) {
        SpreadPlayersCommand.Position[] aspreadplayerscommand$position = new SpreadPlayersCommand.Position[count];

        for (int i = 0; i < aspreadplayerscommand$position.length; i++) {
            SpreadPlayersCommand.Position spreadplayerscommand$position = new SpreadPlayersCommand.Position();
            spreadplayerscommand$position.randomize(random, minX, minZ, maxX, maxZ);
            aspreadplayerscommand$position[i] = spreadplayerscommand$position;
        }

        return aspreadplayerscommand$position;
    }

    static class Position {
        double x;
        double z;

        double dist(SpreadPlayersCommand.Position other) {
            double d0 = this.x - other.x;
            double d1 = this.z - other.z;
            return Math.sqrt(d0 * d0 + d1 * d1);
        }

        void normalize() {
            double d0 = this.getLength();
            this.x /= d0;
            this.z /= d0;
        }

        double getLength() {
            return Math.sqrt(this.x * this.x + this.z * this.z);
        }

        public void moveAway(SpreadPlayersCommand.Position other) {
            this.x = this.x - other.x;
            this.z = this.z - other.z;
        }

        public boolean clamp(double minX, double minZ, double maxX, double maxZ) {
            boolean flag = false;
            if (this.x < minX) {
                this.x = minX;
                flag = true;
            } else if (this.x > maxX) {
                this.x = maxX;
                flag = true;
            }

            if (this.z < minZ) {
                this.z = minZ;
                flag = true;
            } else if (this.z > maxZ) {
                this.z = maxZ;
                flag = true;
            }

            return flag;
        }

        public int getSpawnY(BlockGetter level, int y) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos(this.x, (double)(y + 1), this.z);
            boolean flag = level.getBlockState(blockpos$mutableblockpos).isAir();
            blockpos$mutableblockpos.move(Direction.DOWN);
            boolean flag1 = level.getBlockState(blockpos$mutableblockpos).isAir();

            while (blockpos$mutableblockpos.getY() > level.getMinBuildHeight()) {
                blockpos$mutableblockpos.move(Direction.DOWN);
                boolean flag2 = level.getBlockState(blockpos$mutableblockpos).isAir();
                if (!flag2 && flag1 && flag) {
                    return blockpos$mutableblockpos.getY() + 1;
                }

                flag = flag1;
                flag1 = flag2;
            }

            return y + 1;
        }

        public boolean isSafe(BlockGetter level, int y) {
            BlockPos blockpos = BlockPos.containing(this.x, (double)(this.getSpawnY(level, y) - 1), this.z);
            BlockState blockstate = level.getBlockState(blockpos);
            return blockpos.getY() < y && !blockstate.liquid() && !blockstate.is(BlockTags.FIRE);
        }

        public void randomize(RandomSource random, double minX, double minZ, double maxX, double maxZ) {
            this.x = Mth.nextDouble(random, minX, maxX);
            this.z = Mth.nextDouble(random, minZ, maxZ);
        }
    }
}
