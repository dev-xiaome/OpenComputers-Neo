package com.mojang.blaze3d.vertex;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class SheetedDecalTextureGenerator implements VertexConsumer {
    private final VertexConsumer delegate;
    private final Matrix4f cameraInversePose;
    private final Matrix3f normalInversePose;
    private final float textureScale;
    private final Vector3f worldPos = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private float x;
    private float y;
    private float z;

    public SheetedDecalTextureGenerator(VertexConsumer delegate, PoseStack.Pose pose, float textureScale) {
        this.delegate = delegate;
        this.cameraInversePose = new Matrix4f(pose.pose()).invert();
        this.normalInversePose = new Matrix3f(pose.normal()).invert();
        this.textureScale = textureScale;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.delegate.setColor(-1);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        this.delegate.setNormal(normalX, normalY, normalZ);
        Vector3f vector3f = this.normalInversePose.transform(normalX, normalY, normalZ, this.normal);
        Direction direction = Direction.getNearest(vector3f.x(), vector3f.y(), vector3f.z());
        Vector3f vector3f1 = this.cameraInversePose.transformPosition(this.x, this.y, this.z, this.worldPos);
        vector3f1.rotateY((float) Math.PI);
        vector3f1.rotateX((float) (-Math.PI / 2));
        vector3f1.rotate(direction.getRotation());
        this.delegate.setUv(-vector3f1.x() * this.textureScale, -vector3f1.y() * this.textureScale);
        return this;
    }
}
