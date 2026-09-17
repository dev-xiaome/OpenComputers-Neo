package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BeeStingerLayer<T extends LivingEntity, M extends PlayerModel<T>> extends StuckInBodyLayer<T, M> {
    private static final ResourceLocation BEE_STINGER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/bee/bee_stinger.png");

    public BeeStingerLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    @Override
    protected int numStuck(T entity) {
        return entity.getStingerCount();
    }

    @Override
    protected void renderStuckItem(
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, Entity entity, float x, float y, float z, float partialTick
    ) {
        float f = Mth.sqrt(x * x + z * z);
        float f1 = (float)(Math.atan2((double)x, (double)z) * 180.0F / (float)Math.PI);
        float f2 = (float)(Math.atan2((double)y, (double)f) * 180.0F / (float)Math.PI);
        poseStack.translate(0.0F, 0.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(f1 - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f2));
        float f3 = 0.0F;
        float f4 = 0.125F;
        float f5 = 0.0F;
        float f6 = 0.0625F;
        float f7 = 0.03125F;
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.scale(0.03125F, 0.03125F, 0.03125F);
        poseStack.translate(2.5F, 0.0F, 0.0F);
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(BEE_STINGER_LOCATION));

        for (int i = 0; i < 4; i++) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            PoseStack.Pose posestack$pose = poseStack.last();
            vertex(vertexconsumer, posestack$pose, -4.5F, -1, 0.0F, 0.0F, packedLight);
            vertex(vertexconsumer, posestack$pose, 4.5F, -1, 0.125F, 0.0F, packedLight);
            vertex(vertexconsumer, posestack$pose, 4.5F, 1, 0.125F, 0.0625F, packedLight);
            vertex(vertexconsumer, posestack$pose, -4.5F, 1, 0.0F, 0.0625F, packedLight);
        }
    }

    private static void vertex(
        VertexConsumer consumer, PoseStack.Pose pose, float x, int y, float u, float v, int packedLight
    ) {
        consumer.addVertex(pose, x, (float)y, 0.0F)
            .setColor(-1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
