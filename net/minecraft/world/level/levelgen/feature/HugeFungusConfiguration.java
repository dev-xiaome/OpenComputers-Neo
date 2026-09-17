package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public class HugeFungusConfiguration implements FeatureConfiguration {
    public static final Codec<HugeFungusConfiguration> CODEC = RecordCodecBuilder.create(
        p_284922_ -> p_284922_.group(
                    BlockState.CODEC.fieldOf("valid_base_block").forGetter(p_159875_ -> p_159875_.validBaseState),
                    BlockState.CODEC.fieldOf("stem_state").forGetter(p_159873_ -> p_159873_.stemState),
                    BlockState.CODEC.fieldOf("hat_state").forGetter(p_159871_ -> p_159871_.hatState),
                    BlockState.CODEC.fieldOf("decor_state").forGetter(p_159869_ -> p_159869_.decorState),
                    BlockPredicate.CODEC.fieldOf("replaceable_blocks").forGetter(p_284923_ -> p_284923_.replaceableBlocks),
                    Codec.BOOL.fieldOf("planted").orElse(false).forGetter(p_159867_ -> p_159867_.planted)
                )
                .apply(p_284922_, HugeFungusConfiguration::new)
    );
    public final BlockState validBaseState;
    public final BlockState stemState;
    public final BlockState hatState;
    public final BlockState decorState;
    public final BlockPredicate replaceableBlocks;
    public final boolean planted;

    public HugeFungusConfiguration(
        BlockState validBaseState, BlockState stemState, BlockState hatState, BlockState decorState, BlockPredicate replaceableBlocks, boolean planted
    ) {
        this.validBaseState = validBaseState;
        this.stemState = stemState;
        this.hatState = hatState;
        this.decorState = decorState;
        this.replaceableBlocks = replaceableBlocks;
        this.planted = planted;
    }
}
