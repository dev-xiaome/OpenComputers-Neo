package net.minecraft.world.level.levelgen.feature.stateproviders;

import com.mojang.datafixers.Products.P4;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class NoiseProvider extends NoiseBasedStateProvider {
    public static final MapCodec<NoiseProvider> CODEC = RecordCodecBuilder.mapCodec(
        p_191462_ -> noiseProviderCodec(p_191462_).apply(p_191462_, NoiseProvider::new)
    );
    protected final List<BlockState> states;

    protected static <P extends NoiseProvider> P4<Mu<P>, Long, NormalNoise.NoiseParameters, Float, List<BlockState>> noiseProviderCodec(Instance<P> instance) {
        return noiseCodec(instance).and(Codec.list(BlockState.CODEC).fieldOf("states").forGetter(p_191448_ -> p_191448_.states));
    }

    public NoiseProvider(long seed, NormalNoise.NoiseParameters parameters, float scale, List<BlockState> states) {
        super(seed, parameters, scale);
        this.states = states;
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return BlockStateProviderType.NOISE_PROVIDER;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        return this.getRandomState(this.states, pos, (double)this.scale);
    }

    protected BlockState getRandomState(List<BlockState> possibleStates, BlockPos pos, double delta) {
        double d0 = this.getNoiseValue(pos, delta);
        return this.getRandomState(possibleStates, d0);
    }

    protected BlockState getRandomState(List<BlockState> possibleStates, double delta) {
        double d0 = Mth.clamp((1.0 + delta) / 2.0, 0.0, 0.9999);
        return possibleStates.get((int)(d0 * (double)possibleStates.size()));
    }
}
