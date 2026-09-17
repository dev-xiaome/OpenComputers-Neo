package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public class LightSectionDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final Duration REFRESH_INTERVAL = Duration.ofMillis(500L);
    private static final int RADIUS = 10;
    private static final Vector4f LIGHT_AND_BLOCKS_COLOR = new Vector4f(1.0F, 1.0F, 0.0F, 0.25F);
    private static final Vector4f LIGHT_ONLY_COLOR = new Vector4f(0.25F, 0.125F, 0.0F, 0.125F);
    private final Minecraft minecraft;
    private final LightLayer lightLayer;
    private Instant lastUpdateTime = Instant.now();
    @Nullable
    private LightSectionDebugRenderer.SectionData data;

    public LightSectionDebugRenderer(Minecraft minecraft, LightLayer lightLayer) {
        this.minecraft = minecraft;
        this.lightLayer = lightLayer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Instant instant = Instant.now();
        if (this.data == null || Duration.between(this.lastUpdateTime, instant).compareTo(REFRESH_INTERVAL) > 0) {
            this.lastUpdateTime = instant;
            this.data = new LightSectionDebugRenderer.SectionData(
                this.minecraft.level.getLightEngine(), SectionPos.of(this.minecraft.player.blockPosition()), 10, this.lightLayer
            );
        }

        renderEdges(poseStack, this.data.lightAndBlocksShape, this.data.minPos, bufferSource, camX, camY, camZ, LIGHT_AND_BLOCKS_COLOR);
        renderEdges(poseStack, this.data.lightShape, this.data.minPos, bufferSource, camX, camY, camZ, LIGHT_ONLY_COLOR);
        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.debugSectionQuads());
        renderFaces(poseStack, this.data.lightAndBlocksShape, this.data.minPos, vertexconsumer, camX, camY, camZ, LIGHT_AND_BLOCKS_COLOR);
        renderFaces(poseStack, this.data.lightShape, this.data.minPos, vertexconsumer, camX, camY, camZ, LIGHT_ONLY_COLOR);
    }

    private static void renderFaces(
        PoseStack poseStack,
        DiscreteVoxelShape shape,
        SectionPos pos,
        VertexConsumer buffer,
        double camX,
        double camY,
        double camZ,
        Vector4f color
    ) {
        shape.forAllFaces((p_282087_, p_283360_, p_282854_, p_282233_) -> {
            int i = p_283360_ + pos.getX();
            int j = p_282854_ + pos.getY();
            int k = p_282233_ + pos.getZ();
            renderFace(poseStack, buffer, p_282087_, camX, camY, camZ, i, j, k, color);
        });
    }

    private static void renderEdges(
        PoseStack poseStack,
        DiscreteVoxelShape shape,
        SectionPos pos,
        MultiBufferSource bufferSource,
        double camX,
        double camY,
        double camZ,
        Vector4f color
    ) {
        shape.forAllEdges((p_283441_, p_283631_, p_282083_, p_281900_, p_281481_, p_283547_) -> {
            int i = p_283441_ + pos.getX();
            int j = p_283631_ + pos.getY();
            int k = p_282083_ + pos.getZ();
            int l = p_281900_ + pos.getX();
            int i1 = p_281481_ + pos.getY();
            int j1 = p_283547_ + pos.getZ();
            VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.debugLineStrip(1.0));
            renderEdge(poseStack, vertexconsumer, camX, camY, camZ, i, j, k, l, i1, j1, color);
        }, true);
    }

    private static void renderFace(
        PoseStack poseStack,
        VertexConsumer buffer,
        Direction face,
        double camX,
        double camY,
        double camZ,
        int blockX,
        int blockY,
        int blockZ,
        Vector4f color
    ) {
        float f = (float)((double)SectionPos.sectionToBlockCoord(blockX) - camX);
        float f1 = (float)((double)SectionPos.sectionToBlockCoord(blockY) - camY);
        float f2 = (float)((double)SectionPos.sectionToBlockCoord(blockZ) - camZ);
        LevelRenderer.renderFace(
            poseStack, buffer, face, f, f1, f2, f + 16.0F, f1 + 16.0F, f2 + 16.0F, color.x(), color.y(), color.z(), color.w()
        );
    }

    private static void renderEdge(
        PoseStack poseStack,
        VertexConsumer buffer,
        double camX,
        double camY,
        double camZ,
        int x1,
        int y1,
        int z1,
        int x2,
        int y2,
        int z2,
        Vector4f color
    ) {
        float f = (float)((double)SectionPos.sectionToBlockCoord(x1) - camX);
        float f1 = (float)((double)SectionPos.sectionToBlockCoord(y1) - camY);
        float f2 = (float)((double)SectionPos.sectionToBlockCoord(z1) - camZ);
        float f3 = (float)((double)SectionPos.sectionToBlockCoord(x2) - camX);
        float f4 = (float)((double)SectionPos.sectionToBlockCoord(y2) - camY);
        float f5 = (float)((double)SectionPos.sectionToBlockCoord(z2) - camZ);
        Matrix4f matrix4f = poseStack.last().pose();
        buffer.addVertex(matrix4f, f, f1, f2).setColor(color.x(), color.y(), color.z(), 1.0F);
        buffer.addVertex(matrix4f, f3, f4, f5).setColor(color.x(), color.y(), color.z(), 1.0F);
    }

    @OnlyIn(Dist.CLIENT)
    static final class SectionData {
        final DiscreteVoxelShape lightAndBlocksShape;
        final DiscreteVoxelShape lightShape;
        final SectionPos minPos;

        SectionData(LevelLightEngine levelLightEngine, SectionPos pos, int radius, LightLayer lightLayer) {
            int i = radius * 2 + 1;
            this.lightAndBlocksShape = new BitSetDiscreteVoxelShape(i, i, i);
            this.lightShape = new BitSetDiscreteVoxelShape(i, i, i);

            for (int j = 0; j < i; j++) {
                for (int k = 0; k < i; k++) {
                    for (int l = 0; l < i; l++) {
                        SectionPos sectionpos = SectionPos.of(pos.x() + l - radius, pos.y() + k - radius, pos.z() + j - radius);
                        LayerLightSectionStorage.SectionType layerlightsectionstorage$sectiontype = levelLightEngine.getDebugSectionType(lightLayer, sectionpos);
                        if (layerlightsectionstorage$sectiontype == LayerLightSectionStorage.SectionType.LIGHT_AND_DATA) {
                            this.lightAndBlocksShape.fill(l, k, j);
                            this.lightShape.fill(l, k, j);
                        } else if (layerlightsectionstorage$sectiontype == LayerLightSectionStorage.SectionType.LIGHT_ONLY) {
                            this.lightShape.fill(l, k, j);
                        }
                    }
                }
            }

            this.minPos = SectionPos.of(pos.x() - radius, pos.y() - radius, pos.z() - radius);
        }
    }
}
