package net.minecraft.client.model.geom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public final class ModelPart {
    public static final float DEFAULT_SCALE = 1.0F;
    public float x;
    public float y;
    public float z;
    public float xRot;
    public float yRot;
    public float zRot;
    public float xScale = 1.0F;
    public float yScale = 1.0F;
    public float zScale = 1.0F;
    public boolean visible = true;
    public boolean skipDraw;
    private final List<ModelPart.Cube> cubes;
    private final Map<String, ModelPart> children;
    private PartPose initialPose = PartPose.ZERO;

    public ModelPart(List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
        this.cubes = cubes;
        this.children = children;
    }

    public PartPose storePose() {
        return PartPose.offsetAndRotation(this.x, this.y, this.z, this.xRot, this.yRot, this.zRot);
    }

    public PartPose getInitialPose() {
        return this.initialPose;
    }

    public void setInitialPose(PartPose initialPose) {
        this.initialPose = initialPose;
    }

    public void resetPose() {
        this.loadPose(this.initialPose);
    }

    public void loadPose(PartPose partPose) {
        this.x = partPose.x;
        this.y = partPose.y;
        this.z = partPose.z;
        this.xRot = partPose.xRot;
        this.yRot = partPose.yRot;
        this.zRot = partPose.zRot;
        this.xScale = 1.0F;
        this.yScale = 1.0F;
        this.zScale = 1.0F;
    }

    public void copyFrom(ModelPart modelPart) {
        this.xScale = modelPart.xScale;
        this.yScale = modelPart.yScale;
        this.zScale = modelPart.zScale;
        this.xRot = modelPart.xRot;
        this.yRot = modelPart.yRot;
        this.zRot = modelPart.zRot;
        this.x = modelPart.x;
        this.y = modelPart.y;
        this.z = modelPart.z;
    }

    public boolean hasChild(String name) {
        return this.children.containsKey(name);
    }

    public ModelPart getChild(String name) {
        ModelPart modelpart = this.children.get(name);
        if (modelpart == null) {
            throw new NoSuchElementException("Can't find part " + name);
        } else {
            return modelpart;
        }
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setRotation(float xRot, float yRot, float zRot) {
        this.xRot = xRot;
        this.yRot = yRot;
        this.zRot = zRot;
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        this.render(poseStack, buffer, packedLight, packedOverlay, -1);
    }

    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        if (this.visible) {
            if (!this.cubes.isEmpty() || !this.children.isEmpty()) {
                poseStack.pushPose();
                this.translateAndRotate(poseStack);
                if (!this.skipDraw) {
                    this.compile(poseStack.last(), buffer, packedLight, packedOverlay, color);
                }

                for (ModelPart modelpart : this.children.values()) {
                    modelpart.render(poseStack, buffer, packedLight, packedOverlay, color);
                }

                poseStack.popPose();
            }
        }
    }

    public void visit(PoseStack poseStack, ModelPart.Visitor visitor) {
        this.visit(poseStack, visitor, "");
    }

    private void visit(PoseStack poseStack, ModelPart.Visitor visitor, String path) {
        if (!this.cubes.isEmpty() || !this.children.isEmpty()) {
            poseStack.pushPose();
            this.translateAndRotate(poseStack);
            PoseStack.Pose posestack$pose = poseStack.last();

            for (int i = 0; i < this.cubes.size(); i++) {
                visitor.visit(posestack$pose, path, i, this.cubes.get(i));
            }

            String s = path + "/";
            this.children.forEach((p_171320_, p_171321_) -> p_171321_.visit(poseStack, visitor, s + p_171320_));
            poseStack.popPose();
        }
    }

    public void translateAndRotate(PoseStack poseStack) {
        poseStack.translate(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
        if (this.xRot != 0.0F || this.yRot != 0.0F || this.zRot != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationZYX(this.zRot, this.yRot, this.xRot));
        }

        if (this.xScale != 1.0F || this.yScale != 1.0F || this.zScale != 1.0F) {
            poseStack.scale(this.xScale, this.yScale, this.zScale);
        }
    }

    private void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        for (ModelPart.Cube modelpart$cube : this.cubes) {
            modelpart$cube.compile(pose, buffer, packedLight, packedOverlay, color);
        }
    }

    public ModelPart.Cube getRandomCube(RandomSource random) {
        return this.cubes.get(random.nextInt(this.cubes.size()));
    }

    public boolean isEmpty() {
        return this.cubes.isEmpty();
    }

    public void offsetPos(Vector3f offset) {
        this.x = this.x + offset.x();
        this.y = this.y + offset.y();
        this.z = this.z + offset.z();
    }

    public void offsetRotation(Vector3f offset) {
        this.xRot = this.xRot + offset.x();
        this.yRot = this.yRot + offset.y();
        this.zRot = this.zRot + offset.z();
    }

    public void offsetScale(Vector3f offset) {
        this.xScale = this.xScale + offset.x();
        this.yScale = this.yScale + offset.y();
        this.zScale = this.zScale + offset.z();
    }

    public Stream<ModelPart> getAllParts() {
        return Stream.concat(Stream.of(this), this.children.values().stream().flatMap(ModelPart::getAllParts));
    }

    @OnlyIn(Dist.CLIENT)
    public static class Cube {
        private final ModelPart.Polygon[] polygons;
        public final float minX;
        public final float minY;
        public final float minZ;
        public final float maxX;
        public final float maxY;
        public final float maxZ;

        public Cube(
            int texCoordU,
            int texCoordV,
            float originX,
            float originY,
            float originZ,
            float dimensionX,
            float dimensionY,
            float dimensionZ,
            float gtowX,
            float growY,
            float growZ,
            boolean mirror,
            float texScaleU,
            float texScaleV,
            Set<Direction> visibleFaces
        ) {
            this.minX = originX;
            this.minY = originY;
            this.minZ = originZ;
            this.maxX = originX + dimensionX;
            this.maxY = originY + dimensionY;
            this.maxZ = originZ + dimensionZ;
            this.polygons = new ModelPart.Polygon[visibleFaces.size()];
            float f = originX + dimensionX;
            float f1 = originY + dimensionY;
            float f2 = originZ + dimensionZ;
            originX -= gtowX;
            originY -= growY;
            originZ -= growZ;
            f += gtowX;
            f1 += growY;
            f2 += growZ;
            if (mirror) {
                float f3 = f;
                f = originX;
                originX = f3;
            }

            ModelPart.Vertex modelpart$vertex7 = new ModelPart.Vertex(originX, originY, originZ, 0.0F, 0.0F);
            ModelPart.Vertex modelpart$vertex = new ModelPart.Vertex(f, originY, originZ, 0.0F, 8.0F);
            ModelPart.Vertex modelpart$vertex1 = new ModelPart.Vertex(f, f1, originZ, 8.0F, 8.0F);
            ModelPart.Vertex modelpart$vertex2 = new ModelPart.Vertex(originX, f1, originZ, 8.0F, 0.0F);
            ModelPart.Vertex modelpart$vertex3 = new ModelPart.Vertex(originX, originY, f2, 0.0F, 0.0F);
            ModelPart.Vertex modelpart$vertex4 = new ModelPart.Vertex(f, originY, f2, 0.0F, 8.0F);
            ModelPart.Vertex modelpart$vertex5 = new ModelPart.Vertex(f, f1, f2, 8.0F, 8.0F);
            ModelPart.Vertex modelpart$vertex6 = new ModelPart.Vertex(originX, f1, f2, 8.0F, 0.0F);
            float f4 = (float)texCoordU;
            float f5 = (float)texCoordU + dimensionZ;
            float f6 = (float)texCoordU + dimensionZ + dimensionX;
            float f7 = (float)texCoordU + dimensionZ + dimensionX + dimensionX;
            float f8 = (float)texCoordU + dimensionZ + dimensionX + dimensionZ;
            float f9 = (float)texCoordU + dimensionZ + dimensionX + dimensionZ + dimensionX;
            float f10 = (float)texCoordV;
            float f11 = (float)texCoordV + dimensionZ;
            float f12 = (float)texCoordV + dimensionZ + dimensionY;
            int i = 0;
            if (visibleFaces.contains(Direction.DOWN)) {
                this.polygons[i++] = new ModelPart.Polygon(
                    new ModelPart.Vertex[]{modelpart$vertex4, modelpart$vertex3, modelpart$vertex7, modelpart$vertex},
                    f5,
                    f10,
                    f6,
                    f11,
                    texScaleU,
                    texScaleV,
                    mirror,
                    Direction.DOWN
                );
            }

            if (visibleFaces.contains(Direction.UP)) {
                this.polygons[i++] = new ModelPart.Polygon(
                    new ModelPart.Vertex[]{modelpart$vertex1, modelpart$vertex2, modelpart$vertex6, modelpart$vertex5},
                    f6,
                    f11,
                    f7,
                    f10,
                    texScaleU,
                    texScaleV,
                    mirror,
                    Direction.UP
                );
            }

            if (visibleFaces.contains(Direction.WEST)) {
                this.polygons[i++] = new ModelPart.Polygon(
                    new ModelPart.Vertex[]{modelpart$vertex7, modelpart$vertex3, modelpart$vertex6, modelpart$vertex2},
                    f4,
                    f11,
                    f5,
                    f12,
                    texScaleU,
                    texScaleV,
                    mirror,
                    Direction.WEST
                );
            }

            if (visibleFaces.contains(Direction.NORTH)) {
                this.polygons[i++] = new ModelPart.Polygon(
                    new ModelPart.Vertex[]{modelpart$vertex, modelpart$vertex7, modelpart$vertex2, modelpart$vertex1},
                    f5,
                    f11,
                    f6,
                    f12,
                    texScaleU,
                    texScaleV,
                    mirror,
                    Direction.NORTH
                );
            }

            if (visibleFaces.contains(Direction.EAST)) {
                this.polygons[i++] = new ModelPart.Polygon(
                    new ModelPart.Vertex[]{modelpart$vertex4, modelpart$vertex, modelpart$vertex1, modelpart$vertex5},
                    f6,
                    f11,
                    f8,
                    f12,
                    texScaleU,
                    texScaleV,
                    mirror,
                    Direction.EAST
                );
            }

            if (visibleFaces.contains(Direction.SOUTH)) {
                this.polygons[i] = new ModelPart.Polygon(
                    new ModelPart.Vertex[]{modelpart$vertex3, modelpart$vertex4, modelpart$vertex5, modelpart$vertex6},
                    f8,
                    f11,
                    f9,
                    f12,
                    texScaleU,
                    texScaleV,
                    mirror,
                    Direction.SOUTH
                );
            }
        }

        public void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
            Matrix4f matrix4f = pose.pose();
            Vector3f vector3f = new Vector3f();

            for (ModelPart.Polygon modelpart$polygon : this.polygons) {
                Vector3f vector3f1 = pose.transformNormal(modelpart$polygon.normal, vector3f);
                float f = vector3f1.x();
                float f1 = vector3f1.y();
                float f2 = vector3f1.z();

                for (ModelPart.Vertex modelpart$vertex : modelpart$polygon.vertices) {
                    float f3 = modelpart$vertex.pos.x() / 16.0F;
                    float f4 = modelpart$vertex.pos.y() / 16.0F;
                    float f5 = modelpart$vertex.pos.z() / 16.0F;
                    Vector3f vector3f2 = matrix4f.transformPosition(f3, f4, f5, vector3f);
                    buffer.addVertex(
                        vector3f2.x(), vector3f2.y(), vector3f2.z(), color, modelpart$vertex.u, modelpart$vertex.v, packedOverlay, packedLight, f, f1, f2
                    );
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Polygon {
        public final ModelPart.Vertex[] vertices;
        public final Vector3f normal;

        public Polygon(
            ModelPart.Vertex[] vertices,
            float u1,
            float v1,
            float u2,
            float v2,
            float textureWidth,
            float textureHeight,
            boolean mirror,
            Direction direction
        ) {
            this.vertices = vertices;
            float f = 0.0F / textureWidth;
            float f1 = 0.0F / textureHeight;
            vertices[0] = vertices[0].remap(u2 / textureWidth - f, v1 / textureHeight + f1);
            vertices[1] = vertices[1].remap(u1 / textureWidth + f, v1 / textureHeight + f1);
            vertices[2] = vertices[2].remap(u1 / textureWidth + f, v2 / textureHeight - f1);
            vertices[3] = vertices[3].remap(u2 / textureWidth - f, v2 / textureHeight - f1);
            if (mirror) {
                int i = vertices.length;

                for (int j = 0; j < i / 2; j++) {
                    ModelPart.Vertex modelpart$vertex = vertices[j];
                    vertices[j] = vertices[i - 1 - j];
                    vertices[i - 1 - j] = modelpart$vertex;
                }
            }

            this.normal = direction.step();
            if (mirror) {
                this.normal.mul(-1.0F, 1.0F, 1.0F);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Vertex {
        public final Vector3f pos;
        public final float u;
        public final float v;

        public Vertex(float x, float y, float z, float u, float v) {
            this(new Vector3f(x, y, z), u, v);
        }

        public ModelPart.Vertex remap(float u, float v) {
            return new ModelPart.Vertex(this.pos, u, v);
        }

        public Vertex(Vector3f pos, float u, float v) {
            this.pos = pos;
            this.u = u;
            this.v = v;
        }
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface Visitor {
        void visit(PoseStack.Pose pose, String path, int index, ModelPart.Cube cube);
    }
}
