package net.minecraft.client.renderer.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.BreezeDebugPayload;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public class BreezeDebugRenderer {
    private static final int JUMP_TARGET_LINE_COLOR = FastColor.ARGB32.color(255, 255, 100, 255);
    private static final int TARGET_LINE_COLOR = FastColor.ARGB32.color(255, 100, 255, 255);
    private static final int INNER_CIRCLE_COLOR = FastColor.ARGB32.color(255, 0, 255, 0);
    private static final int MIDDLE_CIRCLE_COLOR = FastColor.ARGB32.color(255, 255, 165, 0);
    private static final int OUTER_CIRCLE_COLOR = FastColor.ARGB32.color(255, 255, 0, 0);
    private static final int CIRCLE_VERTICES = 20;
    private static final float SEGMENT_SIZE_RADIANS = (float) (Math.PI / 10);
    private final Minecraft minecraft;
    private final Map<Integer, BreezeDebugPayload.BreezeInfo> perEntity = new HashMap<>();

    public BreezeDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, double xOffset, double yOffset, double zOffset) {
        LocalPlayer localplayer = this.minecraft.player;
        localplayer.level()
            .getEntities(EntityType.BREEZE, localplayer.getBoundingBox().inflate(100.0), p_312383_ -> true)
            .forEach(
                p_348113_ -> {
                    Optional<BreezeDebugPayload.BreezeInfo> optional = Optional.ofNullable(this.perEntity.get(p_348113_.getId()));
                    optional.map(BreezeDebugPayload.BreezeInfo::attackTarget)
                        .map(p_352675_ -> localplayer.level().getEntity(p_352675_))
                        .map(p_348106_ -> p_348106_.getPosition(this.minecraft.getTimer().getGameTimeDeltaPartialTick(true)))
                        .ifPresent(
                            p_312926_ -> {
                                drawLine(poseStack, buffer, xOffset, yOffset, zOffset, p_348113_.position(), p_312926_, TARGET_LINE_COLOR);
                                Vec3 vec3 = p_312926_.add(0.0, 0.01F, 0.0);
                                drawCircle(
                                    poseStack.last().pose(),
                                    xOffset,
                                    yOffset,
                                    zOffset,
                                    buffer.getBuffer(RenderType.debugLineStrip(2.0)),
                                    vec3,
                                    4.0F,
                                    INNER_CIRCLE_COLOR
                                );
                                drawCircle(
                                    poseStack.last().pose(),
                                    xOffset,
                                    yOffset,
                                    zOffset,
                                    buffer.getBuffer(RenderType.debugLineStrip(2.0)),
                                    vec3,
                                    8.0F,
                                    MIDDLE_CIRCLE_COLOR
                                );
                                drawCircle(
                                    poseStack.last().pose(),
                                    xOffset,
                                    yOffset,
                                    zOffset,
                                    buffer.getBuffer(RenderType.debugLineStrip(2.0)),
                                    vec3,
                                    20.0F,
                                    OUTER_CIRCLE_COLOR
                                );
                            }
                        );
                    optional.map(BreezeDebugPayload.BreezeInfo::jumpTarget)
                        .ifPresent(
                            p_352682_ -> {
                                drawLine(
                                    poseStack, buffer, xOffset, yOffset, zOffset, p_348113_.position(), p_352682_.getCenter(), JUMP_TARGET_LINE_COLOR
                                );
                                DebugRenderer.renderFilledBox(
                                    poseStack,
                                    buffer,
                                    AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(p_352682_)).move(-xOffset, -yOffset, -zOffset),
                                    1.0F,
                                    0.0F,
                                    0.0F,
                                    1.0F
                                );
                            }
                        );
                }
            );
    }

    private static void drawLine(
        PoseStack poseStack, MultiBufferSource buffer, double xOffset, double yOffset, double zOffset, Vec3 fromPos, Vec3 toPos, int color
    ) {
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.debugLineStrip(2.0));
        vertexconsumer.addVertex(poseStack.last(), (float)(fromPos.x - xOffset), (float)(fromPos.y - yOffset), (float)(fromPos.z - zOffset))
            .setColor(color);
        vertexconsumer.addVertex(poseStack.last(), (float)(toPos.x - xOffset), (float)(toPos.y - yOffset), (float)(toPos.z - zOffset))
            .setColor(color);
    }

    private static void drawCircle(
        Matrix4f pose, double xOffset, double yOffset, double zOffset, VertexConsumer consumer, Vec3 pos, float radius, int color
    ) {
        for (int i = 0; i < 20; i++) {
            drawCircleVertex(i, pose, xOffset, yOffset, zOffset, consumer, pos, radius, color);
        }

        drawCircleVertex(0, pose, xOffset, yOffset, zOffset, consumer, pos, radius, color);
    }

    private static void drawCircleVertex(
        int index,
        Matrix4f pose,
        double xOffset,
        double yOffset,
        double zOffset,
        VertexConsumer consumer,
        Vec3 circleCenter,
        float radius,
        int color
    ) {
        float f = (float)index * (float) (Math.PI / 10);
        Vec3 vec3 = circleCenter.add((double)radius * Math.cos((double)f), 0.0, (double)radius * Math.sin((double)f));
        consumer.addVertex(pose, (float)(vec3.x - xOffset), (float)(vec3.y - yOffset), (float)(vec3.z - zOffset)).setColor(color);
    }

    public void clear() {
        this.perEntity.clear();
    }

    public void add(BreezeDebugPayload.BreezeInfo breeze) {
        this.perEntity.put(breeze.id(), breeze);
    }
}
