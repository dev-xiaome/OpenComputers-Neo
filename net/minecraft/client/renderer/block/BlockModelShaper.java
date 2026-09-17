package net.minecraft.client.renderer.block;

import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockModelShaper {
    private Map<BlockState, BakedModel> modelByStateCache = Map.of();
    private final ModelManager modelManager;

    public BlockModelShaper(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    @Deprecated
    public TextureAtlasSprite getParticleIcon(BlockState state) {
        return this.getBlockModel(state).getParticleIcon(net.neoforged.neoforge.client.model.data.ModelData.EMPTY);
    }

    public TextureAtlasSprite getTexture(BlockState p_110883_, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        var data = level.getModelData(pos);
        BakedModel model = this.getBlockModel(p_110883_);
        return model.getParticleIcon(model.getModelData(level, pos, p_110883_, data));
    }

    public BakedModel getBlockModel(BlockState state) {
        BakedModel bakedmodel = this.modelByStateCache.get(state);
        if (bakedmodel == null) {
            bakedmodel = this.modelManager.getMissingModel();
        }

        return bakedmodel;
    }

    public ModelManager getModelManager() {
        return this.modelManager;
    }

    public void replaceCache(Map<BlockState, BakedModel> modelByStateCache) {
        this.modelByStateCache = modelByStateCache;
    }

    public static ModelResourceLocation stateToModelLocation(BlockState state) {
        return stateToModelLocation(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state);
    }

    public static ModelResourceLocation stateToModelLocation(ResourceLocation location, BlockState state) {
        return new ModelResourceLocation(location, statePropertiesToString(state.getValues()));
    }

    public static String statePropertiesToString(Map<Property<?>, Comparable<?>> propertyValues) {
        StringBuilder stringbuilder = new StringBuilder();

        for (Entry<Property<?>, Comparable<?>> entry : propertyValues.entrySet()) {
            if (stringbuilder.length() != 0) {
                stringbuilder.append(',');
            }

            Property<?> property = entry.getKey();
            stringbuilder.append(property.getName());
            stringbuilder.append('=');
            stringbuilder.append(getValue(property, entry.getValue()));
        }

        return stringbuilder.toString();
    }

    private static <T extends Comparable<T>> String getValue(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }
}
