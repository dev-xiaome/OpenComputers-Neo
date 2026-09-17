package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record Tool(List<Tool.Rule> rules, float defaultMiningSpeed, int damagePerBlock) {
    public static final Codec<Tool> CODEC = RecordCodecBuilder.create(
        p_337953_ -> p_337953_.group(
                    Tool.Rule.CODEC.listOf().fieldOf("rules").forGetter(Tool::rules),
                    Codec.FLOAT.optionalFieldOf("default_mining_speed", Float.valueOf(1.0F)).forGetter(Tool::defaultMiningSpeed),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("damage_per_block", 1).forGetter(Tool::damagePerBlock)
                )
                .apply(p_337953_, Tool::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, Tool> STREAM_CODEC = StreamCodec.composite(
        Tool.Rule.STREAM_CODEC.apply(ByteBufCodecs.list()),
        Tool::rules,
        ByteBufCodecs.FLOAT,
        Tool::defaultMiningSpeed,
        ByteBufCodecs.VAR_INT,
        Tool::damagePerBlock,
        Tool::new
    );

    public float getMiningSpeed(BlockState state) {
        for (Tool.Rule tool$rule : this.rules) {
            if (tool$rule.speed.isPresent() && state.is(tool$rule.blocks)) {
                return tool$rule.speed.get();
            }
        }

        return this.defaultMiningSpeed;
    }

    public boolean isCorrectForDrops(BlockState state) {
        for (Tool.Rule tool$rule : this.rules) {
            if (tool$rule.correctForDrops.isPresent() && state.is(tool$rule.blocks)) {
                return tool$rule.correctForDrops.get();
            }
        }

        return false;
    }

    public static record Rule(HolderSet<Block> blocks, Optional<Float> speed, Optional<Boolean> correctForDrops) {
        public static final Codec<Tool.Rule> CODEC = RecordCodecBuilder.create(
            p_337954_ -> p_337954_.group(
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("blocks").forGetter(Tool.Rule::blocks),
                        ExtraCodecs.POSITIVE_FLOAT.optionalFieldOf("speed").forGetter(Tool.Rule::speed),
                        Codec.BOOL.optionalFieldOf("correct_for_drops").forGetter(Tool.Rule::correctForDrops)
                    )
                    .apply(p_337954_, Tool.Rule::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, Tool.Rule> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderSet(Registries.BLOCK),
            Tool.Rule::blocks,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs::optional),
            Tool.Rule::speed,
            ByteBufCodecs.BOOL.apply(ByteBufCodecs::optional),
            Tool.Rule::correctForDrops,
            Tool.Rule::new
        );

        public static Tool.Rule minesAndDrops(List<Block> blocks, float speed) {
            return forBlocks(blocks, Optional.of(speed), Optional.of(true));
        }

        public static Tool.Rule minesAndDrops(TagKey<Block> blocks, float speed) {
            return forTag(blocks, Optional.of(speed), Optional.of(true));
        }

        public static Tool.Rule deniesDrops(TagKey<Block> blocks) {
            return forTag(blocks, Optional.empty(), Optional.of(false));
        }

        public static Tool.Rule overrideSpeed(TagKey<Block> blocks, float speed) {
            return forTag(blocks, Optional.of(speed), Optional.empty());
        }

        public static Tool.Rule overrideSpeed(List<Block> blocks, float speed) {
            return forBlocks(blocks, Optional.of(speed), Optional.empty());
        }

        private static Tool.Rule forTag(TagKey<Block> tag, Optional<Float> speed, Optional<Boolean> correctForDrops) {
            return new Tool.Rule(BuiltInRegistries.BLOCK.getOrCreateTag(tag), speed, correctForDrops);
        }

        private static Tool.Rule forBlocks(List<Block> blocks, Optional<Float> speed, Optional<Boolean> correctForDrops) {
            return new Tool.Rule(HolderSet.direct(blocks.stream().map(Block::builtInRegistryHolder).collect(Collectors.toList())), speed, correctForDrops);
        }
    }
}
