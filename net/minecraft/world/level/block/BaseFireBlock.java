package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {
    private static final int SECONDS_ON_FIRE = 8;
    private final float fireDamage;
    protected static final float AABB_OFFSET = 1.0F;
    protected static final VoxelShape DOWN_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);

    public BaseFireBlock(BlockBehaviour.Properties properties, float fireDamage) {
        super(properties);
        this.fireDamage = fireDamage;
    }

    @Override
    protected abstract MapCodec<? extends BaseFireBlock> codec();

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getState(context.getLevel(), context.getClickedPos());
    }

    public static BlockState getState(BlockGetter reader, BlockPos pos) {
        BlockPos blockpos = pos.below();
        BlockState blockstate = reader.getBlockState(blockpos);
        return SoulFireBlock.canSurviveOnBlock(blockstate)
            ? Blocks.SOUL_FIRE.defaultBlockState()
            : ((FireBlock)Blocks.FIRE).getStateForPlacement(reader, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DOWN_AABB;
    }

    /**
     * Called periodically clientside on blocks near the player to show effects (like furnace fire particles).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) == 0) {
            level.playLocalSound(
                (double)pos.getX() + 0.5,
                (double)pos.getY() + 0.5,
                (double)pos.getZ() + 0.5,
                SoundEvents.FIRE_AMBIENT,
                SoundSource.BLOCKS,
                1.0F + random.nextFloat(),
                random.nextFloat() * 0.7F + 0.3F,
                false
            );
        }

        BlockPos blockpos = pos.below();
        BlockState blockstate = level.getBlockState(blockpos);
        if (!this.canBurn(blockstate) && !blockstate.isFaceSturdy(level, blockpos, Direction.UP)) {
            if (this.canBurn(level.getBlockState(pos.west()))) {
                for (int j = 0; j < 2; j++) {
                    double d3 = (double)pos.getX() + random.nextDouble() * 0.1F;
                    double d8 = (double)pos.getY() + random.nextDouble();
                    double d13 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d3, d8, d13, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.east()))) {
                for (int k = 0; k < 2; k++) {
                    double d4 = (double)(pos.getX() + 1) - random.nextDouble() * 0.1F;
                    double d9 = (double)pos.getY() + random.nextDouble();
                    double d14 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d4, d9, d14, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.north()))) {
                for (int l = 0; l < 2; l++) {
                    double d5 = (double)pos.getX() + random.nextDouble();
                    double d10 = (double)pos.getY() + random.nextDouble();
                    double d15 = (double)pos.getZ() + random.nextDouble() * 0.1F;
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d5, d10, d15, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.south()))) {
                for (int i1 = 0; i1 < 2; i1++) {
                    double d6 = (double)pos.getX() + random.nextDouble();
                    double d11 = (double)pos.getY() + random.nextDouble();
                    double d16 = (double)(pos.getZ() + 1) - random.nextDouble() * 0.1F;
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d6, d11, d16, 0.0, 0.0, 0.0);
                }
            }

            if (this.canBurn(level.getBlockState(pos.above()))) {
                for (int j1 = 0; j1 < 2; j1++) {
                    double d7 = (double)pos.getX() + random.nextDouble();
                    double d12 = (double)(pos.getY() + 1) - random.nextDouble() * 0.1F;
                    double d17 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(ParticleTypes.LARGE_SMOKE, d7, d12, d17, 0.0, 0.0, 0.0);
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                double d0 = (double)pos.getX() + random.nextDouble();
                double d1 = (double)pos.getY() + random.nextDouble() * 0.5 + 0.5;
                double d2 = (double)pos.getZ() + random.nextDouble();
                level.addParticle(ParticleTypes.LARGE_SMOKE, d0, d1, d2, 0.0, 0.0, 0.0);
            }
        }
    }

    protected abstract boolean canBurn(BlockState state);

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!entity.fireImmune()) {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            if (entity.getRemainingFireTicks() == 0) {
                entity.igniteForSeconds(8.0F);
            }
        }

        entity.hurt(level.damageSources().inFire(), this.fireDamage);
        super.entityInside(state, level, pos, entity);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            if (inPortalDimension(level)) {
                Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
                optional = net.neoforged.neoforge.event.EventHooks.onTrySpawnPortal(level, pos, optional);
                if (optional.isPresent()) {
                    optional.get().createPortalBlocks();
                    return;
                }
            }

            if (!state.canSurvive(level, pos)) {
                level.removeBlock(pos, false);
            }
        }
    }

    private static boolean inPortalDimension(Level level) {
        return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER;
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            level.levelEvent(null, 1009, pos, 0);
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    public static boolean canBePlacedAt(Level level, BlockPos pos, Direction direction) {
        BlockState blockstate = level.getBlockState(pos);
        return !blockstate.isAir() ? false : getState(level, pos).canSurvive(level, pos) || isPortal(level, pos, direction);
    }

    private static boolean isPortal(Level level, BlockPos pos, Direction p_direction) {
        if (!inPortalDimension(level)) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();
            boolean flag = false;

            for (Direction direction : Direction.values()) {
                if (level.getBlockState(blockpos$mutableblockpos.set(pos).move(direction)).isPortalFrame(level, blockpos$mutableblockpos)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                Direction.Axis direction$axis = p_direction.getAxis().isHorizontal()
                    ? p_direction.getCounterClockWise().getAxis()
                    : Direction.Plane.HORIZONTAL.getRandomAxis(level.random);
                return PortalShape.findEmptyPortalShape(level, pos, direction$axis).isPresent();
            }
        }
    }
}
