package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLeavesDecorator extends TreeDecorator {
    public static final MapCodec<AttachedToLeavesDecorator> CODEC = RecordCodecBuilder.mapCodec(
        p_225996_ -> p_225996_.group(
                    Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(p_226014_ -> p_226014_.probability),
                    Codec.intRange(0, 16).fieldOf("exclusion_radius_xz").forGetter(p_226012_ -> p_226012_.exclusionRadiusXZ),
                    Codec.intRange(0, 16).fieldOf("exclusion_radius_y").forGetter(p_226010_ -> p_226010_.exclusionRadiusY),
                    BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(p_226008_ -> p_226008_.blockProvider),
                    Codec.intRange(1, 16).fieldOf("required_empty_blocks").forGetter(p_226006_ -> p_226006_.requiredEmptyBlocks),
                    ExtraCodecs.nonEmptyList(Direction.CODEC.listOf()).fieldOf("directions").forGetter(p_225998_ -> p_225998_.directions)
                )
                .apply(p_225996_, AttachedToLeavesDecorator::new)
    );
    protected final float probability;
    protected final int exclusionRadiusXZ;
    protected final int exclusionRadiusY;
    protected final BlockStateProvider blockProvider;
    protected final int requiredEmptyBlocks;
    protected final List<Direction> directions;

    public AttachedToLeavesDecorator(float probability, int exclusionRadiusXZ, int exclusionRadiusY, BlockStateProvider blockProvider, int requiredEmptyBlocks, List<Direction> directions) {
        this.probability = probability;
        this.exclusionRadiusXZ = exclusionRadiusXZ;
        this.exclusionRadiusY = exclusionRadiusY;
        this.blockProvider = blockProvider;
        this.requiredEmptyBlocks = requiredEmptyBlocks;
        this.directions = directions;
    }

    @Override
    public void place(TreeDecorator.Context context) {
        Set<BlockPos> set = new HashSet<>();
        RandomSource randomsource = context.random();

        for (BlockPos blockpos : Util.shuffledCopy(context.leaves(), randomsource)) {
            Direction direction = Util.getRandom(this.directions, randomsource);
            BlockPos blockpos1 = blockpos.relative(direction);
            if (!set.contains(blockpos1) && randomsource.nextFloat() < this.probability && this.hasRequiredEmptyBlocks(context, blockpos, direction)) {
                BlockPos blockpos2 = blockpos1.offset(-this.exclusionRadiusXZ, -this.exclusionRadiusY, -this.exclusionRadiusXZ);
                BlockPos blockpos3 = blockpos1.offset(this.exclusionRadiusXZ, this.exclusionRadiusY, this.exclusionRadiusXZ);

                for (BlockPos blockpos4 : BlockPos.betweenClosed(blockpos2, blockpos3)) {
                    set.add(blockpos4.immutable());
                }

                context.setBlock(blockpos1, this.blockProvider.getState(randomsource, blockpos1));
            }
        }
    }

    private boolean hasRequiredEmptyBlocks(TreeDecorator.Context context, BlockPos pos, Direction direction) {
        for (int i = 1; i <= this.requiredEmptyBlocks; i++) {
            BlockPos blockpos = pos.relative(direction, i);
            if (!context.isAir(blockpos)) {
                return false;
            }
        }

        return true;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return TreeDecoratorType.ATTACHED_TO_LEAVES;
    }
}
