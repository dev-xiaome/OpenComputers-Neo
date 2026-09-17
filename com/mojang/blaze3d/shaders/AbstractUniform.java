package com.mojang.blaze3d.shaders;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public class AbstractUniform {
    public void set(float x) {
    }

    public void set(float x, float y) {
    }

    public void set(float x, float y, float z) {
    }

    public void set(float x, float y, float z, float w) {
    }

    public void setSafe(float x, float y, float z, float w) {
    }

    public void setSafe(int x, int y, int z, int w) {
    }

    public void set(int x) {
    }

    public void set(int x, int y) {
    }

    public void set(int x, int y, int z) {
    }

    public void set(int x, int y, int z, int w) {
    }

    public void set(float[] valueArray) {
    }

    public void set(Vector3f vector) {
    }

    public void set(Vector4f vector) {
    }

    public void setMat2x2(float m00, float m01, float m10, float m11) {
    }

    public void setMat2x3(float m00, float m01, float m02, float m10, float m11, float m12) {
    }

    public void setMat2x4(
        float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13
    ) {
    }

    public void setMat3x2(float m00, float m01, float m10, float m11, float m20, float m21) {
    }

    public void setMat3x3(
        float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22
    ) {
    }

    public void setMat3x4(
        float m00,
        float m01,
        float m02,
        float m03,
        float m10,
        float m11,
        float m12,
        float m13,
        float m20,
        float m21,
        float m22,
        float m23
    ) {
    }

    public void setMat4x2(
        float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13
    ) {
    }

    public void setMat4x3(
        float m00,
        float m01,
        float m02,
        float m03,
        float m10,
        float m11,
        float m12,
        float m13,
        float m20,
        float m21,
        float m22,
        float m23
    ) {
    }

    public void setMat4x4(
        float m00,
        float m01,
        float m02,
        float m03,
        float m10,
        float m11,
        float m12,
        float m13,
        float m20,
        float m21,
        float m22,
        float m23,
        float m30,
        float m31,
        float m32,
        float m33
    ) {
    }

    public void set(Matrix4f matrix) {
    }

    public void set(Matrix3f matrix) {
    }
}
