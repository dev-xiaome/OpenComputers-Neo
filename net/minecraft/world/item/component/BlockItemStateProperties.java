package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

public record BlockItemStateProperties(Map<String, String> properties) {
    public static final BlockItemStateProperties EMPTY = new BlockItemStateProperties(Map.of());
    public static final Codec<BlockItemStateProperties> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
        .xmap(BlockItemStateProperties::new, BlockItemStateProperties::properties);
    private static final StreamCodec<ByteBuf, Map<String, String>> PROPERTIES_STREAM_CODEC = ByteBufCodecs.map(
        Object2ObjectOpenHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8
    );
    public static final StreamCodec<ByteBuf, BlockItemStateProperties> STREAM_CODEC = PROPERTIES_STREAM_CODEC.map(
        BlockItemStateProperties::new, BlockItemStateProperties::properties
    );

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, T value) {
        return new BlockItemStateProperties(Util.copyAndPut(this.properties, property.getName(), property.getName(value)));
    }

    public <T extends Comparable<T>> BlockItemStateProperties with(Property<T> property, BlockState state) {
        return this.with(property, state.getValue(property));
    }

    @Nullable
    public <T extends Comparable<T>> T get(Property<T> property) {
        String s = this.properties.get(property.getName());
        return s == null ? null : property.getValue(s).orElse(null);
    }

    public BlockState apply(BlockState state) {
        StateDefinition<Block, BlockState> statedefinition = state.getBlock().getStateDefinition();

        for (Entry<String, String> entry : this.properties.entrySet()) {
            Property<?> property = statedefinition.getProperty(entry.getKey());
            if (property != null) {
                state = updateState(state, property, entry.getValue());
            }
        }

        return state;
    }

    private static <T extends Comparable<T>> BlockState updateState(BlockState state, Property<T> property, String propertyName) {
        return property.getValue(propertyName).map(p_330629_ -> state.setValue(property, p_330629_)).orElse(state);
    }

    public boolean isEmpty() {
        return this.properties.isEmpty();
    }
}
