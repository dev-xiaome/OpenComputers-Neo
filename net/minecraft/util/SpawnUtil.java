package net.minecraft.util;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnUtil {
    public static <T extends Mob> Optional<T> trySpawnMob(
        EntityType<T> entityType,
        MobSpawnType spawnType,
        ServerLevel level,
        BlockPos pos,
        int attempts,
        int spread,
        int yOffset,
        SpawnUtil.Strategy strategy
    ) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        for (int i = 0; i < attempts; i++) {
            int j = Mth.randomBetweenInclusive(level.random, -spread, spread);
            int k = Mth.randomBetweenInclusive(level.random, -spread, spread);
            blockpos$mutableblockpos.setWithOffset(pos, j, yOffset, k);
            if (level.getWorldBorder().isWithinBounds(blockpos$mutableblockpos)
                && moveToPossibleSpawnPosition(level, yOffset, blockpos$mutableblockpos, strategy)) {
                T t = (T)entityType.create(level, null, blockpos$mutableblockpos, spawnType, false, false);
                if (t != null) {
                    if (net.neoforged.neoforge.event.EventHooks.checkSpawnPosition(t, level, spawnType)) {
                        level.addFreshEntityWithPassengers(t);
                        return Optional.of(t);
                    }

                    t.discard();
                }
            }
        }

        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(ServerLevel level, int yOffset, BlockPos.MutableBlockPos pos, SpawnUtil.Strategy strategy) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos().set(pos);
        BlockState blockstate = level.getBlockState(blockpos$mutableblockpos);

        for (int i = yOffset; i >= -yOffset; i--) {
            pos.move(Direction.DOWN);
            blockpos$mutableblockpos.setWithOffset(pos, Direction.UP);
            BlockState blockstate1 = level.getBlockState(pos);
            if (strategy.canSpawnOn(level, pos, blockstate1, blockpos$mutableblockpos, blockstate)) {
                pos.move(Direction.UP);
                return true;
            }

            blockstate = blockstate1;
        }

        return false;
    }

    public interface Strategy {
        @Deprecated
        SpawnUtil.Strategy LEGACY_IRON_GOLEM = (p_289751_, p_289752_, p_289753_, p_289754_, p_289755_) -> !p_289753_.is(Blocks.COBWEB)
                    && !p_289753_.is(Blocks.CACTUS)
                    && !p_289753_.is(Blocks.GLASS_PANE)
                    && !(p_289753_.getBlock() instanceof StainedGlassPaneBlock)
                    && !(p_289753_.getBlock() instanceof StainedGlassBlock)
                    && !(p_289753_.getBlock() instanceof LeavesBlock)
                    && !p_289753_.is(Blocks.CONDUIT)
                    && !p_289753_.is(Blocks.ICE)
                    && !p_289753_.is(Blocks.TNT)
                    && !p_289753_.is(Blocks.GLOWSTONE)
                    && !p_289753_.is(Blocks.BEACON)
                    && !p_289753_.is(Blocks.SEA_LANTERN)
                    && !p_289753_.is(Blocks.FROSTED_ICE)
                    && !p_289753_.is(Blocks.TINTED_GLASS)
                    && !p_289753_.is(Blocks.GLASS)
                ? (p_289755_.isAir() || p_289755_.liquid()) && (p_289753_.isSolid() || p_289753_.is(Blocks.POWDER_SNOW))
                : false;
        SpawnUtil.Strategy ON_TOP_OF_COLLIDER = (p_216416_, p_216417_, p_216418_, p_216419_, p_216420_) -> p_216420_.getCollisionShape(p_216416_, p_216419_)
                    .isEmpty()
                && Block.isFaceFull(p_216418_.getCollisionShape(p_216416_, p_216417_), Direction.UP);

        boolean canSpawnOn(ServerLevel level, BlockPos targetPos, BlockState targetState, BlockPos attemptedPos, BlockState attemptedState);
    }
}
