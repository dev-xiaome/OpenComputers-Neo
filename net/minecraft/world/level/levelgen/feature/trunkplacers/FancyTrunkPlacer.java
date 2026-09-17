package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class FancyTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<FancyTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        p_70136_ -> trunkPlacerParts(p_70136_).apply(p_70136_, FancyTrunkPlacer::new)
    );
    private static final double TRUNK_HEIGHT_SCALE = 0.618;
    private static final double CLUSTER_DENSITY_MAGIC = 1.382;
    private static final double BRANCH_SLOPE = 0.381;
    private static final double BRANCH_LENGTH_MAGIC = 0.328;

    public FancyTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FANCY_TRUNK_PLACER;
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
        int i = 5;
        int j = freeTreeHeight + 2;
        int k = Mth.floor((double)j * 0.618);
        setDirtAt(level, blockSetter, random, pos.below(), config);
        double d0 = 1.0;
        int l = Math.min(1, Mth.floor(1.382 + Math.pow(1.0 * (double)j / 13.0, 2.0)));
        int i1 = pos.getY() + k;
        int j1 = j - 5;
        List<FancyTrunkPlacer.FoliageCoords> list = Lists.newArrayList();
        list.add(new FancyTrunkPlacer.FoliageCoords(pos.above(j1), i1));

        for (; j1 >= 0; j1--) {
            float f = treeShape(j, j1);
            if (!(f < 0.0F)) {
                for (int k1 = 0; k1 < l; k1++) {
                    double d1 = 1.0;
                    double d2 = 1.0 * (double)f * ((double)random.nextFloat() + 0.328);
                    double d3 = (double)(random.nextFloat() * 2.0F) * Math.PI;
                    double d4 = d2 * Math.sin(d3) + 0.5;
                    double d5 = d2 * Math.cos(d3) + 0.5;
                    BlockPos blockpos = pos.offset(Mth.floor(d4), j1 - 1, Mth.floor(d5));
                    BlockPos blockpos1 = blockpos.above(5);
                    if (this.makeLimb(level, blockSetter, random, blockpos, blockpos1, false, config)) {
                        int l1 = pos.getX() - blockpos.getX();
                        int i2 = pos.getZ() - blockpos.getZ();
                        double d6 = (double)blockpos.getY() - Math.sqrt((double)(l1 * l1 + i2 * i2)) * 0.381;
                        int j2 = d6 > (double)i1 ? i1 : (int)d6;
                        BlockPos blockpos2 = new BlockPos(pos.getX(), j2, pos.getZ());
                        if (this.makeLimb(level, blockSetter, random, blockpos2, blockpos, false, config)) {
                            list.add(new FancyTrunkPlacer.FoliageCoords(blockpos, blockpos2.getY()));
                        }
                    }
                }
            }
        }

        this.makeLimb(level, blockSetter, random, pos, pos.above(k), true, config);
        this.makeBranches(level, blockSetter, random, j, pos, list, config);
        List<FoliagePlacer.FoliageAttachment> list1 = Lists.newArrayList();

        for (FancyTrunkPlacer.FoliageCoords fancytrunkplacer$foliagecoords : list) {
            if (this.trimBranches(j, fancytrunkplacer$foliagecoords.getBranchBase() - pos.getY())) {
                list1.add(fancytrunkplacer$foliagecoords.attachment);
            }
        }

        return list1;
    }

    private boolean makeLimb(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos basePos,
        BlockPos offsetPos,
        boolean modifyWorld,
        TreeConfiguration config
    ) {
        if (!modifyWorld && Objects.equals(basePos, offsetPos)) {
            return true;
        } else {
            BlockPos blockpos = offsetPos.offset(-basePos.getX(), -basePos.getY(), -basePos.getZ());
            int i = this.getSteps(blockpos);
            float f = (float)blockpos.getX() / (float)i;
            float f1 = (float)blockpos.getY() / (float)i;
            float f2 = (float)blockpos.getZ() / (float)i;

            for (int j = 0; j <= i; j++) {
                BlockPos blockpos1 = basePos.offset(Mth.floor(0.5F + (float)j * f), Mth.floor(0.5F + (float)j * f1), Mth.floor(0.5F + (float)j * f2));
                if (modifyWorld) {
                    this.placeLog(
                        level,
                        blockSetter,
                        random,
                        blockpos1,
                        config,
                        p_161826_ -> p_161826_.trySetValue(RotatedPillarBlock.AXIS, this.getLogAxis(basePos, blockpos1))
                    );
                } else if (!this.isFree(level, blockpos1)) {
                    return false;
                }
            }

            return true;
        }
    }

    private int getSteps(BlockPos pos) {
        int i = Mth.abs(pos.getX());
        int j = Mth.abs(pos.getY());
        int k = Mth.abs(pos.getZ());
        return Math.max(i, Math.max(j, k));
    }

    private Direction.Axis getLogAxis(BlockPos pos, BlockPos otherPos) {
        Direction.Axis direction$axis = Direction.Axis.Y;
        int i = Math.abs(otherPos.getX() - pos.getX());
        int j = Math.abs(otherPos.getZ() - pos.getZ());
        int k = Math.max(i, j);
        if (k > 0) {
            if (i == k) {
                direction$axis = Direction.Axis.X;
            } else {
                direction$axis = Direction.Axis.Z;
            }
        }

        return direction$axis;
    }

    private boolean trimBranches(int maxHeight, int currentHeight) {
        return (double)currentHeight >= (double)maxHeight * 0.2;
    }

    private void makeBranches(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int maxHeight,
        BlockPos pos,
        List<FancyTrunkPlacer.FoliageCoords> foliageCoords,
        TreeConfiguration config
    ) {
        for (FancyTrunkPlacer.FoliageCoords fancytrunkplacer$foliagecoords : foliageCoords) {
            int i = fancytrunkplacer$foliagecoords.getBranchBase();
            BlockPos blockpos = new BlockPos(pos.getX(), i, pos.getZ());
            if (!blockpos.equals(fancytrunkplacer$foliagecoords.attachment.pos()) && this.trimBranches(maxHeight, i - pos.getY())) {
                this.makeLimb(level, blockSetter, random, blockpos, fancytrunkplacer$foliagecoords.attachment.pos(), true, config);
            }
        }
    }

    private static float treeShape(int height, int currentY) {
        if ((float)currentY < (float)height * 0.3F) {
            return -1.0F;
        } else {
            float f = (float)height / 2.0F;
            float f1 = f - (float)currentY;
            float f2 = Mth.sqrt(f * f - f1 * f1);
            if (f1 == 0.0F) {
                f2 = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0F;
            }

            return f2 * 0.5F;
        }
    }

    static class FoliageCoords {
        final FoliagePlacer.FoliageAttachment attachment;
        private final int branchBase;

        public FoliageCoords(BlockPos attachmentPos, int branchBase) {
            this.attachment = new FoliagePlacer.FoliageAttachment(attachmentPos, 0, false);
            this.branchBase = branchBase;
        }

        public int getBranchBase() {
            return this.branchBase;
        }
    }
}
