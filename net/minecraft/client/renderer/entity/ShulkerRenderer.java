package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import javax.annotation.Nullable;
import net.minecraft.client.model.ShulkerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.layers.ShulkerHeadLayer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShulkerRenderer extends MobRenderer<Shulker, ShulkerModel<Shulker>> {
    private static final ResourceLocation DEFAULT_TEXTURE_LOCATION = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION
        .texture()
        .withPath(p_349906_ -> "textures/" + p_349906_ + ".png");
    private static final ResourceLocation[] TEXTURE_LOCATION = Sheets.SHULKER_TEXTURE_LOCATION
        .stream()
        .map(p_349907_ -> p_349907_.texture().withPath(p_349905_ -> "textures/" + p_349905_ + ".png"))
        .toArray(ResourceLocation[]::new);

    public ShulkerRenderer(EntityRendererProvider.Context p_174370_) {
        super(p_174370_, new ShulkerModel<>(p_174370_.bakeLayer(ModelLayers.SHULKER)), 0.0F);
        this.addLayer(new ShulkerHeadLayer(this));
    }

    public Vec3 getRenderOffset(Shulker entity, float partialTicks) {
        return entity.getRenderPosition(partialTicks).orElse(super.getRenderOffset(entity, partialTicks)).scale((double)entity.getScale());
    }

    public boolean shouldRender(Shulker livingEntity, Frustum camera, double camX, double camY, double camZ) {
        return super.shouldRender(livingEntity, camera, camX, camY, camZ)
            ? true
            : livingEntity.getRenderPosition(0.0F)
                .filter(
                    p_174374_ -> {
                        EntityType<?> entitytype = livingEntity.getType();
                        float f = entitytype.getHeight() / 2.0F;
                        float f1 = entitytype.getWidth() / 2.0F;
                        Vec3 vec3 = Vec3.atBottomCenterOf(livingEntity.blockPosition());
                        return camera.isVisible(
                            new AABB(p_174374_.x, p_174374_.y + (double)f, p_174374_.z, vec3.x, vec3.y + (double)f, vec3.z)
                                .inflate((double)f1, (double)f, (double)f1)
                        );
                    }
                )
                .isPresent();
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(Shulker entity) {
        return getTextureLocation(entity.getColor());
    }

    public static ResourceLocation getTextureLocation(@Nullable DyeColor color) {
        return color == null ? DEFAULT_TEXTURE_LOCATION : TEXTURE_LOCATION[color.getId()];
    }

    protected void setupRotations(Shulker entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot + 180.0F, partialTick, scale);
        poseStack.rotateAround(entity.getAttachFace().getOpposite().getRotation(), 0.0F, 0.5F, 0.0F);
    }
}
