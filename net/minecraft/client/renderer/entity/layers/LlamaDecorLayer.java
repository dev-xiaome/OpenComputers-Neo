package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LlamaDecorLayer extends RenderLayer<Llama, LlamaModel<Llama>> {
    private static final ResourceLocation[] TEXTURE_LOCATION = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/white.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/orange.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/magenta.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/light_blue.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/yellow.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/lime.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/pink.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/gray.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/light_gray.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/cyan.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/purple.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/blue.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/brown.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/green.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/red.png"),
        ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/black.png")
    };
    private static final ResourceLocation TRADER_LLAMA = ResourceLocation.withDefaultNamespace("textures/entity/llama/decor/trader_llama.png");
    private final LlamaModel<Llama> model;

    public LlamaDecorLayer(RenderLayerParent<Llama, LlamaModel<Llama>> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new LlamaModel<>(modelSet.bakeLayer(ModelLayers.LLAMA_DECOR));
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        Llama livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        DyeColor dyecolor = livingEntity.getSwag();
        ResourceLocation resourcelocation;
        if (dyecolor != null) {
            resourcelocation = TEXTURE_LOCATION[dyecolor.getId()];
        } else {
            if (!livingEntity.isTraderLlama()) {
                return;
            }

            resourcelocation = TRADER_LLAMA;
        }

        this.getParentModel().copyPropertiesTo(this.model);
        this.model.setupAnim(livingEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(resourcelocation));
        this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
