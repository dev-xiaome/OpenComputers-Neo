package net.minecraft.client.renderer.block.model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockModelDefinition {
    private final Map<String, MultiVariant> variants = Maps.newLinkedHashMap();
    private MultiPart multiPart;

    public static BlockModelDefinition fromStream(BlockModelDefinition.Context context, Reader reader) {
        return GsonHelper.fromJson(context.gson, reader, BlockModelDefinition.class);
    }

    public static BlockModelDefinition fromJsonElement(BlockModelDefinition.Context context, JsonElement json) {
        return context.gson.fromJson(json, BlockModelDefinition.class);
    }

    public BlockModelDefinition(Map<String, MultiVariant> variants, MultiPart multiPart) {
        this.multiPart = multiPart;
        this.variants.putAll(variants);
    }

    public BlockModelDefinition(List<BlockModelDefinition> modelDefinitions) {
        BlockModelDefinition blockmodeldefinition = null;

        for (BlockModelDefinition blockmodeldefinition1 : modelDefinitions) {
            if (blockmodeldefinition1.isMultiPart()) {
                this.variants.clear();
                blockmodeldefinition = blockmodeldefinition1;
            }

            this.variants.putAll(blockmodeldefinition1.variants);
        }

        if (blockmodeldefinition != null) {
            this.multiPart = blockmodeldefinition.multiPart;
        }
    }

    @VisibleForTesting
    public boolean hasVariant(String key) {
        return this.variants.get(key) != null;
    }

    @VisibleForTesting
    public MultiVariant getVariant(String key) {
        MultiVariant multivariant = this.variants.get(key);
        if (multivariant == null) {
            throw new BlockModelDefinition.MissingVariantException();
        } else {
            return multivariant;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            if (other instanceof BlockModelDefinition blockmodeldefinition && this.variants.equals(blockmodeldefinition.variants)) {
                return this.isMultiPart() ? this.multiPart.equals(blockmodeldefinition.multiPart) : !blockmodeldefinition.isMultiPart();
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return 31 * this.variants.hashCode() + (this.isMultiPart() ? this.multiPart.hashCode() : 0);
    }

    public Map<String, MultiVariant> getVariants() {
        return this.variants;
    }

    @VisibleForTesting
    public Set<MultiVariant> getMultiVariants() {
        Set<MultiVariant> set = Sets.newHashSet(this.variants.values());
        if (this.isMultiPart()) {
            set.addAll(this.multiPart.getMultiVariants());
        }

        return set;
    }

    public boolean isMultiPart() {
        return this.multiPart != null;
    }

    public MultiPart getMultiPart() {
        return this.multiPart;
    }

    @OnlyIn(Dist.CLIENT)
    public static final class Context {
        protected final Gson gson = new GsonBuilder()
            .registerTypeAdapter(BlockModelDefinition.class, new BlockModelDefinition.Deserializer())
            .registerTypeAdapter(Variant.class, new Variant.Deserializer())
            .registerTypeAdapter(MultiVariant.class, new MultiVariant.Deserializer())
            .registerTypeAdapter(MultiPart.class, new MultiPart.Deserializer(this))
            .registerTypeAdapter(Selector.class, new Selector.Deserializer())
            .create();
        private StateDefinition<Block, BlockState> definition;

        public StateDefinition<Block, BlockState> getDefinition() {
            return this.definition;
        }

        public void setDefinition(StateDefinition<Block, BlockState> stateContainer) {
            this.definition = stateContainer;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Deserializer implements JsonDeserializer<BlockModelDefinition> {
        public BlockModelDefinition deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonobject = json.getAsJsonObject();
            Map<String, MultiVariant> map = this.getVariants(context, jsonobject);
            MultiPart multipart = this.getMultiPart(context, jsonobject);
            if (!map.isEmpty() || multipart != null && !multipart.getMultiVariants().isEmpty()) {
                return new BlockModelDefinition(map, multipart);
            } else {
                throw new JsonParseException("Neither 'variants' nor 'multipart' found");
            }
        }

        protected Map<String, MultiVariant> getVariants(JsonDeserializationContext context, JsonObject json) {
            Map<String, MultiVariant> map = Maps.newHashMap();
            if (json.has("variants")) {
                JsonObject jsonobject = GsonHelper.getAsJsonObject(json, "variants");

                for (Entry<String, JsonElement> entry : jsonobject.entrySet()) {
                    map.put(entry.getKey(), context.deserialize(entry.getValue(), MultiVariant.class));
                }
            }

            return map;
        }

        @Nullable
        protected MultiPart getMultiPart(JsonDeserializationContext context, JsonObject json) {
            if (!json.has("multipart")) {
                return null;
            } else {
                JsonArray jsonarray = GsonHelper.getAsJsonArray(json, "multipart");
                return context.deserialize(jsonarray, MultiPart.class);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected class MissingVariantException extends RuntimeException {
    }
}
