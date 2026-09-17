package net.minecraft.client.renderer.block.model.multipart;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiPart implements UnbakedModel {
    private final StateDefinition<Block, BlockState> definition;
    private final List<Selector> selectors;

    public MultiPart(StateDefinition<Block, BlockState> definition, List<Selector> selectors) {
        this.definition = definition;
        this.selectors = selectors;
    }

    public List<Selector> getSelectors() {
        return this.selectors;
    }

    public Set<MultiVariant> getMultiVariants() {
        Set<MultiVariant> set = Sets.newHashSet();

        for (Selector selector : this.selectors) {
            set.add(selector.getVariant());
        }

        return set;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof MultiPart multipart)
                ? false
                : Objects.equals(this.definition, multipart.definition) && Objects.equals(this.selectors, multipart.selectors);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.definition, this.selectors);
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return this.getSelectors().stream().flatMap(p_111969_ -> p_111969_.getVariant().getDependencies().stream()).collect(Collectors.toSet());
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        this.getSelectors().forEach(p_247936_ -> p_247936_.getVariant().resolveParents(resolver));
    }

    @Nullable
    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        MultiPartBakedModel.Builder multipartbakedmodel$builder = new MultiPartBakedModel.Builder();

        for (Selector selector : this.getSelectors()) {
            BakedModel bakedmodel = selector.getVariant().bake(baker, spriteGetter, state);
            if (bakedmodel != null) {
                multipartbakedmodel$builder.add(selector.getPredicate(this.definition), bakedmodel);
            }
        }

        return multipartbakedmodel$builder.build();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Deserializer implements JsonDeserializer<MultiPart> {
        private final BlockModelDefinition.Context context;

        public Deserializer(BlockModelDefinition.Context context) {
            this.context = context;
        }

        public MultiPart deserialize(JsonElement json, Type type, JsonDeserializationContext jsonContext) throws JsonParseException {
            return new MultiPart(this.context.getDefinition(), this.getSelectors(jsonContext, json.getAsJsonArray()));
        }

        private List<Selector> getSelectors(JsonDeserializationContext jsonContext, JsonArray elements) {
            List<Selector> list = Lists.newArrayList();

            for (JsonElement jsonelement : elements) {
                list.add(jsonContext.deserialize(jsonelement, Selector.class));
            }

            return list;
        }
    }
}
