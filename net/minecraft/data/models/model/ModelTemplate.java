package net.minecraft.data.models.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

public class ModelTemplate {
    private final Optional<ResourceLocation> model;
    private final Set<TextureSlot> requiredSlots;
    private final Optional<String> suffix;

    public ModelTemplate(Optional<ResourceLocation> model, Optional<String> suffix, TextureSlot... requiredSlots) {
        this.model = model;
        this.suffix = suffix;
        this.requiredSlots = ImmutableSet.copyOf(requiredSlots);
    }

    public ResourceLocation getDefaultModelLocation(Block block) {
        return ModelLocationUtils.getModelLocation(block, this.suffix.orElse(""));
    }

    public ResourceLocation create(Block modelBlock, TextureMapping textureMapping, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelOutput) {
        return this.create(ModelLocationUtils.getModelLocation(modelBlock, this.suffix.orElse("")), textureMapping, modelOutput);
    }

    public ResourceLocation createWithSuffix(
        Block modelBlock, String modelLocationSuffix, TextureMapping textureMapping, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelOutput
    ) {
        return this.create(ModelLocationUtils.getModelLocation(modelBlock, modelLocationSuffix + this.suffix.orElse("")), textureMapping, modelOutput);
    }

    public ResourceLocation createWithOverride(
        Block modelBlock, String modelLocationSuffix, TextureMapping textureMapping, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelOutput
    ) {
        return this.create(ModelLocationUtils.getModelLocation(modelBlock, modelLocationSuffix), textureMapping, modelOutput);
    }

    public ResourceLocation create(ResourceLocation modelLocation, TextureMapping textureMapping, BiConsumer<ResourceLocation, Supplier<JsonElement>> modelOutput) {
        return this.create(modelLocation, textureMapping, modelOutput, this::createBaseTemplate);
    }

    public ResourceLocation create(
        ResourceLocation modelLocation,
        TextureMapping textureMapping,
        BiConsumer<ResourceLocation, Supplier<JsonElement>> modelOutput,
        ModelTemplate.JsonFactory factory
    ) {
        Map<TextureSlot, ResourceLocation> map = this.createMap(textureMapping);
        modelOutput.accept(modelLocation, () -> factory.create(modelLocation, map));
        return modelLocation;
    }

    public JsonObject createBaseTemplate(ResourceLocation modelLocation, Map<TextureSlot, ResourceLocation> modelGetter) {
        JsonObject jsonobject = new JsonObject();
        this.model.ifPresent(p_176461_ -> jsonobject.addProperty("parent", p_176461_.toString()));
        if (!modelGetter.isEmpty()) {
            JsonObject jsonobject1 = new JsonObject();
            modelGetter.forEach((p_176457_, p_176458_) -> jsonobject1.addProperty(p_176457_.getId(), p_176458_.toString()));
            jsonobject.add("textures", jsonobject1);
        }

        return jsonobject;
    }

    private Map<TextureSlot, ResourceLocation> createMap(TextureMapping textureMapping) {
        return Streams.concat(this.requiredSlots.stream(), textureMapping.getForced()).collect(ImmutableMap.toImmutableMap(Function.identity(), textureMapping::get));
    }

    public interface JsonFactory {
        JsonObject create(ResourceLocation modelLocation, Map<TextureSlot, ResourceLocation> modelGetter);
    }
}
