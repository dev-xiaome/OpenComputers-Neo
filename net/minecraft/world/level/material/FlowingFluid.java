package net.minecraft.world.level.material;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ByteLinkedOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanMap;
import it.unimi.dsi.fastutil.shorts.Short2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class FlowingFluid extends Fluid {
    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_FLOWING;
    private static final int CACHE_SIZE = 200;
    private static final ThreadLocal<Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>> OCCLUSION_CACHE = ThreadLocal.withInitial(() -> {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap = new Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey>(200) {
            @Override
            protected void rehash(int newSize) {
            }
        };
        object2bytelinkedopenhashmap.defaultReturnValue((byte)127);
        return object2bytelinkedopenhashmap;
    });
    private final Map<FluidState, VoxelShape> shapes = Maps.newIdentityHashMap();

    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        builder.add(FALLING);
    }

    @Override
    public Vec3 getFlow(BlockGetter blockReader, BlockPos pos, FluidState fluidState) {
        double d0 = 0.0;
        double d1 = 0.0;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            blockpos$mutableblockpos.setWithOffset(pos, direction);
            FluidState fluidstate = blockReader.getFluidState(blockpos$mutableblockpos);
            if (this.affectsFlow(fluidstate)) {
                float f = fluidstate.getOwnHeight();
                float f1 = 0.0F;
                if (f == 0.0F) {
                    if (!blockReader.getBlockState(blockpos$mutableblockpos).blocksMotion()) {
                        BlockPos blockpos = blockpos$mutableblockpos.below();
                        FluidState fluidstate1 = blockReader.getFluidState(blockpos);
                        if (this.affectsFlow(fluidstate1)) {
                            f = fluidstate1.getOwnHeight();
                            if (f > 0.0F) {
                                f1 = fluidState.getOwnHeight() - (f - 0.8888889F);
                            }
                        }
                    }
                } else if (f > 0.0F) {
                    f1 = fluidState.getOwnHeight() - f;
                }

                if (f1 != 0.0F) {
                    d0 += (double)((float)direction.getStepX() * f1);
                    d1 += (double)((float)direction.getStepZ() * f1);
                }
            }
        }

        Vec3 vec3 = new Vec3(d0, 0.0, d1);
        if (fluidState.getValue(FALLING)) {
            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                blockpos$mutableblockpos.setWithOffset(pos, direction1);
                if (this.isSolidFace(blockReader, blockpos$mutableblockpos, direction1)
                    || this.isSolidFace(blockReader, blockpos$mutableblockpos.above(), direction1)) {
                    vec3 = vec3.normalize().add(0.0, -6.0, 0.0);
                    break;
                }
            }
        }

        return vec3.normalize();
    }

    private boolean affectsFlow(FluidState state) {
        return state.isEmpty() || state.getType().isSame(this);
    }

    protected boolean isSolidFace(BlockGetter level, BlockPos neighborPos, Direction side) {
        BlockState blockstate = level.getBlockState(neighborPos);
        FluidState fluidstate = level.getFluidState(neighborPos);
        if (fluidstate.getType().isSame(this)) {
            return false;
        } else if (side == Direction.UP) {
            return true;
        } else {
            return blockstate.getBlock() instanceof IceBlock ? false : blockstate.isFaceSturdy(level, neighborPos, side);
        }
    }

    protected void spread(Level level, BlockPos pos, FluidState state) {
        if (!state.isEmpty()) {
            BlockState blockstate = level.getBlockState(pos);
            BlockPos blockpos = pos.below();
            BlockState blockstate1 = level.getBlockState(blockpos);
            FluidState fluidstate = this.getNewLiquid(level, blockpos, blockstate1);
            if (this.canSpreadTo(
                level, pos, blockstate, Direction.DOWN, blockpos, blockstate1, level.getFluidState(blockpos), fluidstate.getType()
            )) {
                this.spreadTo(level, blockpos, blockstate1, Direction.DOWN, fluidstate);
                if (this.sourceNeighborCount(level, pos) >= 3) {
                    this.spreadToSides(level, pos, state, blockstate);
                }
            } else if (state.isSource() || !this.isWaterHole(level, fluidstate.getType(), pos, blockstate, blockpos, blockstate1)) {
                this.spreadToSides(level, pos, state, blockstate);
            }
        }
    }

    private void spreadToSides(Level level, BlockPos pos, FluidState fluidState, BlockState blockState) {
        int i = fluidState.getAmount() - this.getDropOff(level);
        if (fluidState.getValue(FALLING)) {
            i = 7;
        }

        if (i > 0) {
            Map<Direction, FluidState> map = this.getSpread(level, pos, blockState);

            for (Entry<Direction, FluidState> entry : map.entrySet()) {
                Direction direction = entry.getKey();
                FluidState fluidstate = entry.getValue();
                BlockPos blockpos = pos.relative(direction);
                BlockState blockstate = level.getBlockState(blockpos);
                if (this.canSpreadTo(level, pos, blockState, direction, blockpos, blockstate, level.getFluidState(blockpos), fluidstate.getType())) {
                    this.spreadTo(level, blockpos, blockstate, direction, fluidstate);
                }
            }
        }
    }

    protected FluidState getNewLiquid(Level level, BlockPos pos, BlockState blockState) {
        int i = 0;
        int j = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.relative(direction);
            BlockState blockstate = level.getBlockState(blockpos);
            FluidState fluidstate = blockstate.getFluidState();
            if (fluidstate.getType().isSame(this) && this.canPassThroughWall(direction, level, pos, blockState, blockpos, blockstate)) {
                if (fluidstate.isSource() && net.neoforged.neoforge.event.EventHooks.canCreateFluidSource(level, blockpos, blockstate)) {
                    j++;
                }

                i = Math.max(i, fluidstate.getAmount());
            }
        }

        if (j >= 2) {
            BlockState blockstate1 = level.getBlockState(pos.below());
            FluidState fluidstate1 = blockstate1.getFluidState();
            if (blockstate1.isSolid() || this.isSourceBlockOfThisType(fluidstate1)) {
                return this.getSource(false);
            }
        }

        BlockPos blockpos1 = pos.above();
        BlockState blockstate2 = level.getBlockState(blockpos1);
        FluidState fluidstate2 = blockstate2.getFluidState();
        if (!fluidstate2.isEmpty()
            && fluidstate2.getType().isSame(this)
            && this.canPassThroughWall(Direction.UP, level, pos, blockState, blockpos1, blockstate2)) {
            return this.getFlowing(8, true);
        } else {
            int k = i - this.getDropOff(level);
            return k <= 0 ? Fluids.EMPTY.defaultFluidState() : this.getFlowing(k, false);
        }
    }

    private boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState) {
        Object2ByteLinkedOpenHashMap<Block.BlockStatePairKey> object2bytelinkedopenhashmap;
        if (!state.getBlock().hasDynamicShape() && !spreadState.getBlock().hasDynamicShape()) {
            object2bytelinkedopenhashmap = OCCLUSION_CACHE.get();
        } else {
            object2bytelinkedopenhashmap = null;
        }

        Block.BlockStatePairKey block$blockstatepairkey;
        if (object2bytelinkedopenhashmap != null) {
            block$blockstatepairkey = new Block.BlockStatePairKey(state, spreadState, direction);
            byte b0 = object2bytelinkedopenhashmap.getAndMoveToFirst(block$blockstatepairkey);
            if (b0 != 127) {
                return b0 != 0;
            }
        } else {
            block$blockstatepairkey = null;
        }

        VoxelShape voxelshape1 = state.getCollisionShape(level, pos);
        VoxelShape voxelshape = spreadState.getCollisionShape(level, spreadPos);
        boolean flag = !Shapes.mergedFaceOccludes(voxelshape1, voxelshape, direction);
        if (object2bytelinkedopenhashmap != null) {
            if (object2bytelinkedopenhashmap.size() == 200) {
                object2bytelinkedopenhashmap.removeLastByte();
            }

            object2bytelinkedopenhashmap.putAndMoveToFirst(block$blockstatepairkey, (byte)(flag ? 1 : 0));
        }

        return flag;
    }

    public abstract Fluid getFlowing();

    public FluidState getFlowing(int level, boolean falling) {
        return this.getFlowing().defaultFluidState().setValue(LEVEL, Integer.valueOf(level)).setValue(FALLING, Boolean.valueOf(falling));
    }

    public abstract Fluid getSource();

    public FluidState getSource(boolean falling) {
        return this.getSource().defaultFluidState().setValue(FALLING, Boolean.valueOf(falling));
    }

    @Override
    public boolean canConvertToSource(FluidState state, Level level, BlockPos pos) {
        return this.canConvertToSource(level);
    }

    /**
     * @deprecated Forge: Use {@link #canConvertToSource(FluidState, Level, BlockPos)}
     *             instead.
     */
    @Deprecated
    protected abstract boolean canConvertToSource(Level level);

    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState) {
        if (blockState.getBlock() instanceof LiquidBlockContainer) {
            ((LiquidBlockContainer)blockState.getBlock()).placeLiquid(level, pos, blockState, fluidState);
        } else {
            if (!blockState.isAir()) {
                this.beforeDestroyingBlock(level, pos, blockState);
            }

            level.setBlock(pos, fluidState.createLegacyBlock(), 3);
        }
    }

    protected abstract void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state);

    private static short getCacheKey(BlockPos sourcePos, BlockPos spreadPos) {
        int i = spreadPos.getX() - sourcePos.getX();
        int j = spreadPos.getZ() - sourcePos.getZ();
        return (short)((i + 128 & 0xFF) << 8 | j + 128 & 0xFF);
    }

    protected int getSlopeDistance(
        LevelReader level,
        BlockPos spreadPos,
        int distance,
        Direction p_direction,
        BlockState currentSpreadState,
        BlockPos sourcePos,
        Short2ObjectMap<Pair<BlockState, FluidState>> stateCache,
        Short2BooleanMap waterHoleCache
    ) {
        int i = 1000;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != p_direction) {
                BlockPos blockpos = spreadPos.relative(direction);
                short short1 = getCacheKey(sourcePos, blockpos);
                Pair<BlockState, FluidState> pair = stateCache.computeIfAbsent(short1, p_284932_ -> {
                    BlockState blockstate1 = level.getBlockState(blockpos);
                    return Pair.of(blockstate1, blockstate1.getFluidState());
                });
                BlockState blockstate = pair.getFirst();
                FluidState fluidstate = pair.getSecond();
                if (this.canPassThrough(level, this.getFlowing(), spreadPos, currentSpreadState, direction, blockpos, blockstate, fluidstate)) {
                    boolean flag = waterHoleCache.computeIfAbsent(short1, p_192912_ -> {
                        BlockPos blockpos1 = blockpos.below();
                        BlockState blockstate1 = level.getBlockState(blockpos1);
                        return this.isWaterHole(level, this.getFlowing(), blockpos, blockstate, blockpos1, blockstate1);
                    });
                    if (flag) {
                        return distance;
                    }

                    if (distance < this.getSlopeFindDistance(level)) {
                        int j = this.getSlopeDistance(level, blockpos, distance + 1, direction.getOpposite(), blockstate, sourcePos, stateCache, waterHoleCache);
                        if (j < i) {
                            i = j;
                        }
                    }
                }
            }
        }

        return i;
    }

    private boolean isWaterHole(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState) {
        if (!this.canPassThroughWall(Direction.DOWN, level, pos, state, spreadPos, spreadState)) {
            return false;
        } else {
            return spreadState.getFluidState().getType().isSame(this) ? true : this.canHoldFluid(level, spreadPos, spreadState, fluid);
        }
    }

    private boolean canPassThrough(
        BlockGetter level,
        Fluid fluid,
        BlockPos pos,
        BlockState state,
        Direction direction,
        BlockPos spreadPos,
        BlockState spreadState,
        FluidState fluidState
    ) {
        return !this.isSourceBlockOfThisType(fluidState)
            && this.canPassThroughWall(direction, level, pos, state, spreadPos, spreadState)
            && this.canHoldFluid(level, spreadPos, spreadState, fluid);
    }

    private boolean isSourceBlockOfThisType(FluidState state) {
        return state.getType().isSame(this) && state.isSource();
    }

    protected abstract int getSlopeFindDistance(LevelReader level);

    /**
     * Returns the number of immediately adjacent source blocks of the same fluid that lie on the horizontal plane.
     */
    private int sourceNeighborCount(LevelReader level, BlockPos pos) {
        int i = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.relative(direction);
            FluidState fluidstate = level.getFluidState(blockpos);
            if (this.isSourceBlockOfThisType(fluidstate)) {
                i++;
            }
        }

        return i;
    }

    protected Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state) {
        int i = 1000;
        Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
        Short2ObjectMap<Pair<BlockState, FluidState>> short2objectmap = new Short2ObjectOpenHashMap<>();
        Short2BooleanMap short2booleanmap = new Short2BooleanOpenHashMap();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.relative(direction);
            short short1 = getCacheKey(pos, blockpos);
            Pair<BlockState, FluidState> pair = short2objectmap.computeIfAbsent(short1, p_284929_ -> {
                BlockState blockstate1 = level.getBlockState(blockpos);
                return Pair.of(blockstate1, blockstate1.getFluidState());
            });
            BlockState blockstate = pair.getFirst();
            FluidState fluidstate = pair.getSecond();
            FluidState fluidstate1 = this.getNewLiquid(level, blockpos, blockstate);
            if (this.canPassThrough(level, fluidstate1.getType(), pos, state, direction, blockpos, blockstate, fluidstate)) {
                BlockPos blockpos1 = blockpos.below();
                boolean flag = short2booleanmap.computeIfAbsent(short1, p_255612_ -> {
                    BlockState blockstate1 = level.getBlockState(blockpos1);
                    return this.isWaterHole(level, this.getFlowing(), blockpos, blockstate, blockpos1, blockstate1);
                });
                int j;
                if (flag) {
                    j = 0;
                } else {
                    j = this.getSlopeDistance(level, blockpos, 1, direction.getOpposite(), blockstate, pos, short2objectmap, short2booleanmap);
                }

                if (j < i) {
                    map.clear();
                }

                if (j <= i) {
                    map.put(direction, fluidstate1);
                    i = j;
                }
            }
        }

        return map;
    }

    private boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        Block block = state.getBlock();
        if (block instanceof LiquidBlockContainer liquidblockcontainer) {
            return liquidblockcontainer.canPlaceLiquid(null, level, pos, state, fluid);
        } else if (block instanceof DoorBlock
            || state.is(BlockTags.SIGNS)
            || state.is(Blocks.LADDER)
            || state.is(Blocks.SUGAR_CANE)
            || state.is(Blocks.BUBBLE_COLUMN)) {
            return false;
        } else {
            return !state.is(Blocks.NETHER_PORTAL)
                    && !state.is(Blocks.END_PORTAL)
                    && !state.is(Blocks.END_GATEWAY)
                    && !state.is(Blocks.STRUCTURE_VOID)
                ? !state.blocksMotion()
                : false;
        }
    }

    protected boolean canSpreadTo(
        BlockGetter level,
        BlockPos fromPos,
        BlockState fromBlockState,
        Direction direction,
        BlockPos toPos,
        BlockState toBlockState,
        FluidState toFluidState,
        Fluid fluid
    ) {
        return toFluidState.canBeReplacedWith(level, toPos, fluid, direction)
            && this.canPassThroughWall(direction, level, fromPos, fromBlockState, toPos, toBlockState)
            && this.canHoldFluid(level, toPos, toBlockState, fluid);
    }

    protected abstract int getDropOff(LevelReader level);

    protected int getSpreadDelay(Level level, BlockPos pos, FluidState currentState, FluidState newState) {
        return this.getTickDelay(level);
    }

    @Override
    public void tick(Level level, BlockPos pos, FluidState state) {
        if (!state.isSource()) {
            FluidState fluidstate = this.getNewLiquid(level, pos, level.getBlockState(pos));
            int i = this.getSpreadDelay(level, pos, state, fluidstate);
            if (fluidstate.isEmpty()) {
                state = fluidstate;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else if (!fluidstate.equals(state)) {
                state = fluidstate;
                BlockState blockstate = fluidstate.createLegacyBlock();
                level.setBlock(pos, blockstate, 2);
                level.scheduleTick(pos, fluidstate.getType(), i);
                level.updateNeighborsAt(pos, blockstate.getBlock());
            }
        }

        this.spread(level, pos, state);
    }

    protected static int getLegacyLevel(FluidState state) {
        return state.isSource() ? 0 : 8 - Math.min(state.getAmount(), 8) + (state.getValue(FALLING) ? 8 : 0);
    }

    private static boolean hasSameAbove(FluidState fluidState, BlockGetter level, BlockPos pos) {
        return fluidState.getType().isSame(level.getFluidState(pos.above()).getType());
    }

    @Override
    public float getHeight(FluidState state, BlockGetter level, BlockPos pos) {
        return hasSameAbove(state, level, pos) ? 1.0F : state.getOwnHeight();
    }

    @Override
    public float getOwnHeight(FluidState state) {
        return (float)state.getAmount() / 9.0F;
    }

    @Override
    public abstract int getAmount(FluidState state);

    @Override
    public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return state.getAmount() == 9 && hasSameAbove(state, level, pos)
            ? Shapes.block()
            : this.shapes.computeIfAbsent(state, p_76073_ -> Shapes.box(0.0, 0.0, 0.0, 1.0, (double)p_76073_.getHeight(level, pos), 1.0));
    }
}
