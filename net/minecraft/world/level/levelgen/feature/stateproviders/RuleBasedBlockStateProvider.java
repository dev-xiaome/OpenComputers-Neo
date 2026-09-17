package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

public record RuleBasedBlockStateProvider(BlockStateProvider fallback, List<RuleBasedBlockStateProvider.Rule> rules) {
    public static final Codec<RuleBasedBlockStateProvider> CODEC = RecordCodecBuilder.create(
        p_225939_ -> p_225939_.group(
                    BlockStateProvider.CODEC.fieldOf("fallback").forGetter(RuleBasedBlockStateProvider::fallback),
                    RuleBasedBlockStateProvider.Rule.CODEC.listOf().fieldOf("rules").forGetter(RuleBasedBlockStateProvider::rules)
                )
                .apply(p_225939_, RuleBasedBlockStateProvider::new)
    );

    public static RuleBasedBlockStateProvider simple(BlockStateProvider fallback) {
        return new RuleBasedBlockStateProvider(fallback, List.of());
    }

    public static RuleBasedBlockStateProvider simple(Block block) {
        return simple(BlockStateProvider.simple(block));
    }

    public BlockState getState(WorldGenLevel level, RandomSource random, BlockPos pos) {
        for (RuleBasedBlockStateProvider.Rule rulebasedblockstateprovider$rule : this.rules) {
            if (rulebasedblockstateprovider$rule.ifTrue().test(level, pos)) {
                return rulebasedblockstateprovider$rule.then().getState(random, pos);
            }
        }

        return this.fallback.getState(random, pos);
    }

    public static record Rule(BlockPredicate ifTrue, BlockStateProvider then) {
        public static final Codec<RuleBasedBlockStateProvider.Rule> CODEC = RecordCodecBuilder.create(
            p_225956_ -> p_225956_.group(
                        BlockPredicate.CODEC.fieldOf("if_true").forGetter(RuleBasedBlockStateProvider.Rule::ifTrue),
                        BlockStateProvider.CODEC.fieldOf("then").forGetter(RuleBasedBlockStateProvider.Rule::then)
                    )
                    .apply(p_225956_, RuleBasedBlockStateProvider.Rule::new)
        );
    }
}
