package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemPickupParticle extends Particle {
    private static final int LIFE_TIME = 3;
    private final RenderBuffers renderBuffers;
    private final Entity itemEntity;
    private final Entity target;
    private int life;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetXOld;
    private double targetYOld;
    private double targetZOld;

    public ItemPickupParticle(EntityRenderDispatcher entityRenderDispatcher, RenderBuffers buffers, ClientLevel level, Entity itemEntity, Entity target) {
        this(entityRenderDispatcher, buffers, level, itemEntity, target, itemEntity.getDeltaMovement());
    }

    private ItemPickupParticle(
        EntityRenderDispatcher entityRenderDispatcher, RenderBuffers buffers, ClientLevel level, Entity itemEntity, Entity target, Vec3 speedVector
    ) {
        super(level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), speedVector.x, speedVector.y, speedVector.z);
        this.renderBuffers = buffers;
        this.itemEntity = this.getSafeCopy(itemEntity);
        this.target = target;
        this.entityRenderDispatcher = entityRenderDispatcher;
        this.updatePosition();
        this.saveOldPosition();
    }

    private Entity getSafeCopy(Entity entity) {
        return (Entity)(!(entity instanceof ItemEntity) ? entity : ((ItemEntity)entity).copy());
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        float f = ((float)this.life + partialTicks) / 3.0F;
        f *= f;
        double d0 = Mth.lerp((double)partialTicks, this.targetXOld, this.targetX);
        double d1 = Mth.lerp((double)partialTicks, this.targetYOld, this.targetY);
        double d2 = Mth.lerp((double)partialTicks, this.targetZOld, this.targetZ);
        double d3 = Mth.lerp((double)f, this.itemEntity.getX(), d0);
        double d4 = Mth.lerp((double)f, this.itemEntity.getY(), d1);
        double d5 = Mth.lerp((double)f, this.itemEntity.getZ(), d2);
        MultiBufferSource.BufferSource multibuffersource$buffersource = this.renderBuffers.bufferSource();
        Vec3 vec3 = renderInfo.getPosition();
        this.entityRenderDispatcher
            .render(
                this.itemEntity,
                d3 - vec3.x(),
                d4 - vec3.y(),
                d5 - vec3.z(),
                this.itemEntity.getYRot(),
                partialTicks,
                new PoseStack(),
                multibuffersource$buffersource,
                this.entityRenderDispatcher.getPackedLightCoords(this.itemEntity, partialTicks)
            );
        multibuffersource$buffersource.endBatch();
    }

    @Override
    public void tick() {
        this.life++;
        if (this.life == 3) {
            this.remove();
        }

        this.saveOldPosition();
        this.updatePosition();
    }

    private void updatePosition() {
        this.targetX = this.target.getX();
        this.targetY = (this.target.getY() + this.target.getEyeY()) / 2.0;
        this.targetZ = this.target.getZ();
    }

    private void saveOldPosition() {
        this.targetXOld = this.targetX;
        this.targetYOld = this.targetY;
        this.targetZOld = this.targetZ;
    }

    // Neo: Computing a bounding box for the pickup animation is annoying to patch in, and probably slower than
    // always rendering it
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(float partialTicks) {
        return net.minecraft.world.phys.AABB.INFINITE;
    }
}
