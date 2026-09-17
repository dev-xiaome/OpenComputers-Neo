package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Selector {
    private final Condition condition;
    private final MultiVariant variant;

    public Selector(Condition condition, MultiVariant variant) {
        if (condition == null) {
            throw new IllegalArgumentException("Missing condition for selector");
        } else if (variant == null) {
            throw new IllegalArgumentException("Missing variant for selector");
        } else {
            this.condition = condition;
            this.variant = variant;
        }
    }

    public MultiVariant getVariant() {
        return this.variant;
    }

    public Predicate<BlockState> getPredicate(StateDefinition<Block, BlockState> definition) {
        return this.condition.getPredicate(definition);
    }

    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Deserializer implements JsonDeserializer<Selector> {
        public Selector deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonobject = json.getAsJsonObject();
            return new Selector(this.getSelector(jsonobject), context.deserialize(jsonobject.get("apply"), MultiVariant.class));
        }

        private Condition getSelector(JsonObject json) {
            return json.has("when") ? getCondition(GsonHelper.getAsJsonObject(json, "when")) : Condition.TRUE;
        }

        @VisibleForTesting
        static Condition getCondition(JsonObject json) {
            Set<Entry<String, JsonElement>> set = json.entrySet();
            if (set.isEmpty()) {
                throw new JsonParseException("No elements found in selector");
            } else if (set.size() == 1) {
                if (json.has("OR")) {
                    List<Condition> list1 = Streams.stream(GsonHelper.getAsJsonArray(json, "OR"))
                        .map(p_112038_ -> getCondition(p_112038_.getAsJsonObject()))
                        .collect(Collectors.toList());
                    return new OrCondition(list1);
                } else if (json.has("AND")) {
                    List<Condition> list = Streams.stream(GsonHelper.getAsJsonArray(json, "AND"))
                        .map(p_112028_ -> getCondition(p_112028_.getAsJsonObject()))
                        .collect(Collectors.toList());
                    return new AndCondition(list);
                } else {
                    return getKeyValueCondition(set.iterator().next());
                }
            } else {
                return new AndCondition(set.stream().map(Selector.Deserializer::getKeyValueCondition).collect(Collectors.toList()));
            }
        }

        private static Condition getKeyValueCondition(Entry<String, JsonElement> entry) {
            return new KeyValueCondition(entry.getKey(), entry.getValue().getAsString());
        }
    }
}
