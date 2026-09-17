package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChorusFlowerBlock extends Block {
    public static final MapCodec<ChorusFlowerBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_344653_ -> p_344653_.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("plant").forGetter(p_304498_ -> p_304498_.plant), propertiesCodec())
                .apply(p_344653_, ChorusFlowerBlock::new)
    );
    public static final int DEAD_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    protected static final VoxelShape BLOCK_SUPPORT_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);
    private final Block plant;

    @Override
    public MapCodec<ChorusFlowerBlock> codec() {
        return CODEC;
    }

    public ChorusFlowerBlock(Block plant, BlockBehaviour.Properties properties) {
        super(properties);
        this.plant = plant;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, Integer.valueOf(0)));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    /**
     * Returns whether this block is of a type that needs random ticking. Called for ref-counting purposes by {@code ExtendedBlockStorage} in order to broadly cull a chunk from the random chunk update list for efficiency's sake.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 5;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return BLOCK_SUPPORT_SHAPE;
    }

    /**
     * Performs a random tick on a block.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockpos = pos.above();
        if (level.isEmptyBlock(blockpos) && blockpos.getY() < level.getMaxBuildHeight()) {
            int i = state.getValue(AGE);
            if (i < 5 && net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, blockpos, state, true)) {
                boolean flag = false;
                boolean flag1 = false;
                BlockState blockstate = level.getBlockState(pos.below());
                net.neoforged.neoforge.common.util.TriState soilDecision = blockstate.canSustainPlant(level, pos.below(), Direction.UP, state);
                if (!soilDecision.isDefault()) flag = soilDecision.isTrue();
                else
                if (blockstate.is(Blocks.END_STONE)) {
                    flag = true;
                } else if (blockstate.is(this.plant)) {
                    int j = 1;

                    for (int k = 0; k < 4; k++) {
                        BlockState blockstate1 = level.getBlockState(pos.below(j + 1));
                        if (!blockstate1.is(this.plant)) {
                            net.neoforged.neoforge.common.util.TriState soilDecision2 = blockstate1.canSustainPlant(level, pos.below(j + 1), Direction.UP, state);
                            if (!soilDecision2.isDefault()) flag1 = soilDecision2.isTrue();
                            if (blockstate1.is(Blocks.END_STONE)) {
                                flag1 = true;
                            }
                            break;
                        }

                        j++;
                    }

                    if (j < 2 || j <= random.nextInt(flag1 ? 5 : 4)) {
                        flag = true;
                    }
                } else if (blockstate.isAir()) {
                    flag = true;
                }

                if (flag && allNeighborsEmpty(level, blockpos, null) && level.isEmptyBlock(pos.above(2))) {
                    level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), 2);
                    this.placeGrownFlower(level, blockpos, i);
                } else if (i < 4) {
                    int l = random.nextInt(4);
                    if (flag1) {
                        l++;
                    }

                    boolean flag2 = false;

                    for (int i1 = 0; i1 < l; i1++) {
                        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos blockpos1 = pos.relative(direction);
                        if (level.isEmptyBlock(blockpos1)
                            && level.isEmptyBlock(blockpos1.below())
                            && allNeighborsEmpty(level, blockpos1, direction.getOpposite())) {
                            this.placeGrownFlower(level, blockpos1, i + 1);
                            flag2 = true;
                        }
                    }

                    if (flag2) {
                        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), 2);
                    } else {
                        this.placeDeadFlower(level, pos);
                    }
                } else {
                    this.placeDeadFlower(level, pos);
                }
                net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, pos, state);
            }
        }
    }

    private void placeGrownFlower(Level level, BlockPos pos, int age) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, Integer.valueOf(age)), 2);
        level.levelEvent(1033, pos, 0);
    }

    private void placeDeadFlower(Level level, BlockPos pos) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, Integer.valueOf(5)), 2);
        level.levelEvent(1034, pos, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction excludingSide) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != excludingSide && !level.isEmptyBlock(pos.relative(direction))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (facing != Direction.UP && !state.canSurvive(level, currentPos)) {
            level.scheduleTick(currentPos, this, 1);
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockstate = level.getBlockState(pos.below());
        net.neoforged.neoforge.common.util.TriState soilDecision = blockstate.canSustainPlant(level, pos.below(), Direction.UP, state);
        if (!soilDecision.isDefault()) return soilDecision.isTrue();
        if (!blockstate.is(this.plant) && !blockstate.is(Blocks.END_STONE)) {
            if (!blockstate.isAir()) {
                return false;
            } else {
                boolean flag = false;

                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockState blockstate1 = level.getBlockState(pos.relative(direction));
                    if (blockstate1.is(this.plant)) {
                        if (flag) {
                            return false;
                        }

                        flag = true;
                    } else if (!blockstate1.isAir()) {
                        return false;
                    }
                }

                return flag;
            }
        } else {
            return true;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    public static void generatePlant(LevelAccessor level, BlockPos pos, RandomSource random, int maxHorizontalDistance) {
        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, Blocks.CHORUS_PLANT.defaultBlockState()), 2);
        growTreeRecursive(level, pos, random, pos, maxHorizontalDistance, 0);
    }

    private static void growTreeRecursive(LevelAccessor level, BlockPos branchPos, RandomSource random, BlockPos originalBranchPos, int maxHorizontalDistance, int iterations) {
        Block block = Blocks.CHORUS_PLANT;
        int i = random.nextInt(4) + 1;
        if (iterations == 0) {
            i++;
        }

        for (int j = 0; j < i; j++) {
            BlockPos blockpos = branchPos.above(j + 1);
            if (!allNeighborsEmpty(level, blockpos, null)) {
                return;
            }

            level.setBlock(blockpos, ChorusPlantBlock.getStateWithConnections(level, blockpos, block.defaultBlockState()), 2);
            level.setBlock(blockpos.below(), ChorusPlantBlock.getStateWithConnections(level, blockpos.below(), block.defaultBlockState()), 2);
        }

        boolean flag = false;
        if (iterations < 4) {
            int l = random.nextInt(4);
            if (iterations == 0) {
                l++;
            }

            for (int k = 0; k < l; k++) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockpos1 = branchPos.above(i).relative(direction);
                if (Math.abs(blockpos1.getX() - originalBranchPos.getX()) < maxHorizontalDistance
                    && Math.abs(blockpos1.getZ() - originalBranchPos.getZ()) < maxHorizontalDistance
                    && level.isEmptyBlock(blockpos1)
                    && level.isEmptyBlock(blockpos1.below())
                    && allNeighborsEmpty(level, blockpos1, direction.getOpposite())) {
                    flag = true;
                    level.setBlock(blockpos1, ChorusPlantBlock.getStateWithConnections(level, blockpos1, block.defaultBlockState()), 2);
                    level.setBlock(
                        blockpos1.relative(direction.getOpposite()),
                        ChorusPlantBlock.getStateWithConnections(level, blockpos1.relative(direction.getOpposite()), block.defaultBlockState()),
                        2
                    );
                    growTreeRecursive(level, blockpos1, random, originalBranchPos, maxHorizontalDistance, iterations + 1);
                }
            }
        }

        if (!flag) {
            level.setBlock(branchPos.above(i), Blocks.CHORUS_FLOWER.defaultBlockState().setValue(AGE, Integer.valueOf(5)), 2);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockpos = hit.getBlockPos();
        if (!level.isClientSide && projectile.mayInteract(level, blockpos) && projectile.mayBreak(level)) {
            level.destroyBlock(blockpos, true, projectile);
        }
    }
}
