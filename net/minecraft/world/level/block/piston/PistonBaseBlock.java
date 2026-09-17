package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PistonBaseBlock extends DirectionalBlock {
    public static final MapCodec<PistonBaseBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_308861_ -> p_308861_.group(Codec.BOOL.fieldOf("sticky").forGetter(p_304492_ -> p_304492_.isSticky), propertiesCodec())
                .apply(p_308861_, PistonBaseBlock::new)
    );
    public static final BooleanProperty EXTENDED = BlockStateProperties.EXTENDED;
    public static final int TRIGGER_EXTEND = 0;
    public static final int TRIGGER_CONTRACT = 1;
    public static final int TRIGGER_DROP = 2;
    public static final float PLATFORM_THICKNESS = 4.0F;
    protected static final VoxelShape EAST_AABB = Block.box(0.0, 0.0, 0.0, 12.0, 16.0, 16.0);
    protected static final VoxelShape WEST_AABB = Block.box(4.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 12.0);
    protected static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 4.0, 16.0, 16.0, 16.0);
    protected static final VoxelShape UP_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);
    protected static final VoxelShape DOWN_AABB = Block.box(0.0, 4.0, 0.0, 16.0, 16.0, 16.0);
    /**
     * Whether this is a sticky piston
     */
    private final boolean isSticky;

    @Override
    public MapCodec<PistonBaseBlock> codec() {
        return CODEC;
    }

    public PistonBaseBlock(boolean isSticky, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EXTENDED, Boolean.valueOf(false)));
        this.isSticky = isSticky;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(EXTENDED)) {
            switch ((Direction)state.getValue(FACING)) {
                case DOWN:
                    return DOWN_AABB;
                case UP:
                default:
                    return UP_AABB;
                case NORTH:
                    return NORTH_AABB;
                case SOUTH:
                    return SOUTH_AABB;
                case WEST:
                    return WEST_AABB;
                case EAST:
                    return EAST_AABB;
            }
        } else {
            return Shapes.block();
        }
    }

    /**
     * Called by BlockItem after this block has been placed.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            this.checkIfExtend(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            this.checkIfExtend(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) == null) {
                this.checkIfExtend(level, pos, state);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite()).setValue(EXTENDED, Boolean.valueOf(false));
    }

    private void checkIfExtend(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        boolean flag = this.getNeighborSignal(level, pos, direction);
        if (flag && !state.getValue(EXTENDED)) {
            if (new PistonStructureResolver(level, pos, direction, true).resolve()) {
                level.blockEvent(pos, this, 0, direction.get3DDataValue());
            }
        } else if (!flag && state.getValue(EXTENDED)) {
            BlockPos blockpos = pos.relative(direction, 2);
            BlockState blockstate = level.getBlockState(blockpos);
            int i = 1;
            if (blockstate.is(Blocks.MOVING_PISTON)
                && blockstate.getValue(FACING) == direction
                && level.getBlockEntity(blockpos) instanceof PistonMovingBlockEntity pistonmovingblockentity
                && pistonmovingblockentity.isExtending()
                && (
                    pistonmovingblockentity.getProgress(0.0F) < 0.5F
                        || level.getGameTime() == pistonmovingblockentity.getLastTicked()
                        || ((ServerLevel)level).isHandlingTick()
                )) {
                i = 2;
            }

            level.blockEvent(pos, this, i, direction.get3DDataValue());
        }
    }

    private boolean getNeighborSignal(SignalGetter signalGetter, BlockPos pos, Direction p_direction) {
        for (Direction direction : Direction.values()) {
            if (direction != p_direction && signalGetter.hasSignal(pos.relative(direction), direction)) {
                return true;
            }
        }

        if (signalGetter.hasSignal(pos, Direction.DOWN)) {
            return true;
        } else {
            BlockPos blockpos = pos.above();

            for (Direction direction1 : Direction.values()) {
                if (direction1 != Direction.DOWN && signalGetter.hasSignal(blockpos.relative(direction1), direction1)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Called on server when {@link net.minecraft.world.level.Level#blockEvent} is called. If server returns true, then also called on the client. On the Server, this may perform additional changes to the world, like pistons replacing the block with an extended base. On the client, the update may involve replacing block entities or effects such as sounds or particles
     */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        Direction direction = state.getValue(FACING);
        BlockState blockstate = state.setValue(EXTENDED, Boolean.valueOf(true));
        if (!level.isClientSide) {
            boolean flag = this.getNeighborSignal(level, pos, direction);
            if (flag && (id == 1 || id == 2)) {
                level.setBlock(pos, blockstate, 2);
                return false;
            }

            if (!flag && id == 0) {
                return false;
            }
        }

        if (id == 0) {
            if (net.neoforged.neoforge.event.EventHooks.onPistonMovePre(level, pos, direction, true)) return false;
            if (!this.moveBlocks(level, pos, direction, true)) {
                return false;
            }

            level.setBlock(pos, blockstate, 67);
            level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.25F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(blockstate));
        } else if (id == 1 || id == 2) {
            if (net.neoforged.neoforge.event.EventHooks.onPistonMovePre(level, pos, direction, false)) return false;
            BlockEntity blockentity = level.getBlockEntity(pos.relative(direction));
            if (blockentity instanceof PistonMovingBlockEntity) {
                ((PistonMovingBlockEntity)blockentity).finalTick();
            }

            BlockState blockstate1 = Blocks.MOVING_PISTON
                .defaultBlockState()
                .setValue(MovingPistonBlock.FACING, direction)
                .setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
            level.setBlock(pos, blockstate1, 20);
            level.setBlockEntity(
                MovingPistonBlock.newMovingBlockEntity(
                    pos, blockstate1, this.defaultBlockState().setValue(FACING, Direction.from3DDataValue(param & 7)), direction, false, true
                )
            );
            level.blockUpdated(pos, blockstate1.getBlock());
            blockstate1.updateNeighbourShapes(level, pos, 2);
            if (this.isSticky) {
                BlockPos blockpos = pos.offset(direction.getStepX() * 2, direction.getStepY() * 2, direction.getStepZ() * 2);
                BlockState blockstate2 = level.getBlockState(blockpos);
                boolean flag1 = false;
                if (blockstate2.is(Blocks.MOVING_PISTON)
                    && level.getBlockEntity(blockpos) instanceof PistonMovingBlockEntity pistonmovingblockentity
                    && pistonmovingblockentity.getDirection() == direction
                    && pistonmovingblockentity.isExtending()) {
                    pistonmovingblockentity.finalTick();
                    flag1 = true;
                }

                if (!flag1) {
                    if (id != 1
                        || blockstate2.isAir()
                        || !isPushable(blockstate2, level, blockpos, direction.getOpposite(), false, direction)
                        || blockstate2.getPistonPushReaction() != PushReaction.NORMAL
                            && !blockstate2.is(Blocks.PISTON)
                            && !blockstate2.is(Blocks.STICKY_PISTON)) {
                        level.removeBlock(pos.relative(direction), false);
                    } else {
                        this.moveBlocks(level, pos, direction, false);
                    }
                }
            } else {
                level.removeBlock(pos.relative(direction), false);
            }

            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.15F + 0.6F);
            level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(blockstate1));
        }

        net.neoforged.neoforge.event.EventHooks.onPistonMovePost(level, pos, direction, (id == 0));
        return true;
    }

    /**
     * Checks if the piston can push the given BlockState.
     */
    public static boolean isPushable(BlockState state, Level level, BlockPos pos, Direction movementDirection, boolean allowDestroy, Direction pistonFacing) {
        if (pos.getY() < level.getMinBuildHeight()
            || pos.getY() > level.getMaxBuildHeight() - 1
            || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        } else if (state.isAir()) {
            return true;
        } else if (state.is(Blocks.OBSIDIAN)
            || state.is(Blocks.CRYING_OBSIDIAN)
            || state.is(Blocks.RESPAWN_ANCHOR)
            || state.is(Blocks.REINFORCED_DEEPSLATE)) {
            return false;
        } else if (movementDirection == Direction.DOWN && pos.getY() == level.getMinBuildHeight()) {
            return false;
        } else if (movementDirection == Direction.UP && pos.getY() == level.getMaxBuildHeight() - 1) {
            return false;
        } else {
            if (!state.is(Blocks.PISTON) && !state.is(Blocks.STICKY_PISTON)) {
                if (state.getDestroySpeed(level, pos) == -1.0F) {
                    return false;
                }

                switch (state.getPistonPushReaction()) {
                    case BLOCK:
                        return false;
                    case DESTROY:
                        return allowDestroy;
                    case PUSH_ONLY:
                        return movementDirection == pistonFacing;
                }
            } else if (state.getValue(EXTENDED)) {
                return false;
            }

            return !state.hasBlockEntity();
        }
    }

    private boolean moveBlocks(Level level, BlockPos pos, Direction facing, boolean extending) {
        BlockPos blockpos = pos.relative(facing);
        if (!extending && level.getBlockState(blockpos).is(Blocks.PISTON_HEAD)) {
            level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 20);
        }

        PistonStructureResolver pistonstructureresolver = new PistonStructureResolver(level, pos, facing, extending);
        if (!pistonstructureresolver.resolve()) {
            return false;
        } else {
            Map<BlockPos, BlockState> map = Maps.newHashMap();
            List<BlockPos> list = pistonstructureresolver.getToPush();
            List<BlockState> list1 = Lists.newArrayList();

            for (BlockPos blockpos1 : list) {
                BlockState blockstate = level.getBlockState(blockpos1);
                list1.add(blockstate);
                map.put(blockpos1, blockstate);
            }

            List<BlockPos> list2 = pistonstructureresolver.getToDestroy();
            BlockState[] ablockstate = new BlockState[list.size() + list2.size()];
            Direction direction = extending ? facing : facing.getOpposite();
            int i = 0;

            for (int j = list2.size() - 1; j >= 0; j--) {
                BlockPos blockpos2 = list2.get(j);
                BlockState blockstate1 = level.getBlockState(blockpos2);
                BlockEntity blockentity = blockstate1.hasBlockEntity() ? level.getBlockEntity(blockpos2) : null;
                dropResources(blockstate1, level, blockpos2, blockentity);
                blockstate1.onDestroyedByPushReaction(level, blockpos2, direction, level.getFluidState(blockpos2));
                if (!blockstate1.is(BlockTags.FIRE)) {
                    level.addDestroyBlockEffect(blockpos2, blockstate1);
                }

                ablockstate[i++] = blockstate1;
            }

            for (int k = list.size() - 1; k >= 0; k--) {
                BlockPos blockpos3 = list.get(k);
                BlockState blockstate5 = level.getBlockState(blockpos3);
                blockpos3 = blockpos3.relative(direction);
                map.remove(blockpos3);
                BlockState blockstate8 = Blocks.MOVING_PISTON.defaultBlockState().setValue(FACING, facing);
                level.setBlock(blockpos3, blockstate8, 68);
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockpos3, blockstate8, list1.get(k), facing, extending, false));
                ablockstate[i++] = blockstate5;
            }

            if (extending) {
                PistonType pistontype = this.isSticky ? PistonType.STICKY : PistonType.DEFAULT;
                BlockState blockstate4 = Blocks.PISTON_HEAD
                    .defaultBlockState()
                    .setValue(PistonHeadBlock.FACING, facing)
                    .setValue(PistonHeadBlock.TYPE, pistontype);
                BlockState blockstate6 = Blocks.MOVING_PISTON
                    .defaultBlockState()
                    .setValue(MovingPistonBlock.FACING, facing)
                    .setValue(MovingPistonBlock.TYPE, this.isSticky ? PistonType.STICKY : PistonType.DEFAULT);
                map.remove(blockpos);
                level.setBlock(blockpos, blockstate6, 68);
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(blockpos, blockstate6, blockstate4, facing, true, true));
            }

            BlockState blockstate3 = Blocks.AIR.defaultBlockState();

            for (BlockPos blockpos4 : map.keySet()) {
                level.setBlock(blockpos4, blockstate3, 82);
            }

            for (Entry<BlockPos, BlockState> entry : map.entrySet()) {
                BlockPos blockpos5 = entry.getKey();
                BlockState blockstate2 = entry.getValue();
                blockstate2.updateIndirectNeighbourShapes(level, blockpos5, 2);
                blockstate3.updateNeighbourShapes(level, blockpos5, 2);
                blockstate3.updateIndirectNeighbourShapes(level, blockpos5, 2);
            }

            i = 0;

            for (int l = list2.size() - 1; l >= 0; l--) {
                BlockState blockstate7 = ablockstate[i++];
                BlockPos blockpos6 = list2.get(l);
                blockstate7.updateIndirectNeighbourShapes(level, blockpos6, 2);
                level.updateNeighborsAt(blockpos6, blockstate7.getBlock());
            }

            for (int i1 = list.size() - 1; i1 >= 0; i1--) {
                level.updateNeighborsAt(list.get(i1), ablockstate[i++].getBlock());
            }

            if (extending) {
                level.updateNeighborsAt(blockpos, Blocks.PISTON_HEAD);
            }

            return true;
        }
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    public BlockState rotate(BlockState state, net.minecraft.world.level.LevelAccessor world, BlockPos pos, Rotation direction) {
         return state.getValue(EXTENDED) ? state : super.rotate(state, world, pos, direction);
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, EXTENDED);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return state.getValue(EXTENDED);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
