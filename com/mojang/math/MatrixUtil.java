package com.mojang.math;

import org.apache.commons.lang3.tuple.Triple;
import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class MatrixUtil {
    private static final float G = 3.0F + 2.0F * Math.sqrt(2.0F);
    private static final GivensParameters PI_4 = GivensParameters.fromPositiveAngle((float) (java.lang.Math.PI / 4));

    private MatrixUtil() {
    }

    public static Matrix4f mulComponentWise(Matrix4f matrix, float scalar) {
        return matrix.set(
            matrix.m00() * scalar,
            matrix.m01() * scalar,
            matrix.m02() * scalar,
            matrix.m03() * scalar,
            matrix.m10() * scalar,
            matrix.m11() * scalar,
            matrix.m12() * scalar,
            matrix.m13() * scalar,
            matrix.m20() * scalar,
            matrix.m21() * scalar,
            matrix.m22() * scalar,
            matrix.m23() * scalar,
            matrix.m30() * scalar,
            matrix.m31() * scalar,
            matrix.m32() * scalar,
            matrix.m33() * scalar
        );
    }

    private static GivensParameters approxGivensQuat(float p_276275_, float p_276276_, float p_276282_) {
        float f = 2.0F * (p_276275_ - p_276282_);
        return G * p_276276_ * p_276276_ < f * f ? GivensParameters.fromUnnormalized(p_276276_, f) : PI_4;
    }

    private static GivensParameters qrGivensQuat(float input1, float input2) {
        float f = (float)java.lang.Math.hypot((double)input1, (double)input2);
        float f1 = f > 1.0E-6F ? input2 : 0.0F;
        float f2 = Math.abs(input1) + Math.max(f, 1.0E-6F);
        if (input1 < 0.0F) {
            float f3 = f1;
            f1 = f2;
            f2 = f3;
        }

        return GivensParameters.fromUnnormalized(f1, f2);
    }

    private static void similarityTransform(Matrix3f input, Matrix3f tempStorage) {
        input.mul(tempStorage);
        tempStorage.transpose();
        tempStorage.mul(input);
        input.set(tempStorage);
    }

    private static void stepJacobi(Matrix3f input, Matrix3f tempStorage, Quaternionf resultEigenvector, Quaternionf resultEigenvalue) {
        if (input.m01 * input.m01 + input.m10 * input.m10 > 1.0E-6F) {
            GivensParameters givensparameters = approxGivensQuat(input.m00, 0.5F * (input.m01 + input.m10), input.m11);
            Quaternionf quaternionf = givensparameters.aroundZ(resultEigenvector);
            resultEigenvalue.mul(quaternionf);
            givensparameters.aroundZ(tempStorage);
            similarityTransform(input, tempStorage);
        }

        if (input.m02 * input.m02 + input.m20 * input.m20 > 1.0E-6F) {
            GivensParameters givensparameters1 = approxGivensQuat(input.m00, 0.5F * (input.m02 + input.m20), input.m22).inverse();
            Quaternionf quaternionf1 = givensparameters1.aroundY(resultEigenvector);
            resultEigenvalue.mul(quaternionf1);
            givensparameters1.aroundY(tempStorage);
            similarityTransform(input, tempStorage);
        }

        if (input.m12 * input.m12 + input.m21 * input.m21 > 1.0E-6F) {
            GivensParameters givensparameters2 = approxGivensQuat(input.m11, 0.5F * (input.m12 + input.m21), input.m22);
            Quaternionf quaternionf2 = givensparameters2.aroundX(resultEigenvector);
            resultEigenvalue.mul(quaternionf2);
            givensparameters2.aroundX(tempStorage);
            similarityTransform(input, tempStorage);
        }
    }

    public static Quaternionf eigenvalueJacobi(Matrix3f input, int iterations) {
        Quaternionf quaternionf = new Quaternionf();
        Matrix3f matrix3f = new Matrix3f();
        Quaternionf quaternionf1 = new Quaternionf();

        for (int i = 0; i < iterations; i++) {
            stepJacobi(input, matrix3f, quaternionf1, quaternionf);
        }

        quaternionf.normalize();
        return quaternionf;
    }

    public static Triple<Quaternionf, Vector3f, Quaternionf> svdDecompose(Matrix3f matrix) {
        Matrix3f matrix3f = new Matrix3f(matrix);
        matrix3f.transpose();
        matrix3f.mul(matrix);
        Quaternionf quaternionf = eigenvalueJacobi(matrix3f, 5);
        float f = matrix3f.m00;
        float f1 = matrix3f.m11;
        boolean flag = (double)f < 1.0E-6;
        boolean flag1 = (double)f1 < 1.0E-6;
        Matrix3f matrix3f1 = matrix.rotate(quaternionf);
        Quaternionf quaternionf1 = new Quaternionf();
        Quaternionf quaternionf2 = new Quaternionf();
        GivensParameters givensparameters;
        if (flag) {
            givensparameters = qrGivensQuat(matrix3f1.m11, -matrix3f1.m10);
        } else {
            givensparameters = qrGivensQuat(matrix3f1.m00, matrix3f1.m01);
        }

        Quaternionf quaternionf3 = givensparameters.aroundZ(quaternionf2);
        Matrix3f matrix3f2 = givensparameters.aroundZ(matrix3f);
        quaternionf1.mul(quaternionf3);
        matrix3f2.transpose().mul(matrix3f1);
        if (flag) {
            givensparameters = qrGivensQuat(matrix3f2.m22, -matrix3f2.m20);
        } else {
            givensparameters = qrGivensQuat(matrix3f2.m00, matrix3f2.m02);
        }

        givensparameters = givensparameters.inverse();
        Quaternionf quaternionf4 = givensparameters.aroundY(quaternionf2);
        Matrix3f matrix3f3 = givensparameters.aroundY(matrix3f1);
        quaternionf1.mul(quaternionf4);
        matrix3f3.transpose().mul(matrix3f2);
        if (flag1) {
            givensparameters = qrGivensQuat(matrix3f3.m22, -matrix3f3.m21);
        } else {
            givensparameters = qrGivensQuat(matrix3f3.m11, matrix3f3.m12);
        }

        Quaternionf quaternionf5 = givensparameters.aroundX(quaternionf2);
        Matrix3f matrix3f4 = givensparameters.aroundX(matrix3f2);
        quaternionf1.mul(quaternionf5);
        matrix3f4.transpose().mul(matrix3f3);
        Vector3f vector3f = new Vector3f(matrix3f4.m00, matrix3f4.m11, matrix3f4.m22);
        return Triple.of(quaternionf1, vector3f, quaternionf.conjugate());
    }

    public static boolean isPureTranslation(Matrix4f matrix) {
        return (matrix.properties() & 8) != 0;
    }

    public static boolean isOrthonormal(Matrix4f matrix) {
        return (matrix.properties() & 16) != 0;
    }
}
