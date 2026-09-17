package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class CrossCollisionBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION
        .entrySet()
        .stream()
        .filter(p_52346_ -> p_52346_.getKey().getAxis().isHorizontal())
        .collect(Util.toMap());
    protected final VoxelShape[] collisionShapeByIndex;
    protected final VoxelShape[] shapeByIndex;
    private final Object2IntMap<BlockState> stateToIndex = new Object2IntOpenHashMap<>();

    protected CrossCollisionBlock(float nodeWidth, float extensionWidth, float nodeHeight, float extensionHeight, float collisionHeight, BlockBehaviour.Properties properties) {
        super(properties);
        this.collisionShapeByIndex = this.makeShapes(nodeWidth, extensionWidth, collisionHeight, 0.0F, collisionHeight);
        this.shapeByIndex = this.makeShapes(nodeWidth, extensionWidth, nodeHeight, 0.0F, extensionHeight);

        for (BlockState blockstate : this.stateDefinition.getPossibleStates()) {
            this.getAABBIndex(blockstate);
        }
    }

    @Override
    protected abstract MapCodec<? extends CrossCollisionBlock> codec();

    protected VoxelShape[] makeShapes(float nodeWidth, float extensionWidth, float nodeHeight, float extensionBottom, float extensionHeight) {
        float f = 8.0F - nodeWidth;
        float f1 = 8.0F + nodeWidth;
        float f2 = 8.0F - extensionWidth;
        float f3 = 8.0F + extensionWidth;
        VoxelShape voxelshape = Block.box((double)f, 0.0, (double)f, (double)f1, (double)nodeHeight, (double)f1);
        VoxelShape voxelshape1 = Block.box((double)f2, (double)extensionBottom, 0.0, (double)f3, (double)extensionHeight, (double)f3);
        VoxelShape voxelshape2 = Block.box((double)f2, (double)extensionBottom, (double)f2, (double)f3, (double)extensionHeight, 16.0);
        VoxelShape voxelshape3 = Block.box(0.0, (double)extensionBottom, (double)f2, (double)f3, (double)extensionHeight, (double)f3);
        VoxelShape voxelshape4 = Block.box((double)f2, (double)extensionBottom, (double)f2, 16.0, (double)extensionHeight, (double)f3);
        VoxelShape voxelshape5 = Shapes.or(voxelshape1, voxelshape4);
        VoxelShape voxelshape6 = Shapes.or(voxelshape2, voxelshape3);
        VoxelShape[] avoxelshape = new VoxelShape[]{
            Shapes.empty(),
            voxelshape2,
            voxelshape3,
            voxelshape6,
            voxelshape1,
            Shapes.or(voxelshape2, voxelshape1),
            Shapes.or(voxelshape3, voxelshape1),
            Shapes.or(voxelshape6, voxelshape1),
            voxelshape4,
            Shapes.or(voxelshape2, voxelshape4),
            Shapes.or(voxelshape3, voxelshape4),
            Shapes.or(voxelshape6, voxelshape4),
            voxelshape5,
            Shapes.or(voxelshape2, voxelshape5),
            Shapes.or(voxelshape3, voxelshape5),
            Shapes.or(voxelshape6, voxelshape5)
        };

        for (int i = 0; i < 16; i++) {
            avoxelshape[i] = Shapes.or(voxelshape, avoxelshape[i]);
        }

        return avoxelshape;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex[this.getAABBIndex(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.collisionShapeByIndex[this.getAABBIndex(state)];
    }

    private static int indexFor(Direction facing) {
        return 1 << facing.get2DDataValue();
    }

    protected int getAABBIndex(BlockState state) {
        return this.stateToIndex.computeIntIfAbsent(state, p_52366_ -> {
            int i = 0;
            if (p_52366_.getValue(NORTH)) {
                i |= indexFor(Direction.NORTH);
            }

            if (p_52366_.getValue(EAST)) {
                i |= indexFor(Direction.EAST);
            }

            if (p_52366_.getValue(SOUTH)) {
                i |= indexFor(Direction.SOUTH);
            }

            if (p_52366_.getValue(WEST)) {
                i |= indexFor(Direction.WEST);
            }

            return i;
        });
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        switch (rot) {
            case CLOCKWISE_180:
                return state.setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
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
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK:
                return state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default:
                return super.mirror(state, mirror);
        }
    }
}
