package net.minecraft.world.level.levelgen.feature.configurations;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

public class OreConfiguration implements FeatureConfiguration {
    public static final Codec<OreConfiguration> CODEC = RecordCodecBuilder.create(
        p_67849_ -> p_67849_.group(
                    Codec.list(OreConfiguration.TargetBlockState.CODEC).fieldOf("targets").forGetter(p_161027_ -> p_161027_.targetStates),
                    Codec.intRange(0, 64).fieldOf("size").forGetter(p_161025_ -> p_161025_.size),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("discard_chance_on_air_exposure").forGetter(p_161020_ -> p_161020_.discardChanceOnAirExposure)
                )
                .apply(p_67849_, OreConfiguration::new)
    );
    public final List<OreConfiguration.TargetBlockState> targetStates;
    public final int size;
    public final float discardChanceOnAirExposure;

    public OreConfiguration(List<OreConfiguration.TargetBlockState> targetStates, int size, float discardChanceOnAirExposure) {
        this.size = size;
        this.targetStates = targetStates;
        this.discardChanceOnAirExposure = discardChanceOnAirExposure;
    }

    public OreConfiguration(List<OreConfiguration.TargetBlockState> targetStates, int size) {
        this(targetStates, size, 0.0F);
    }

    public OreConfiguration(RuleTest target, BlockState state, int size, float discardChanceOnAirExposure) {
        this(ImmutableList.of(new OreConfiguration.TargetBlockState(target, state)), size, discardChanceOnAirExposure);
    }

    public OreConfiguration(RuleTest target, BlockState state, int size) {
        this(ImmutableList.of(new OreConfiguration.TargetBlockState(target, state)), size, 0.0F);
    }

    public static OreConfiguration.TargetBlockState target(RuleTest target, BlockState state) {
        return new OreConfiguration.TargetBlockState(target, state);
    }

    public static class TargetBlockState {
        public static final Codec<OreConfiguration.TargetBlockState> CODEC = RecordCodecBuilder.create(
            p_161039_ -> p_161039_.group(
                        RuleTest.CODEC.fieldOf("target").forGetter(p_161043_ -> p_161043_.target),
                        BlockState.CODEC.fieldOf("state").forGetter(p_161041_ -> p_161041_.state)
                    )
                    .apply(p_161039_, OreConfiguration.TargetBlockState::new)
        );
        public final RuleTest target;
        public final BlockState state;

        TargetBlockState(RuleTest target, BlockState state) {
            this.target = target;
            this.state = state;
        }
    }
}
