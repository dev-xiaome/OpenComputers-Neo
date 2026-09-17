package net.minecraft.client.renderer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemModelShaper {
    public final Int2ObjectMap<ModelResourceLocation> shapes = new Int2ObjectOpenHashMap<>(256);
    private final Int2ObjectMap<BakedModel> shapesCache = new Int2ObjectOpenHashMap<>(256);
    private final ModelManager modelManager;

    public ItemModelShaper(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    public BakedModel getItemModel(ItemStack stack) {
        BakedModel bakedmodel = this.getItemModel(stack.getItem());
        return bakedmodel == null ? this.modelManager.getMissingModel() : bakedmodel;
    }

    @Nullable
    public BakedModel getItemModel(Item item) {
        return this.shapesCache.get(getIndex(item));
    }

    private static int getIndex(Item item) {
        return Item.getId(item);
    }

    public void register(Item item, ModelResourceLocation modelLocation) {
        this.shapes.put(getIndex(item), modelLocation);
    }

    public ModelManager getModelManager() {
        return this.modelManager;
    }

    public void rebuildCache() {
        this.shapesCache.clear();

        for (Entry<Integer, ModelResourceLocation> entry : this.shapes.entrySet()) {
            this.shapesCache.put(entry.getKey(), this.modelManager.getModel(entry.getValue()));
        }
    }
}
