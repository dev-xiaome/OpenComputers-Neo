package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class KeyValueCondition implements Condition {
    private static final Splitter PIPE_SPLITTER = Splitter.on('|').omitEmptyStrings();
    private final String key;
    private final String value;

    public KeyValueCondition(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public Predicate<BlockState> getPredicate(StateDefinition<Block, BlockState> definition) {
        Property<?> property = definition.getProperty(this.key);
        if (property == null) {
            throw new RuntimeException(String.format(Locale.ROOT, "Unknown property '%s' on '%s'", this.key, definition.getOwner()));
        } else {
            String s = this.value;
            boolean flag = !s.isEmpty() && s.charAt(0) == '!';
            if (flag) {
                s = s.substring(1);
            }

            List<String> list = PIPE_SPLITTER.splitToList(s);
            if (list.isEmpty()) {
                throw new RuntimeException(String.format(Locale.ROOT, "Empty value '%s' for property '%s' on '%s'", this.value, this.key, definition.getOwner()));
            } else {
                Predicate<BlockState> predicate;
                if (list.size() == 1) {
                    predicate = this.getBlockStatePredicate(definition, property, s);
                } else {
                    List<Predicate<BlockState>> list1 = list.stream()
                        .map(p_111958_ -> this.getBlockStatePredicate(definition, property, p_111958_))
                        .collect(Collectors.toList());
                    predicate = p_111954_ -> list1.stream().anyMatch(p_173509_ -> p_173509_.test(p_111954_));
                }

                return flag ? predicate.negate() : predicate;
            }
        }
    }

    private Predicate<BlockState> getBlockStatePredicate(StateDefinition<Block, BlockState> definition, Property<?> property, String value) {
        Optional<?> optional = property.getValue(value);
        if (optional.isEmpty()) {
            throw new RuntimeException(
                String.format(Locale.ROOT, "Unknown value '%s' for property '%s' on '%s' in '%s'", value, this.key, definition.getOwner(), this.value)
            );
        } else {
            return p_339295_ -> p_339295_.getValue(property).equals(optional.get());
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("key", this.key).add("value", this.value).toString();
    }
}
