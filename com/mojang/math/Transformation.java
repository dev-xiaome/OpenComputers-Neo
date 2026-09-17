package com.mojang.math;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.ExtraCodecs;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Transformation implements net.neoforged.neoforge.common.extensions.ITransformationExtension {
    private final Matrix4f matrix;
    public static final Codec<Transformation> CODEC = RecordCodecBuilder.create(
        p_269604_ -> p_269604_.group(
                    ExtraCodecs.VECTOR3F.fieldOf("translation").forGetter(p_269599_ -> p_269599_.translation),
                    ExtraCodecs.QUATERNIONF.fieldOf("left_rotation").forGetter(p_269600_ -> p_269600_.leftRotation),
                    ExtraCodecs.VECTOR3F.fieldOf("scale").forGetter(p_269603_ -> p_269603_.scale),
                    ExtraCodecs.QUATERNIONF.fieldOf("right_rotation").forGetter(p_269598_ -> p_269598_.rightRotation)
                )
                .apply(p_269604_, Transformation::new)
    );
    public static final Codec<Transformation> EXTENDED_CODEC = Codec.withAlternative(
        CODEC, ExtraCodecs.MATRIX4F.xmap(Transformation::new, Transformation::getMatrix)
    );
    private boolean decomposed;
    @Nullable
    private Vector3f translation;
    @Nullable
    private Quaternionf leftRotation;
    @Nullable
    private Vector3f scale;
    @Nullable
    private Quaternionf rightRotation;
    private static final Transformation IDENTITY = Util.make(() -> {
        Transformation transformation = new Transformation(new Matrix4f());
        transformation.translation = new Vector3f();
        transformation.leftRotation = new Quaternionf();
        transformation.scale = new Vector3f(1.0F, 1.0F, 1.0F);
        transformation.rightRotation = new Quaternionf();
        transformation.decomposed = true;
        return transformation;
    });

    public Transformation(@Nullable Matrix4f matrix) {
        if (matrix == null) {
            this.matrix = new Matrix4f();
        } else {
            this.matrix = matrix;
        }
    }

    public Transformation(@Nullable Vector3f translation, @Nullable Quaternionf leftRotation, @Nullable Vector3f scale, @Nullable Quaternionf rightRotation) {
        this.matrix = compose(translation, leftRotation, scale, rightRotation);
        this.translation = translation != null ? translation : new Vector3f();
        this.leftRotation = leftRotation != null ? leftRotation : new Quaternionf();
        this.scale = scale != null ? scale : new Vector3f(1.0F, 1.0F, 1.0F);
        this.rightRotation = rightRotation != null ? rightRotation : new Quaternionf();
        this.decomposed = true;
    }

    public static Transformation identity() {
        return IDENTITY;
    }

    public Transformation compose(Transformation other) {
        Matrix4f matrix4f = this.getMatrix();
        matrix4f.mul(other.getMatrix());
        return new Transformation(matrix4f);
    }

    @Nullable
    public Transformation inverse() {
        if (this == IDENTITY) {
            return this;
        } else {
            Matrix4f matrix4f = this.getMatrix().invert();
            return matrix4f.isFinite() ? new Transformation(matrix4f) : null;
        }
    }

    private void ensureDecomposed() {
        if (!this.decomposed) {
            float f = 1.0F / this.matrix.m33();
            Triple<Quaternionf, Vector3f, Quaternionf> triple = MatrixUtil.svdDecompose(new Matrix3f(this.matrix).scale(f));
            this.translation = this.matrix.getTranslation(new Vector3f()).mul(f);
            this.leftRotation = new Quaternionf(triple.getLeft());
            this.scale = new Vector3f(triple.getMiddle());
            this.rightRotation = new Quaternionf(triple.getRight());
            this.decomposed = true;
        }
    }

    private static Matrix4f compose(
        @Nullable Vector3f translation, @Nullable Quaternionf leftRotation, @Nullable Vector3f scale, @Nullable Quaternionf rightRotation
    ) {
        Matrix4f matrix4f = new Matrix4f();
        if (translation != null) {
            matrix4f.translation(translation);
        }

        if (leftRotation != null) {
            matrix4f.rotate(leftRotation);
        }

        if (scale != null) {
            matrix4f.scale(scale);
        }

        if (rightRotation != null) {
            matrix4f.rotate(rightRotation);
        }

        return matrix4f;
    }

    public Matrix4f getMatrix() {
        return new Matrix4f(this.matrix);
    }

    public Vector3f getTranslation() {
        this.ensureDecomposed();
        return new Vector3f(this.translation);
    }

    public Quaternionf getLeftRotation() {
        this.ensureDecomposed();
        return new Quaternionf(this.leftRotation);
    }

    public Vector3f getScale() {
        this.ensureDecomposed();
        return new Vector3f(this.scale);
    }

    public Quaternionf getRightRotation() {
        this.ensureDecomposed();
        return new Quaternionf(this.rightRotation);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other != null && this.getClass() == other.getClass()) {
            Transformation transformation = (Transformation)other;
            return Objects.equals(this.matrix, transformation.matrix);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.matrix);
    }

    private Matrix3f normalTransform = null;
    public Matrix3f getNormalMatrix() {
        checkNormalTransform();
        return normalTransform;
    }
    private void checkNormalTransform() {
        if (normalTransform == null) {
            normalTransform = new Matrix3f(matrix);
            normalTransform.invert();
            normalTransform.transpose();
        }
    }

    public Transformation slerp(Transformation transformation, float delta) {
        Vector3f vector3f = this.getTranslation();
        Quaternionf quaternionf = this.getLeftRotation();
        Vector3f vector3f1 = this.getScale();
        Quaternionf quaternionf1 = this.getRightRotation();
        vector3f.lerp(transformation.getTranslation(), delta);
        quaternionf.slerp(transformation.getLeftRotation(), delta);
        vector3f1.lerp(transformation.getScale(), delta);
        quaternionf1.slerp(transformation.getRightRotation(), delta);
        return new Transformation(vector3f, quaternionf, vector3f1, quaternionf1);
    }
}
