package net.minecraft.core.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class BlockParticleOption implements ParticleOptions {
    private static final Codec<BlockState> BLOCK_STATE_CODEC = Codec.withAlternative(
        BlockState.CODEC, BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState
    );
    private final ParticleType<BlockParticleOption> type;
    private final BlockState state;

    public static MapCodec<BlockParticleOption> codec(ParticleType<BlockParticleOption> particleType) {
        return BLOCK_STATE_CODEC.xmap(p_123638_ -> new BlockParticleOption(particleType, p_123638_), p_123633_ -> p_123633_.state).fieldOf("block_state");
    }

    public static StreamCodec<? super RegistryFriendlyByteBuf, BlockParticleOption> streamCodec(ParticleType<BlockParticleOption> particleType) {
        return ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).map(p_319424_ -> new BlockParticleOption(particleType, p_319424_), p_319425_ -> p_319425_.state);
    }

    public BlockParticleOption(ParticleType<BlockParticleOption> type, BlockState state) {
        this.type = type;
        this.state = state;
    }

    @Override
    public ParticleType<BlockParticleOption> getType() {
        return this.type;
    }

    public BlockState getState() {
        return this.state;
    }

    //FORGE: Add a source pos property, so we can provide models with additional model data
    private net.minecraft.core.BlockPos pos;
    public BlockParticleOption setPos(net.minecraft.core.BlockPos pos) {
        this.pos = pos;
        return this;
    }

    public net.minecraft.core.BlockPos getPos() {
        return pos;
    }
}
