package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.StructuresDebugPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StructureRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Minecraft minecraft;
    private final Map<ResourceKey<Level>, Map<String, BoundingBox>> postMainBoxes = Maps.newIdentityHashMap();
    private final Map<ResourceKey<Level>, Map<String, StructuresDebugPayload.PieceInfo>> postPieces = Maps.newIdentityHashMap();
    private static final int MAX_RENDER_DIST = 500;

    public StructureRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Camera camera = this.minecraft.gameRenderer.getMainCamera();
        ResourceKey<Level> resourcekey = this.minecraft.level.dimension();
        BlockPos blockpos = BlockPos.containing(camera.getPosition().x, 0.0, camera.getPosition().z);
        VertexConsumer vertexconsumer = bufferSource.getBuffer(RenderType.lines());
        if (this.postMainBoxes.containsKey(resourcekey)) {
            for (BoundingBox boundingbox : this.postMainBoxes.get(resourcekey).values()) {
                if (blockpos.closerThan(boundingbox.getCenter(), 500.0)) {
                    LevelRenderer.renderLineBox(
                        poseStack,
                        vertexconsumer,
                        (double)boundingbox.minX() - camX,
                        (double)boundingbox.minY() - camY,
                        (double)boundingbox.minZ() - camZ,
                        (double)(boundingbox.maxX() + 1) - camX,
                        (double)(boundingbox.maxY() + 1) - camY,
                        (double)(boundingbox.maxZ() + 1) - camZ,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F,
                        1.0F
                    );
                }
            }
        }

        Map<String, StructuresDebugPayload.PieceInfo> map = this.postPieces.get(resourcekey);
        if (map != null) {
            for (StructuresDebugPayload.PieceInfo structuresdebugpayload$pieceinfo : map.values()) {
                BoundingBox boundingbox1 = structuresdebugpayload$pieceinfo.boundingBox();
                if (blockpos.closerThan(boundingbox1.getCenter(), 500.0)) {
                    if (structuresdebugpayload$pieceinfo.isStart()) {
                        LevelRenderer.renderLineBox(
                            poseStack,
                            vertexconsumer,
                            (double)boundingbox1.minX() - camX,
                            (double)boundingbox1.minY() - camY,
                            (double)boundingbox1.minZ() - camZ,
                            (double)(boundingbox1.maxX() + 1) - camX,
                            (double)(boundingbox1.maxY() + 1) - camY,
                            (double)(boundingbox1.maxZ() + 1) - camZ,
                            0.0F,
                            1.0F,
                            0.0F,
                            1.0F,
                            0.0F,
                            1.0F,
                            0.0F
                        );
                    } else {
                        LevelRenderer.renderLineBox(
                            poseStack,
                            vertexconsumer,
                            (double)boundingbox1.minX() - camX,
                            (double)boundingbox1.minY() - camY,
                            (double)boundingbox1.minZ() - camZ,
                            (double)(boundingbox1.maxX() + 1) - camX,
                            (double)(boundingbox1.maxY() + 1) - camY,
                            (double)(boundingbox1.maxZ() + 1) - camZ,
                            0.0F,
                            0.0F,
                            1.0F,
                            1.0F,
                            0.0F,
                            0.0F,
                            1.0F
                        );
                    }
                }
            }
        }
    }

    public void addBoundingBox(BoundingBox boundingBox, List<StructuresDebugPayload.PieceInfo> pieces, ResourceKey<Level> dimension) {
        this.postMainBoxes.computeIfAbsent(dimension, p_294379_ -> new HashMap<>()).put(boundingBox.toString(), boundingBox);
        Map<String, StructuresDebugPayload.PieceInfo> map = this.postPieces.computeIfAbsent(dimension, p_294187_ -> new HashMap<>());

        for (StructuresDebugPayload.PieceInfo structuresdebugpayload$pieceinfo : pieces) {
            map.put(structuresdebugpayload$pieceinfo.boundingBox().toString(), structuresdebugpayload$pieceinfo);
        }
    }

    @Override
    public void clear() {
        this.postMainBoxes.clear();
        this.postPieces.clear();
    }
}
