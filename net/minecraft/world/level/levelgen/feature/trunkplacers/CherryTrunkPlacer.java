package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class CherryTrunkPlacer extends TrunkPlacer {
    private static final Codec<UniformInt> BRANCH_START_CODEC = UniformInt.CODEC
        .codec()
        .validate(
            p_275181_ -> p_275181_.getMaxValue() - p_275181_.getMinValue() < 1
                    ? DataResult.error(() -> "Need at least 2 blocks variation for the branch starts to fit both branches")
                    : DataResult.success(p_275181_)
        );
    public static final MapCodec<CherryTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        p_338099_ -> trunkPlacerParts(p_338099_)
                .and(
                    p_338099_.group(
                        IntProvider.codec(1, 3).fieldOf("branch_count").forGetter(p_272644_ -> p_272644_.branchCount),
                        IntProvider.codec(2, 16).fieldOf("branch_horizontal_length").forGetter(p_273612_ -> p_273612_.branchHorizontalLength),
                        IntProvider.validateCodec(-16, 0, BRANCH_START_CODEC)
                            .fieldOf("branch_start_offset_from_top")
                            .forGetter(p_272705_ -> p_272705_.branchStartOffsetFromTop),
                        IntProvider.codec(-16, 16).fieldOf("branch_end_offset_from_top").forGetter(p_273633_ -> p_273633_.branchEndOffsetFromTop)
                    )
                )
                .apply(p_338099_, CherryTrunkPlacer::new)
    );
    private final IntProvider branchCount;
    private final IntProvider branchHorizontalLength;
    private final UniformInt branchStartOffsetFromTop;
    private final UniformInt secondBranchStartOffsetFromTop;
    private final IntProvider branchEndOffsetFromTop;

    public CherryTrunkPlacer(
        int baseHeight, int heightRandA, int heightRandB, IntProvider branchCount, IntProvider branchHorizontalLength, UniformInt branchStartOffsetFromTop, IntProvider branchEndOffsetFromTop
    ) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchCount = branchCount;
        this.branchHorizontalLength = branchHorizontalLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
        this.secondBranchStartOffsetFromTop = UniformInt.of(branchStartOffsetFromTop.getMinValue(), branchStartOffsetFromTop.getMaxValue() - 1);
        this.branchEndOffsetFromTop = branchEndOffsetFromTop;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.CHERRY_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        BlockPos pos,
        TreeConfiguration config
    ) {
        setDirtAt(level, blockSetter, random, pos.below(), config);
        int i = Math.max(0, freeTreeHeight - 1 + this.branchStartOffsetFromTop.sample(random));
        int j = Math.max(0, freeTreeHeight - 1 + this.secondBranchStartOffsetFromTop.sample(random));
        if (j >= i) {
            j++;
        }

        int k = this.branchCount.sample(random);
        boolean flag = k == 3;
        boolean flag1 = k >= 2;
        int l;
        if (flag) {
            l = freeTreeHeight;
        } else if (flag1) {
            l = Math.max(i, j) + 1;
        } else {
            l = i + 1;
        }

        for (int i1 = 0; i1 < l; i1++) {
            this.placeLog(level, blockSetter, random, pos.above(i1), config);
        }

        List<FoliagePlacer.FoliageAttachment> list = new ArrayList<>();
        if (flag) {
            list.add(new FoliagePlacer.FoliageAttachment(pos.above(l), 0, false));
        }

        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Function<BlockState, BlockState> function = p_273382_ -> p_273382_.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
        list.add(
            this.generateBranch(level, blockSetter, random, freeTreeHeight, pos, config, function, direction, i, i < l - 1, blockpos$mutableblockpos)
        );
        if (flag1) {
            list.add(
                this.generateBranch(
                    level, blockSetter, random, freeTreeHeight, pos, config, function, direction.getOpposite(), j, j < l - 1, blockpos$mutableblockpos
                )
            );
        }

        return list;
    }

    private FoliagePlacer.FoliageAttachment generateBranch(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        BlockPos pos,
        TreeConfiguration config,
        Function<BlockState, BlockState> propertySetter,
        Direction p_direction,
        int secondBranchStartOffsetFromTop,
        boolean doubleBranch,
        BlockPos.MutableBlockPos currentPos
    ) {
        currentPos.set(pos).move(Direction.UP, secondBranchStartOffsetFromTop);
        int i = freeTreeHeight - 1 + this.branchEndOffsetFromTop.sample(random);
        boolean flag = doubleBranch || i < secondBranchStartOffsetFromTop;
        int j = this.branchHorizontalLength.sample(random) + (flag ? 1 : 0);
        BlockPos blockpos = pos.relative(p_direction, j).above(i);
        int k = flag ? 2 : 1;

        for (int l = 0; l < k; l++) {
            this.placeLog(level, blockSetter, random, currentPos.move(p_direction), config, propertySetter);
        }

        Direction direction = blockpos.getY() > currentPos.getY() ? Direction.UP : Direction.DOWN;

        while (true) {
            int i1 = currentPos.distManhattan(blockpos);
            if (i1 == 0) {
                return new FoliagePlacer.FoliageAttachment(blockpos.above(), 0, false);
            }

            float f = (float)Math.abs(blockpos.getY() - currentPos.getY()) / (float)i1;
            boolean flag1 = random.nextFloat() < f;
            currentPos.move(flag1 ? direction : p_direction);
            this.placeLog(level, blockSetter, random, currentPos, config, flag1 ? Function.identity() : propertySetter);
        }
    }
}
