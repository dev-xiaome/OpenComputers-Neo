package net.minecraft.client.renderer.blockentity;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface BlockEntityRendererProvider<T extends BlockEntity> {
    BlockEntityRenderer<T> create(BlockEntityRendererProvider.Context context);

    @OnlyIn(Dist.CLIENT)
    public static class Context {
        private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
        private final BlockRenderDispatcher blockRenderDispatcher;
        private final ItemRenderer itemRenderer;
        private final EntityRenderDispatcher entityRenderer;
        private final EntityModelSet modelSet;
        private final Font font;

        public Context(
            BlockEntityRenderDispatcher blockEntityRenderDispatcher,
            BlockRenderDispatcher blockRenderDispatcher,
            ItemRenderer itemRenderer,
            EntityRenderDispatcher entityRenderer,
            EntityModelSet modelSet,
            Font font
        ) {
            this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
            this.blockRenderDispatcher = blockRenderDispatcher;
            this.itemRenderer = itemRenderer;
            this.entityRenderer = entityRenderer;
            this.modelSet = modelSet;
            this.font = font;
        }

        public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
            return this.blockEntityRenderDispatcher;
        }

        public BlockRenderDispatcher getBlockRenderDispatcher() {
            return this.blockRenderDispatcher;
        }

        public EntityRenderDispatcher getEntityRenderer() {
            return this.entityRenderer;
        }

        public ItemRenderer getItemRenderer() {
            return this.itemRenderer;
        }

        public EntityModelSet getModelSet() {
            return this.modelSet;
        }

        public ModelPart bakeLayer(ModelLayerLocation layerLocation) {
            return this.modelSet.bakeLayer(layerLocation);
        }

        public Font getFont() {
            return this.font;
        }
    }
}
