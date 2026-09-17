package net.minecraft.client.renderer.culling;

import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public class Frustum {
    public static final int OFFSET_STEP = 4;
    private final FrustumIntersection intersection = new FrustumIntersection();
    private final Matrix4f matrix = new Matrix4f();
    private Vector4f viewVector;
    private double camX;
    private double camY;
    private double camZ;

    public Frustum(Matrix4f frustum, Matrix4f projection) {
        this.calculateFrustum(frustum, projection);
    }

    public Frustum(Frustum other) {
        this.intersection.set(other.matrix);
        this.matrix.set(other.matrix);
        this.camX = other.camX;
        this.camY = other.camY;
        this.camZ = other.camZ;
        this.viewVector = other.viewVector;
    }

    public Frustum offsetToFullyIncludeCameraCube(int offset) {
        double d0 = Math.floor(this.camX / (double)offset) * (double)offset;
        double d1 = Math.floor(this.camY / (double)offset) * (double)offset;
        double d2 = Math.floor(this.camZ / (double)offset) * (double)offset;
        double d3 = Math.ceil(this.camX / (double)offset) * (double)offset;
        double d4 = Math.ceil(this.camY / (double)offset) * (double)offset;

        for (double d5 = Math.ceil(this.camZ / (double)offset) * (double)offset;
            this.intersection
                    .intersectAab(
                        (float)(d0 - this.camX),
                        (float)(d1 - this.camY),
                        (float)(d2 - this.camZ),
                        (float)(d3 - this.camX),
                        (float)(d4 - this.camY),
                        (float)(d5 - this.camZ)
                    )
                != -2;
            this.camZ = this.camZ - (double)(this.viewVector.z() * 4.0F)
        ) {
            this.camX = this.camX - (double)(this.viewVector.x() * 4.0F);
            this.camY = this.camY - (double)(this.viewVector.y() * 4.0F);
        }

        return this;
    }

    public void prepare(double camX, double camY, double camZ) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    private void calculateFrustum(Matrix4f frustum, Matrix4f projection) {
        projection.mul(frustum, this.matrix);
        this.intersection.set(this.matrix);
        this.viewVector = this.matrix.transformTranspose(new Vector4f(0.0F, 0.0F, 1.0F, 0.0F));
    }

    public boolean isVisible(AABB aabb) {
        // FORGE: exit early for infinite bounds, these would otherwise fail in the intersection test at certain camera angles (GH-9321)
        if (aabb.isInfinite()) return true;
        return this.cubeInFrustum(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
    }

    private boolean cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        float f = (float)(minX - this.camX);
        float f1 = (float)(minY - this.camY);
        float f2 = (float)(minZ - this.camZ);
        float f3 = (float)(maxX - this.camX);
        float f4 = (float)(maxY - this.camY);
        float f5 = (float)(maxZ - this.camZ);
        return this.intersection.testAab(f, f1, f2, f3, f4, f5);
    }
}
