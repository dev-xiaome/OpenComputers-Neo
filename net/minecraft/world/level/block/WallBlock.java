package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final EnumProperty<WallSide> EAST_WALL = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> NORTH_WALL = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> SOUTH_WALL = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST_WALL = BlockStateProperties.WEST_WALL;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final Map<BlockState, VoxelShape> shapeByIndex;
    private final Map<BlockState, VoxelShape> collisionShapeByIndex;
    private static final int WALL_WIDTH = 3;
    private static final int WALL_HEIGHT = 14;
    private static final int POST_WIDTH = 4;
    private static final int POST_COVER_WIDTH = 1;
    private static final int WALL_COVER_START = 7;
    private static final int WALL_COVER_END = 9;
    private static final VoxelShape POST_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape NORTH_TEST = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_TEST = Block.box(0.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape EAST_TEST = Block.box(7.0, 0.0, 7.0, 16.0, 16.0, 9.0);

    @Override
    public MapCodec<WallBlock> codec() {
        return CODEC;
    }

    public WallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(UP, Boolean.valueOf(true))
                .setValue(NORTH_WALL, WallSide.NONE)
                .setValue(EAST_WALL, WallSide.NONE)
                .setValue(SOUTH_WALL, WallSide.NONE)
                .setValue(WEST_WALL, WallSide.NONE)
                .setValue(WATERLOGGED, Boolean.valueOf(false))
        );
        this.shapeByIndex = this.makeShapes(4.0F, 3.0F, 16.0F, 0.0F, 14.0F, 16.0F);
        this.collisionShapeByIndex = this.makeShapes(4.0F, 3.0F, 24.0F, 0.0F, 24.0F, 24.0F);
    }

    private static VoxelShape applyWallShape(VoxelShape baseShape, WallSide height, VoxelShape lowShape, VoxelShape tallShape) {
        if (height == WallSide.TALL) {
            return Shapes.or(baseShape, tallShape);
        } else {
            return height == WallSide.LOW ? Shapes.or(baseShape, lowShape) : baseShape;
        }
    }

    private Map<BlockState, VoxelShape> makeShapes(float width, float depth, float wallPostHeight, float wallMinY, float wallLowHeight, float wallTallHeight) {
        float f = 8.0F - width;
        float f1 = 8.0F + width;
        float f2 = 8.0F - depth;
        float f3 = 8.0F + depth;
        VoxelShape voxelshape = Block.box((double)f, 0.0, (double)f, (double)f1, (double)wallPostHeight, (double)f1);
        VoxelShape voxelshape1 = Block.box((double)f2, (double)wallMinY, 0.0, (double)f3, (double)wallLowHeight, (double)f3);
        VoxelShape voxelshape2 = Block.box((double)f2, (double)wallMinY, (double)f2, (double)f3, (double)wallLowHeight, 16.0);
        VoxelShape voxelshape3 = Block.box(0.0, (double)wallMinY, (double)f2, (double)f3, (double)wallLowHeight, (double)f3);
        VoxelShape voxelshape4 = Block.box((double)f2, (double)wallMinY, (double)f2, 16.0, (double)wallLowHeight, (double)f3);
        VoxelShape voxelshape5 = Block.box((double)f2, (double)wallMinY, 0.0, (double)f3, (double)wallTallHeight, (double)f3);
        VoxelShape voxelshape6 = Block.box((double)f2, (double)wallMinY, (double)f2, (double)f3, (double)wallTallHeight, 16.0);
        VoxelShape voxelshape7 = Block.box(0.0, (double)wallMinY, (double)f2, (double)f3, (double)wallTallHeight, (double)f3);
        VoxelShape voxelshape8 = Block.box((double)f2, (double)wallMinY, (double)f2, 16.0, (double)wallTallHeight, (double)f3);
        Builder<BlockState, VoxelShape> builder = ImmutableMap.builder();

        for (Boolean obool : UP.getPossibleValues()) {
            for (WallSide wallside : EAST_WALL.getPossibleValues()) {
                for (WallSide wallside1 : NORTH_WALL.getPossibleValues()) {
                    for (WallSide wallside2 : WEST_WALL.getPossibleValues()) {
                        for (WallSide wallside3 : SOUTH_WALL.getPossibleValues()) {
                            VoxelShape voxelshape9 = Shapes.empty();
                            voxelshape9 = applyWallShape(voxelshape9, wallside, voxelshape4, voxelshape8);
                            voxelshape9 = applyWallShape(voxelshape9, wallside2, voxelshape3, voxelshape7);
                            voxelshape9 = applyWallShape(voxelshape9, wallside1, voxelshape1, voxelshape5);
                            voxelshape9 = applyWallShape(voxelshape9, wallside3, voxelshape2, voxelshape6);
                            if (obool) {
                                voxelshape9 = Shapes.or(voxelshape9, voxelshape);
                            }

                            BlockState blockstate = this.defaultBlockState()
                                .setValue(UP, obool)
                                .setValue(EAST_WALL, wallside)
                                .setValue(WEST_WALL, wallside2)
                                .setValue(NORTH_WALL, wallside1)
                                .setValue(SOUTH_WALL, wallside3);
                            builder.put(blockstate.setValue(WATERLOGGED, Boolean.valueOf(false)), voxelshape9);
                            builder.put(blockstate.setValue(WATERLOGGED, Boolean.valueOf(true)), voxelshape9);
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex.get(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.collisionShapeByIndex.get(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private boolean connectsTo(BlockState state, boolean sideSolid, Direction direction) {
        Block block = state.getBlock();
        boolean flag = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction);
        return state.is(BlockTags.WALLS) || !isExceptionForConnection(state) && sideSolid || block instanceof IronBarsBlock || flag;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader levelreader = context.getLevel();
        BlockPos blockpos = context.getClickedPos();
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockpos1 = blockpos.north();
        BlockPos blockpos2 = blockpos.east();
        BlockPos blockpos3 = blockpos.south();
        BlockPos blockpos4 = blockpos.west();
        BlockPos blockpos5 = blockpos.above();
        BlockState blockstate = levelreader.getBlockState(blockpos1);
        BlockState blockstate1 = levelreader.getBlockState(blockpos2);
        BlockState blockstate2 = levelreader.getBlockState(blockpos3);
        BlockState blockstate3 = levelreader.getBlockState(blockpos4);
        BlockState blockstate4 = levelreader.getBlockState(blockpos5);
        boolean flag = this.connectsTo(blockstate, blockstate.isFaceSturdy(levelreader, blockpos1, Direction.SOUTH), Direction.SOUTH);
        boolean flag1 = this.connectsTo(blockstate1, blockstate1.isFaceSturdy(levelreader, blockpos2, Direction.WEST), Direction.WEST);
        boolean flag2 = this.connectsTo(blockstate2, blockstate2.isFaceSturdy(levelreader, blockpos3, Direction.NORTH), Direction.NORTH);
        boolean flag3 = this.connectsTo(blockstate3, blockstate3.isFaceSturdy(levelreader, blockpos4, Direction.EAST), Direction.EAST);
        BlockState blockstate5 = this.defaultBlockState().setValue(WATERLOGGED, Boolean.valueOf(fluidstate.getType() == Fluids.WATER));
        return this.updateShape(levelreader, blockstate5, blockpos5, blockstate4, flag, flag1, flag2, flag3);
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        if (facing == Direction.DOWN) {
            return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
        } else {
            return facing == Direction.UP
                ? this.topUpdate(level, state, facingPos, facingState)
                : this.sideUpdate(level, currentPos, state, facingPos, facingState, facing);
        }
    }

    private static boolean isConnected(BlockState state, Property<WallSide> heightProperty) {
        return state.getValue(heightProperty) != WallSide.NONE;
    }

    private static boolean isCovered(VoxelShape firstShape, VoxelShape secondShape) {
        return !Shapes.joinIsNotEmpty(secondShape, firstShape, BooleanOp.ONLY_FIRST);
    }

    private BlockState topUpdate(LevelReader level, BlockState state, BlockPos pos, BlockState secondState) {
        boolean flag = isConnected(state, NORTH_WALL);
        boolean flag1 = isConnected(state, EAST_WALL);
        boolean flag2 = isConnected(state, SOUTH_WALL);
        boolean flag3 = isConnected(state, WEST_WALL);
        return this.updateShape(level, state, pos, secondState, flag, flag1, flag2, flag3);
    }

    private BlockState sideUpdate(LevelReader level, BlockPos firstPos, BlockState firstState, BlockPos secondPos, BlockState secondState, Direction dir) {
        Direction direction = dir.getOpposite();
        boolean flag = dir == Direction.NORTH
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, NORTH_WALL);
        boolean flag1 = dir == Direction.EAST
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, EAST_WALL);
        boolean flag2 = dir == Direction.SOUTH
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, SOUTH_WALL);
        boolean flag3 = dir == Direction.WEST
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, WEST_WALL);
        BlockPos blockpos = firstPos.above();
        BlockState blockstate = level.getBlockState(blockpos);
        return this.updateShape(level, firstState, blockpos, blockstate, flag, flag1, flag2, flag3);
    }

    private BlockState updateShape(
        LevelReader level,
        BlockState state,
        BlockPos pos,
        BlockState neighbour,
        boolean northConnection,
        boolean eastConnection,
        boolean southConnection,
        boolean westConnection
    ) {
        VoxelShape voxelshape = neighbour.getCollisionShape(level, pos).getFaceShape(Direction.DOWN);
        BlockState blockstate = this.updateSides(state, northConnection, eastConnection, southConnection, westConnection, voxelshape);
        return blockstate.setValue(UP, Boolean.valueOf(this.shouldRaisePost(blockstate, neighbour, voxelshape)));
    }

    private boolean shouldRaisePost(BlockState state, BlockState neighbour, VoxelShape shape) {
        boolean flag = neighbour.getBlock() instanceof WallBlock && neighbour.getValue(UP);
        if (flag) {
            return true;
        } else {
            WallSide wallside = state.getValue(NORTH_WALL);
            WallSide wallside1 = state.getValue(SOUTH_WALL);
            WallSide wallside2 = state.getValue(EAST_WALL);
            WallSide wallside3 = state.getValue(WEST_WALL);
            boolean flag1 = wallside1 == WallSide.NONE;
            boolean flag2 = wallside3 == WallSide.NONE;
            boolean flag3 = wallside2 == WallSide.NONE;
            boolean flag4 = wallside == WallSide.NONE;
            boolean flag5 = flag4 && flag1 && flag2 && flag3 || flag4 != flag1 || flag2 != flag3;
            if (flag5) {
                return true;
            } else {
                boolean flag6 = wallside == WallSide.TALL && wallside1 == WallSide.TALL || wallside2 == WallSide.TALL && wallside3 == WallSide.TALL;
                return flag6 ? false : neighbour.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(shape, POST_TEST);
            }
        }
    }

    private BlockState updateSides(BlockState state, boolean northConnection, boolean eastConnection, boolean southConnection, boolean westConnection, VoxelShape wallShape) {
        return state.setValue(NORTH_WALL, this.makeWallState(northConnection, wallShape, NORTH_TEST))
            .setValue(EAST_WALL, this.makeWallState(eastConnection, wallShape, EAST_TEST))
            .setValue(SOUTH_WALL, this.makeWallState(southConnection, wallShape, SOUTH_TEST))
            .setValue(WEST_WALL, this.makeWallState(westConnection, wallShape, WEST_TEST));
    }

    private WallSide makeWallState(boolean allowConnection, VoxelShape shape, VoxelShape neighbourShape) {
        if (allowConnection) {
            return isCovered(shape, neighbourShape) ? WallSide.TALL : WallSide.LOW;
        } else {
            return WallSide.NONE;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, NORTH_WALL, EAST_WALL, WEST_WALL, SOUTH_WALL, WATERLOGGED);
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return state.setValue(NORTH_WALL, state.getValue(SOUTH_WALL))
                    .setValue(EAST_WALL, state.getValue(WEST_WALL))
                    .setValue(SOUTH_WALL, state.getValue(NORTH_WALL))
                    .setValue(WEST_WALL, state.getValue(EAST_WALL));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH_WALL, state.getValue(EAST_WALL))
                    .setValue(EAST_WALL, state.getValue(SOUTH_WALL))
                    .setValue(SOUTH_WALL, state.getValue(WEST_WALL))
                    .setValue(WEST_WALL, state.getValue(NORTH_WALL));
            case CLOCKWISE_90:
                return state.setValue(NORTH_WALL, state.getValue(WEST_WALL))
                    .setValue(EAST_WALL, state.getValue(NORTH_WALL))
                    .setValue(SOUTH_WALL, state.getValue(EAST_WALL))
                    .setValue(WEST_WALL, state.getValue(SOUTH_WALL));
            default:
                return state;
        }
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH_WALL, state.getValue(SOUTH_WALL)).setValue(SOUTH_WALL, state.getValue(NORTH_WALL));
            case FRONT_BACK:
                return state.setValue(EAST_WALL, state.getValue(WEST_WALL)).setValue(WEST_WALL, state.getValue(EAST_WALL));
            default:
                return super.mirror(state, mirror);
        }
    }
}
