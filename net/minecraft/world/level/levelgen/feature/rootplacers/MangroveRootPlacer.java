package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class MangroveRootPlacer extends RootPlacer {
    public static final int ROOT_WIDTH_LIMIT = 8;
    public static final int ROOT_LENGTH_LIMIT = 15;
    public static final MapCodec<MangroveRootPlacer> CODEC = RecordCodecBuilder.mapCodec(
        p_225856_ -> rootPlacerParts(p_225856_)
                .and(MangroveRootPlacement.CODEC.fieldOf("mangrove_root_placement").forGetter(p_225849_ -> p_225849_.mangroveRootPlacement))
                .apply(p_225856_, MangroveRootPlacer::new)
    );
    private final MangroveRootPlacement mangroveRootPlacement;

    public MangroveRootPlacer(IntProvider trunkOffset, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement, MangroveRootPlacement mangroveRootPlacement) {
        super(trunkOffset, rootProvider, aboveRootPlacement);
        this.mangroveRootPlacement = mangroveRootPlacement;
    }

    @Override
    public boolean placeRoots(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos pos,
        BlockPos trunkOrigin,
        TreeConfiguration treeConfig
    ) {
        List<BlockPos> list = Lists.newArrayList();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pos.mutable();

        while (blockpos$mutableblockpos.getY() < trunkOrigin.getY()) {
            if (!this.canPlaceRoot(level, blockpos$mutableblockpos)) {
                return false;
            }

            blockpos$mutableblockpos.move(Direction.UP);
        }

        list.add(trunkOrigin.below());

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = trunkOrigin.relative(direction);
            List<BlockPos> list1 = Lists.newArrayList();
            if (!this.simulateRoots(level, random, blockpos, direction, trunkOrigin, list1, 0)) {
                return false;
            }

            list.addAll(list1);
            list.add(trunkOrigin.relative(direction));
        }

        for (BlockPos blockpos1 : list) {
            this.placeRoot(level, blockSetter, random, blockpos1, treeConfig);
        }

        return true;
    }

    private boolean simulateRoots(
        LevelSimulatedReader level,
        RandomSource random,
        BlockPos pos,
        Direction direction,
        BlockPos trunkOrigin,
        List<BlockPos> roots,
        int length
    ) {
        int i = this.mangroveRootPlacement.maxRootLength();
        if (length != i && roots.size() <= i) {
            for (BlockPos blockpos : this.potentialRootPositions(pos, direction, random, trunkOrigin)) {
                if (this.canPlaceRoot(level, blockpos)) {
                    roots.add(blockpos);
                    if (!this.simulateRoots(level, random, blockpos, direction, trunkOrigin, roots, length + 1)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    protected List<BlockPos> potentialRootPositions(BlockPos pos, Direction direction, RandomSource random, BlockPos trunkOrigin) {
        BlockPos blockpos = pos.below();
        BlockPos blockpos1 = pos.relative(direction);
        int i = pos.distManhattan(trunkOrigin);
        int j = this.mangroveRootPlacement.maxRootWidth();
        float f = this.mangroveRootPlacement.randomSkewChance();
        if (i > j - 3 && i <= j) {
            return random.nextFloat() < f ? List.of(blockpos, blockpos1.below()) : List.of(blockpos);
        } else if (i > j) {
            return List.of(blockpos);
        } else if (random.nextFloat() < f) {
            return List.of(blockpos);
        } else {
            return random.nextBoolean() ? List.of(blockpos1) : List.of(blockpos);
        }
    }

    @Override
    protected boolean canPlaceRoot(LevelSimulatedReader level, BlockPos pos) {
        return super.canPlaceRoot(level, pos)
            || level.isStateAtPosition(pos, p_225858_ -> p_225858_.is(this.mangroveRootPlacement.canGrowThrough()));
    }

    @Override
    protected void placeRoot(
        LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, BlockPos pos, TreeConfiguration treeConfig
    ) {
        if (level.isStateAtPosition(pos, p_225847_ -> p_225847_.is(this.mangroveRootPlacement.muddyRootsIn()))) {
            BlockState blockstate = this.mangroveRootPlacement.muddyRootsProvider().getState(random, pos);
            blockSetter.accept(pos, this.getPotentiallyWaterloggedState(level, pos, blockstate));
        } else {
            super.placeRoot(level, blockSetter, random, pos, treeConfig);
        }
    }

    @Override
    protected RootPlacerType<?> type() {
        return RootPlacerType.MANGROVE_ROOT_PLACER;
    }
}
