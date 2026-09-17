package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.material.FluidState;

public abstract class RootPlacer {
    public static final Codec<RootPlacer> CODEC = BuiltInRegistries.ROOT_PLACER_TYPE.byNameCodec().dispatch(RootPlacer::type, RootPlacerType::codec);
    protected final IntProvider trunkOffsetY;
    protected final BlockStateProvider rootProvider;
    protected final Optional<AboveRootPlacement> aboveRootPlacement;

    protected static <P extends RootPlacer> P3<Mu<P>, IntProvider, BlockStateProvider, Optional<AboveRootPlacement>> rootPlacerParts(Instance<P> instance) {
        return instance.group(
            IntProvider.CODEC.fieldOf("trunk_offset_y").forGetter(p_225897_ -> p_225897_.trunkOffsetY),
            BlockStateProvider.CODEC.fieldOf("root_provider").forGetter(p_225895_ -> p_225895_.rootProvider),
            AboveRootPlacement.CODEC.optionalFieldOf("above_root_placement").forGetter(p_225888_ -> p_225888_.aboveRootPlacement)
        );
    }

    public RootPlacer(IntProvider trunkOffset, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement) {
        this.trunkOffsetY = trunkOffset;
        this.rootProvider = rootProvider;
        this.aboveRootPlacement = aboveRootPlacement;
    }

    protected abstract RootPlacerType<?> type();

    public abstract boolean placeRoots(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos pos,
        BlockPos trunkOrigin,
        TreeConfiguration treeConfig
    );

    protected boolean canPlaceRoot(LevelSimulatedReader level, BlockPos pos) {
        return TreeFeature.validTreePos(level, pos);
    }

    protected void placeRoot(
        LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, BlockPos pos, TreeConfiguration treeConfig
    ) {
        if (this.canPlaceRoot(level, pos)) {
            blockSetter.accept(pos, this.getPotentiallyWaterloggedState(level, pos, this.rootProvider.getState(random, pos)));
            if (this.aboveRootPlacement.isPresent()) {
                AboveRootPlacement aboverootplacement = this.aboveRootPlacement.get();
                BlockPos blockpos = pos.above();
                if (random.nextFloat() < aboverootplacement.aboveRootPlacementChance()
                    && level.isStateAtPosition(blockpos, BlockBehaviour.BlockStateBase::isAir)) {
                    blockSetter.accept(
                        blockpos,
                        this.getPotentiallyWaterloggedState(level, blockpos, aboverootplacement.aboveRootProvider().getState(random, blockpos))
                    );
                }
            }
        }
    }

    protected BlockState getPotentiallyWaterloggedState(LevelSimulatedReader level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            boolean flag = level.isFluidAtPosition(pos, p_225890_ -> p_225890_.is(FluidTags.WATER));
            return state.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(flag));
        } else {
            return state;
        }
    }

    public BlockPos getTrunkOrigin(BlockPos pos, RandomSource random) {
        return pos.above(this.trunkOffsetY.sample(random));
    }
}
