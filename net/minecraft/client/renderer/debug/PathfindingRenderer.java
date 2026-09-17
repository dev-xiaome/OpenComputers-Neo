package net.minecraft.client.renderer.debug;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PathfindingRenderer implements DebugRenderer.SimpleDebugRenderer {
    private final Map<Integer, Path> pathMap = Maps.newHashMap();
    private final Map<Integer, Float> pathMaxDist = Maps.newHashMap();
    private final Map<Integer, Long> creationMap = Maps.newHashMap();
    private static final long TIMEOUT = 5000L;
    private static final float MAX_RENDER_DIST = 80.0F;
    private static final boolean SHOW_OPEN_CLOSED = true;
    private static final boolean SHOW_OPEN_CLOSED_COST_MALUS = false;
    private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_TEXT = false;
    private static final boolean SHOW_OPEN_CLOSED_NODE_TYPE_WITH_BOX = true;
    private static final boolean SHOW_GROUND_LABELS = true;
    private static final float TEXT_SCALE = 0.02F;

    public void addPath(int entityId, Path path, float maxDistanceToWaypoint) {
        this.pathMap.put(entityId, path);
        this.creationMap.put(entityId, Util.getMillis());
        this.pathMaxDist.put(entityId, maxDistanceToWaypoint);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        if (!this.pathMap.isEmpty()) {
            long i = Util.getMillis();

            for (Integer integer : this.pathMap.keySet()) {
                Path path = this.pathMap.get(integer);
                float f = this.pathMaxDist.get(integer);
                renderPath(poseStack, bufferSource, path, f, true, true, camX, camY, camZ);
            }

            for (Integer integer1 : this.creationMap.keySet().toArray(new Integer[0])) {
                if (i - this.creationMap.get(integer1) > 5000L) {
                    this.pathMap.remove(integer1);
                    this.creationMap.remove(integer1);
                }
            }
        }
    }

    public static void renderPath(
        PoseStack poseStack,
        MultiBufferSource buffer,
        Path path,
        float p_270841_,
        boolean p_270481_,
        boolean p_270748_,
        double x,
        double y,
        double z
    ) {
        renderPathLine(poseStack, buffer.getBuffer(RenderType.debugLineStrip(6.0)), path, x, y, z);
        BlockPos blockpos = path.getTarget();
        if (distanceToCamera(blockpos, x, y, z) <= 80.0F) {
            DebugRenderer.renderFilledBox(
                poseStack,
                buffer,
                new AABB(
                        (double)((float)blockpos.getX() + 0.25F),
                        (double)((float)blockpos.getY() + 0.25F),
                        (double)blockpos.getZ() + 0.25,
                        (double)((float)blockpos.getX() + 0.75F),
                        (double)((float)blockpos.getY() + 0.75F),
                        (double)((float)blockpos.getZ() + 0.75F)
                    )
                    .move(-x, -y, -z),
                0.0F,
                1.0F,
                0.0F,
                0.5F
            );

            for (int i = 0; i < path.getNodeCount(); i++) {
                Node node = path.getNode(i);
                if (distanceToCamera(node.asBlockPos(), x, y, z) <= 80.0F) {
                    float f = i == path.getNextNodeIndex() ? 1.0F : 0.0F;
                    float f1 = i == path.getNextNodeIndex() ? 0.0F : 1.0F;
                    DebugRenderer.renderFilledBox(
                        poseStack,
                        buffer,
                        new AABB(
                                (double)((float)node.x + 0.5F - p_270841_),
                                (double)((float)node.y + 0.01F * (float)i),
                                (double)((float)node.z + 0.5F - p_270841_),
                                (double)((float)node.x + 0.5F + p_270841_),
                                (double)((float)node.y + 0.25F + 0.01F * (float)i),
                                (double)((float)node.z + 0.5F + p_270841_)
                            )
                            .move(-x, -y, -z),
                        f,
                        0.0F,
                        f1,
                        0.5F
                    );
                }
            }
        }

        Path.DebugData path$debugdata = path.debugData();
        if (p_270481_ && path$debugdata != null) {
            for (Node node1 : path$debugdata.closedSet()) {
                if (distanceToCamera(node1.asBlockPos(), x, y, z) <= 80.0F) {
                    DebugRenderer.renderFilledBox(
                        poseStack,
                        buffer,
                        new AABB(
                                (double)((float)node1.x + 0.5F - p_270841_ / 2.0F),
                                (double)((float)node1.y + 0.01F),
                                (double)((float)node1.z + 0.5F - p_270841_ / 2.0F),
                                (double)((float)node1.x + 0.5F + p_270841_ / 2.0F),
                                (double)node1.y + 0.1,
                                (double)((float)node1.z + 0.5F + p_270841_ / 2.0F)
                            )
                            .move(-x, -y, -z),
                        1.0F,
                        0.8F,
                        0.8F,
                        0.5F
                    );
                }
            }

            for (Node node3 : path$debugdata.openSet()) {
                if (distanceToCamera(node3.asBlockPos(), x, y, z) <= 80.0F) {
                    DebugRenderer.renderFilledBox(
                        poseStack,
                        buffer,
                        new AABB(
                                (double)((float)node3.x + 0.5F - p_270841_ / 2.0F),
                                (double)((float)node3.y + 0.01F),
                                (double)((float)node3.z + 0.5F - p_270841_ / 2.0F),
                                (double)((float)node3.x + 0.5F + p_270841_ / 2.0F),
                                (double)node3.y + 0.1,
                                (double)((float)node3.z + 0.5F + p_270841_ / 2.0F)
                            )
                            .move(-x, -y, -z),
                        0.8F,
                        1.0F,
                        1.0F,
                        0.5F
                    );
                }
            }
        }

        if (p_270748_) {
            for (int j = 0; j < path.getNodeCount(); j++) {
                Node node2 = path.getNode(j);
                if (distanceToCamera(node2.asBlockPos(), x, y, z) <= 80.0F) {
                    DebugRenderer.renderFloatingText(
                        poseStack,
                        buffer,
                        String.valueOf(node2.type),
                        (double)node2.x + 0.5,
                        (double)node2.y + 0.75,
                        (double)node2.z + 0.5,
                        -1,
                        0.02F,
                        true,
                        0.0F,
                        true
                    );
                    DebugRenderer.renderFloatingText(
                        poseStack,
                        buffer,
                        String.format(Locale.ROOT, "%.2f", node2.costMalus),
                        (double)node2.x + 0.5,
                        (double)node2.y + 0.25,
                        (double)node2.z + 0.5,
                        -1,
                        0.02F,
                        true,
                        0.0F,
                        true
                    );
                }
            }
        }
    }

    public static void renderPathLine(PoseStack poseStack, VertexConsumer consumer, Path path, double x, double y, double z) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (!(distanceToCamera(node.asBlockPos(), x, y, z) > 80.0F)) {
                float f = (float)i / (float)path.getNodeCount() * 0.33F;
                int j = i == 0 ? 0 : Mth.hsvToRgb(f, 0.9F, 0.9F);
                int k = j >> 16 & 0xFF;
                int l = j >> 8 & 0xFF;
                int i1 = j & 0xFF;
                consumer.addVertex(
                        poseStack.last(),
                        (float)((double)node.x - x + 0.5),
                        (float)((double)node.y - y + 0.5),
                        (float)((double)node.z - z + 0.5)
                    )
                    .setColor(k, l, i1, 255);
            }
        }
    }

    private static float distanceToCamera(BlockPos pos, double x, double y, double z) {
        return (float)(
            Math.abs((double)pos.getX() - x) + Math.abs((double)pos.getY() - y) + Math.abs((double)pos.getZ() - z)
        );
    }
}
