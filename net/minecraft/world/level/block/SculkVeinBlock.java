package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class SculkVeinBlock extends MultifaceBlock implements SculkBehaviour, SimpleWaterloggedBlock {
    public static final MapCodec<SculkVeinBlock> CODEC = simpleCodec(SculkVeinBlock::new);
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final MultifaceSpreader veinSpreader = new MultifaceSpreader(new SculkVeinBlock.SculkVeinSpreaderConfig(MultifaceSpreader.DEFAULT_SPREAD_ORDER));
    private final MultifaceSpreader sameSpaceSpreader = new MultifaceSpreader(
        new SculkVeinBlock.SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType.SAME_POSITION)
    );

    @Override
    public MapCodec<SculkVeinBlock> codec() {
        return CODEC;
    }

    public SculkVeinBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    @Override
    public MultifaceSpreader getSpreader() {
        return this.veinSpreader;
    }

    public MultifaceSpreader getSameSpaceSpreader() {
        return this.sameSpaceSpreader;
    }

    public static boolean regrow(LevelAccessor level, BlockPos pos, BlockState state, Collection<Direction> directions) {
        boolean flag = false;
        BlockState blockstate = Blocks.SCULK_VEIN.defaultBlockState();

        for (Direction direction : directions) {
            BlockPos blockpos = pos.relative(direction);
            if (canAttachTo(level, direction, blockpos, level.getBlockState(blockpos))) {
                blockstate = blockstate.setValue(getFaceProperty(direction), Boolean.valueOf(true));
                flag = true;
            }
        }

        if (!flag) {
            return false;
        } else {
            if (!state.getFluidState().isEmpty()) {
                blockstate = blockstate.setValue(WATERLOGGED, Boolean.valueOf(true));
            }

            level.setBlock(pos, blockstate, 3);
            return true;
        }
    }

    @Override
    public void onDischarged(LevelAccessor level, BlockState state, BlockPos pos, RandomSource random) {
        if (state.is(this)) {
            for (Direction direction : DIRECTIONS) {
                BooleanProperty booleanproperty = getFaceProperty(direction);
                if (state.getValue(booleanproperty) && level.getBlockState(pos.relative(direction)).is(Blocks.SCULK)) {
                    state = state.setValue(booleanproperty, Boolean.valueOf(false));
                }
            }

            if (!hasAnyFace(state)) {
                FluidState fluidstate = level.getFluidState(pos);
                state = (fluidstate.isEmpty() ? Blocks.AIR : Blocks.WATER).defaultBlockState();
            }

            level.setBlock(pos, state, 3);
            SculkBehaviour.super.onDischarged(level, state, pos, random);
        }
    }

    @Override
    public int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
    ) {
        if (shouldConvertBlocks && this.attemptPlaceSculk(spreader, level, cursor.getPos(), random)) {
            return cursor.getCharge() - 1;
        } else {
            return random.nextInt(spreader.chargeDecayRate()) == 0 ? Mth.floor((float)cursor.getCharge() * 0.5F) : cursor.getCharge();
        }
    }

    private boolean attemptPlaceSculk(SculkSpreader spreader, LevelAccessor level, BlockPos pos, RandomSource random) {
        BlockState blockstate = level.getBlockState(pos);
        TagKey<Block> tagkey = spreader.replaceableBlocks();

        for (Direction direction : Direction.allShuffled(random)) {
            if (hasFace(blockstate, direction)) {
                BlockPos blockpos = pos.relative(direction);
                BlockState blockstate1 = level.getBlockState(blockpos);
                if (blockstate1.is(tagkey)) {
                    BlockState blockstate2 = Blocks.SCULK.defaultBlockState();
                    level.setBlock(blockpos, blockstate2, 3);
                    Block.pushEntitiesUp(blockstate1, blockstate2, level, blockpos);
                    level.playSound(null, blockpos, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
                    this.veinSpreader.spreadAll(blockstate2, level, blockpos, spreader.isWorldGeneration());
                    Direction direction1 = direction.getOpposite();

                    for (Direction direction2 : DIRECTIONS) {
                        if (direction2 != direction1) {
                            BlockPos blockpos1 = blockpos.relative(direction2);
                            BlockState blockstate3 = level.getBlockState(blockpos1);
                            if (blockstate3.is(this)) {
                                this.onDischarged(level, blockstate3, blockpos1, random);
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    public static boolean hasSubstrateAccess(LevelAccessor level, BlockState state, BlockPos pos) {
        if (!state.is(Blocks.SCULK_VEIN)) {
            return false;
        } else {
            for (Direction direction : DIRECTIONS) {
                if (hasFace(state, direction) && level.getBlockState(pos.relative(direction)).is(BlockTags.SCULK_REPLACEABLE)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(
        BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return !useContext.getItemInHand().is(Items.SCULK_VEIN) || super.canBeReplaced(state, useContext);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    class SculkVeinSpreaderConfig extends MultifaceSpreader.DefaultSpreaderConfig {
        private final MultifaceSpreader.SpreadType[] spreadTypes;

        public SculkVeinSpreaderConfig(MultifaceSpreader.SpreadType... spreadTypes) {
            super(SculkVeinBlock.this);
            this.spreadTypes = spreadTypes;
        }

        @Override
        public boolean stateCanBeReplaced(BlockGetter level, BlockPos pos, BlockPos spreadPos, Direction direction, BlockState state) {
            BlockState blockstate = level.getBlockState(spreadPos.relative(direction));
            if (!blockstate.is(Blocks.SCULK) && !blockstate.is(Blocks.SCULK_CATALYST) && !blockstate.is(Blocks.MOVING_PISTON)) {
                if (pos.distManhattan(spreadPos) == 2) {
                    BlockPos blockpos = pos.relative(direction.getOpposite());
                    if (level.getBlockState(blockpos).isFaceSturdy(level, blockpos, direction)) {
                        return false;
                    }
                }

                FluidState fluidstate = state.getFluidState();
                if (!fluidstate.isEmpty() && !fluidstate.is(Fluids.WATER)) {
                    return false;
                } else {
                    return state.is(BlockTags.FIRE)
                        ? false
                        : state.canBeReplaced() || super.stateCanBeReplaced(level, pos, spreadPos, direction, state);
                }
            } else {
                return false;
            }
        }

        @Override
        public MultifaceSpreader.SpreadType[] getSpreadTypes() {
            return this.spreadTypes;
        }

        @Override
        public boolean isOtherBlockValidAsSource(BlockState otherBlock) {
            return !otherBlock.is(Blocks.SCULK_VEIN);
        }
    }
}
