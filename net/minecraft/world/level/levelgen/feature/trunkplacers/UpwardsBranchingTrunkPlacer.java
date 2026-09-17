package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class UpwardsBranchingTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<UpwardsBranchingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        p_259008_ -> trunkPlacerParts(p_259008_)
                .and(
                    p_259008_.group(
                        IntProvider.POSITIVE_CODEC.fieldOf("extra_branch_steps").forGetter(p_226242_ -> p_226242_.extraBranchSteps),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("place_branch_per_log_probability").forGetter(p_226240_ -> p_226240_.placeBranchPerLogProbability),
                        IntProvider.NON_NEGATIVE_CODEC.fieldOf("extra_branch_length").forGetter(p_226238_ -> p_226238_.extraBranchLength),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("can_grow_through").forGetter(p_226234_ -> p_226234_.canGrowThrough)
                    )
                )
                .apply(p_259008_, UpwardsBranchingTrunkPlacer::new)
    );
    private final IntProvider extraBranchSteps;
    private final float placeBranchPerLogProbability;
    private final IntProvider extraBranchLength;
    private final HolderSet<Block> canGrowThrough;

    public UpwardsBranchingTrunkPlacer(
        int baseHeight, int heightRandA, int heightRandB, IntProvider extraBranchSteps, float placeBranchPerLogProbability, IntProvider extraBranchLength, HolderSet<Block> canGrowThrough
    ) {
        super(baseHeight, heightRandA, heightRandB);
        this.extraBranchSteps = extraBranchSteps;
        this.placeBranchPerLogProbability = placeBranchPerLogProbability;
        this.extraBranchLength = extraBranchLength;
        this.canGrowThrough = canGrowThrough;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.UPWARDS_BRANCHING_TRUNK_PLACER;
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
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < freeTreeHeight; i++) {
            int j = pos.getY() + i;
            if (this.placeLog(level, blockSetter, random, blockpos$mutableblockpos.set(pos.getX(), j, pos.getZ()), config)
                && i < freeTreeHeight - 1
                && random.nextFloat() < this.placeBranchPerLogProbability) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int k = this.extraBranchLength.sample(random);
                int l = Math.max(0, k - this.extraBranchLength.sample(random) - 1);
                int i1 = this.extraBranchSteps.sample(random);
                this.placeBranch(level, blockSetter, random, freeTreeHeight, config, list, blockpos$mutableblockpos, j, direction, l, i1);
            }

            if (i == freeTreeHeight - 1) {
                list.add(new FoliagePlacer.FoliageAttachment(blockpos$mutableblockpos.set(pos.getX(), j + 1, pos.getZ()), 0, false));
            }
        }

        return list;
    }

    private void placeBranch(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        TreeConfiguration treeConfig,
        List<FoliagePlacer.FoliageAttachment> foliageAttachments,
        BlockPos.MutableBlockPos pos,
        int y,
        Direction direction,
        int extraBranchLength,
        int extraBranchSteps
    ) {
        int i = y + extraBranchLength;
        int j = pos.getX();
        int k = pos.getZ();
        int l = extraBranchLength;

        while (l < freeTreeHeight && extraBranchSteps > 0) {
            if (l >= 1) {
                int i1 = y + l;
                j += direction.getStepX();
                k += direction.getStepZ();
                i = i1;
                if (this.placeLog(level, blockSetter, random, pos.set(j, i1, k), treeConfig)) {
                    i = i1 + 1;
                }

                foliageAttachments.add(new FoliagePlacer.FoliageAttachment(pos.immutable(), 0, false));
            }

            l++;
            extraBranchSteps--;
        }

        if (i - y > 1) {
            BlockPos blockpos = new BlockPos(j, i, k);
            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(blockpos, 0, false));
            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(blockpos.below(2), 0, false));
        }
    }

    @Override
    protected boolean validTreePos(LevelSimulatedReader level, BlockPos pos) {
        return super.validTreePos(level, pos) || level.isStateAtPosition(pos, p_226232_ -> p_226232_.is(this.canGrowThrough));
    }
}
