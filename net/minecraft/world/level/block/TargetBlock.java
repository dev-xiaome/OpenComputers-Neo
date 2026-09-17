package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TargetBlock extends Block {
    public static final MapCodec<TargetBlock> CODEC = simpleCodec(TargetBlock::new);
    private static final IntegerProperty OUTPUT_POWER = BlockStateProperties.POWER;
    private static final int ACTIVATION_TICKS_ARROWS = 20;
    private static final int ACTIVATION_TICKS_OTHER = 8;

    @Override
    public MapCodec<TargetBlock> codec() {
        return CODEC;
    }

    public TargetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(OUTPUT_POWER, Integer.valueOf(0)));
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        int i = updateRedstoneOutput(level, state, hit, projectile);
        if (projectile.getOwner() instanceof ServerPlayer serverplayer) {
            serverplayer.awardStat(Stats.TARGET_HIT);
            CriteriaTriggers.TARGET_BLOCK_HIT.trigger(serverplayer, projectile, hit.getLocation(), i);
        }
    }

    private static int updateRedstoneOutput(LevelAccessor level, BlockState state, BlockHitResult hit, Entity projectile) {
        int i = getRedstoneStrength(hit, hit.getLocation());
        int j = projectile instanceof AbstractArrow ? 20 : 8;
        if (!level.getBlockTicks().hasScheduledTick(hit.getBlockPos(), state.getBlock())) {
            setOutputPower(level, state, i, hit.getBlockPos(), j);
        }

        return i;
    }

    private static int getRedstoneStrength(BlockHitResult hit, Vec3 hitLocation) {
        Direction direction = hit.getDirection();
        double d0 = Math.abs(Mth.frac(hitLocation.x) - 0.5);
        double d1 = Math.abs(Mth.frac(hitLocation.y) - 0.5);
        double d2 = Math.abs(Mth.frac(hitLocation.z) - 0.5);
        Direction.Axis direction$axis = direction.getAxis();
        double d3;
        if (direction$axis == Direction.Axis.Y) {
            d3 = Math.max(d0, d2);
        } else if (direction$axis == Direction.Axis.Z) {
            d3 = Math.max(d0, d1);
        } else {
            d3 = Math.max(d1, d2);
        }

        return Math.max(1, Mth.ceil(15.0 * Mth.clamp((0.5 - d3) / 0.5, 0.0, 1.0)));
    }

    private static void setOutputPower(LevelAccessor level, BlockState state, int power, BlockPos pos, int waitTime) {
        level.setBlock(pos, state.setValue(OUTPUT_POWER, Integer.valueOf(power)), 3);
        level.scheduleTick(pos, state.getBlock(), waitTime);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OUTPUT_POWER) != 0) {
            level.setBlock(pos, state.setValue(OUTPUT_POWER, Integer.valueOf(0)), 3);
        }
    }

    /**
     * Returns the signal this block emits in the given direction.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     *
     */
    @Override
    protected int getSignal(BlockState blockState, BlockGetter blockAccess, BlockPos pos, Direction side) {
        return blockState.getValue(OUTPUT_POWER);
    }

    /**
     * Returns whether this block is capable of emitting redstone signals.
     *
     */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT_POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            if (state.getValue(OUTPUT_POWER) > 0 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.setBlock(pos, state.setValue(OUTPUT_POWER, Integer.valueOf(0)), 18);
            }
        }
    }
}
