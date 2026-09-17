package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemFrameRenderer<T extends ItemFrame> extends EntityRenderer<T> {
    private static final ModelResourceLocation FRAME_LOCATION = ModelResourceLocation.vanilla("item_frame", "map=false");
    private static final ModelResourceLocation MAP_FRAME_LOCATION = ModelResourceLocation.vanilla("item_frame", "map=true");
    private static final ModelResourceLocation GLOW_FRAME_LOCATION = ModelResourceLocation.vanilla("glow_item_frame", "map=false");
    private static final ModelResourceLocation GLOW_MAP_FRAME_LOCATION = ModelResourceLocation.vanilla("glow_item_frame", "map=true");
    public static final int GLOW_FRAME_BRIGHTNESS = 5;
    public static final int BRIGHT_MAP_LIGHT_ADJUSTMENT = 30;
    private final ItemRenderer itemRenderer;
    private final BlockRenderDispatcher blockRenderer;

    public ItemFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    protected int getBlockLightLevel(T entity, BlockPos pos) {
        return entity.getType() == EntityType.GLOW_ITEM_FRAME
            ? Math.max(5, super.getBlockLightLevel(entity, pos))
            : super.getBlockLightLevel(entity, pos);
    }

    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.pushPose();
        Direction direction = entity.getDirection();
        Vec3 vec3 = this.getRenderOffset(entity, partialTicks);
        poseStack.translate(-vec3.x(), -vec3.y(), -vec3.z());
        double d0 = 0.46875;
        poseStack.translate((double)direction.getStepX() * 0.46875, (double)direction.getStepY() * 0.46875, (double)direction.getStepZ() * 0.46875);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        boolean flag = entity.isInvisible();
        ItemStack itemstack = entity.getItem();
        if (!flag) {
            ModelManager modelmanager = this.blockRenderer.getBlockModelShaper().getModelManager();
            ModelResourceLocation modelresourcelocation = this.getFrameModelResourceLoc(entity, itemstack);
            poseStack.pushPose();
            poseStack.translate(-0.5F, -0.5F, -0.5F);
            this.blockRenderer
                .getModelRenderer()
                .renderModel(
                    poseStack.last(),
                    buffer.getBuffer(Sheets.solidBlockSheet()),
                    null,
                    modelmanager.getModel(modelresourcelocation),
                    1.0F,
                    1.0F,
                    1.0F,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
                );
            poseStack.popPose();
        }

        if (!itemstack.isEmpty()) {
            MapItemSavedData mapitemsaveddata = MapItem.getSavedData(itemstack, entity.level());
            if (flag) {
                poseStack.translate(0.0F, 0.0F, 0.5F);
            } else {
                poseStack.translate(0.0F, 0.0F, 0.4375F);
            }

            int j = mapitemsaveddata != null ? entity.getRotation() % 4 * 2 : entity.getRotation();
            poseStack.mulPose(Axis.ZP.rotationDegrees((float)j * 360.0F / 8.0F));
            if (!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.RenderItemInFrameEvent(entity, this, poseStack, buffer, packedLight)).isCanceled()) {
            if (mapitemsaveddata != null) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                float f = 0.0078125F;
                poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
                poseStack.translate(-64.0F, -64.0F, 0.0F);
                poseStack.translate(0.0F, 0.0F, -1.0F);
                if (mapitemsaveddata != null) {
                    int i = this.getLightVal(entity, 15728850, packedLight);
                    Minecraft.getInstance().gameRenderer.getMapRenderer().render(poseStack, buffer, entity.getFramedMapId(itemstack), mapitemsaveddata, true, i);
                }
            } else {
                int k = this.getLightVal(entity, 15728880, packedLight);
                poseStack.scale(0.5F, 0.5F, 0.5F);
                this.itemRenderer
                    .renderStatic(itemstack, ItemDisplayContext.FIXED, k, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
            }
            }
        }

        poseStack.popPose();
    }

    private int getLightVal(T itemFrame, int glowLightVal, int regularLightVal) {
        return itemFrame.getType() == EntityType.GLOW_ITEM_FRAME ? glowLightVal : regularLightVal;
    }

    private ModelResourceLocation getFrameModelResourceLoc(T entity, ItemStack item) {
        boolean flag = entity.getType() == EntityType.GLOW_ITEM_FRAME;
        if (item.getItem() instanceof MapItem) {
            return flag ? GLOW_MAP_FRAME_LOCATION : MAP_FRAME_LOCATION;
        } else {
            return flag ? GLOW_FRAME_LOCATION : FRAME_LOCATION;
        }
    }

    public Vec3 getRenderOffset(T entity, float partialTicks) {
        return new Vec3((double)((float)entity.getDirection().getStepX() * 0.3F), -0.25, (double)((float)entity.getDirection().getStepZ() * 0.3F));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    protected boolean shouldShowName(T entity) {
        if (Minecraft.renderNames()
            && !entity.getItem().isEmpty()
            && entity.getItem().has(DataComponents.CUSTOM_NAME)
            && this.entityRenderDispatcher.crosshairPickEntity == entity) {
            double d0 = this.entityRenderDispatcher.distanceToSqr(entity);
            float f = entity.isDiscrete() ? 32.0F : 64.0F;
            return d0 < (double)(f * f);
        } else {
            return false;
        }
    }

    protected void renderNameTag(T entity, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        super.renderNameTag(entity, entity.getItem().getHoverName(), poseStack, bufferSource, packedLight, partialTick);
    }
}
