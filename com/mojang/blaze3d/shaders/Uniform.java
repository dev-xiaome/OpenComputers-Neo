package com.mojang.blaze3d.shaders;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class Uniform extends AbstractUniform implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int UT_INT1 = 0;
    public static final int UT_INT2 = 1;
    public static final int UT_INT3 = 2;
    public static final int UT_INT4 = 3;
    public static final int UT_FLOAT1 = 4;
    public static final int UT_FLOAT2 = 5;
    public static final int UT_FLOAT3 = 6;
    public static final int UT_FLOAT4 = 7;
    public static final int UT_MAT2 = 8;
    public static final int UT_MAT3 = 9;
    public static final int UT_MAT4 = 10;
    private static final boolean TRANSPOSE_MATRICIES = false;
    private int location;
    private final int count;
    private final int type;
    private final IntBuffer intValues;
    private final FloatBuffer floatValues;
    private final String name;
    private boolean dirty;
    private final Shader parent;

    public Uniform(String name, int type, int count, Shader parent) {
        this.name = name;
        this.count = count;
        this.type = type;
        this.parent = parent;
        if (type <= 3) {
            this.intValues = MemoryUtil.memAllocInt(count);
            this.floatValues = null;
        } else {
            this.intValues = null;
            this.floatValues = MemoryUtil.memAllocFloat(count);
        }

        this.location = -1;
        this.markDirty();
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        return GlStateManager._glGetUniformLocation(program, name);
    }

    public static void uploadInteger(int location, int value) {
        RenderSystem.glUniform1i(location, value);
    }

    public static int glGetAttribLocation(int program, CharSequence name) {
        return GlStateManager._glGetAttribLocation(program, name);
    }

    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        GlStateManager._glBindAttribLocation(program, index, name);
    }

    @Override
    public void close() {
        if (this.intValues != null) {
            MemoryUtil.memFree(this.intValues);
        }

        if (this.floatValues != null) {
            MemoryUtil.memFree(this.floatValues);
        }
    }

    private void markDirty() {
        this.dirty = true;
        if (this.parent != null) {
            this.parent.markDirty();
        }
    }

    public static int getTypeFromString(String typeName) {
        int i = -1;
        if ("int".equals(typeName)) {
            i = 0;
        } else if ("float".equals(typeName)) {
            i = 4;
        } else if (typeName.startsWith("matrix")) {
            if (typeName.endsWith("2x2")) {
                i = 8;
            } else if (typeName.endsWith("3x3")) {
                i = 9;
            } else if (typeName.endsWith("4x4")) {
                i = 10;
            }
        }

        return i;
    }

    public void setLocation(int location) {
        this.location = location;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public final void set(float x) {
        this.floatValues.position(0);
        this.floatValues.put(0, x);
        this.markDirty();
    }

    @Override
    public final void set(float x, float y) {
        this.floatValues.position(0);
        this.floatValues.put(0, x);
        this.floatValues.put(1, y);
        this.markDirty();
    }

    public final void set(int index, float value) {
        this.floatValues.position(0);
        this.floatValues.put(index, value);
        this.markDirty();
    }

    @Override
    public final void set(float x, float y, float z) {
        this.floatValues.position(0);
        this.floatValues.put(0, x);
        this.floatValues.put(1, y);
        this.floatValues.put(2, z);
        this.markDirty();
    }

    @Override
    public final void set(Vector3f vector) {
        this.floatValues.position(0);
        vector.get(this.floatValues);
        this.markDirty();
    }

    @Override
    public final void set(float x, float y, float z, float w) {
        this.floatValues.position(0);
        this.floatValues.put(x);
        this.floatValues.put(y);
        this.floatValues.put(z);
        this.floatValues.put(w);
        this.floatValues.flip();
        this.markDirty();
    }

    @Override
    public final void set(Vector4f vector) {
        this.floatValues.position(0);
        vector.get(this.floatValues);
        this.markDirty();
    }

    @Override
    public final void setSafe(float x, float y, float z, float w) {
        this.floatValues.position(0);
        if (this.type >= 4) {
            this.floatValues.put(0, x);
        }

        if (this.type >= 5) {
            this.floatValues.put(1, y);
        }

        if (this.type >= 6) {
            this.floatValues.put(2, z);
        }

        if (this.type >= 7) {
            this.floatValues.put(3, w);
        }

        this.markDirty();
    }

    @Override
    public final void setSafe(int x, int y, int z, int w) {
        this.intValues.position(0);
        if (this.type >= 0) {
            this.intValues.put(0, x);
        }

        if (this.type >= 1) {
            this.intValues.put(1, y);
        }

        if (this.type >= 2) {
            this.intValues.put(2, z);
        }

        if (this.type >= 3) {
            this.intValues.put(3, w);
        }

        this.markDirty();
    }

    @Override
    public final void set(int x) {
        this.intValues.position(0);
        this.intValues.put(0, x);
        this.markDirty();
    }

    @Override
    public final void set(int x, int y) {
        this.intValues.position(0);
        this.intValues.put(0, x);
        this.intValues.put(1, y);
        this.markDirty();
    }

    @Override
    public final void set(int x, int y, int z) {
        this.intValues.position(0);
        this.intValues.put(0, x);
        this.intValues.put(1, y);
        this.intValues.put(2, z);
        this.markDirty();
    }

    @Override
    public final void set(int x, int y, int z, int w) {
        this.intValues.position(0);
        this.intValues.put(0, x);
        this.intValues.put(1, y);
        this.intValues.put(2, z);
        this.intValues.put(3, w);
        this.markDirty();
    }

    @Override
    public final void set(float[] valueArray) {
        if (valueArray.length < this.count) {
            LOGGER.warn("Uniform.set called with a too-small value array (expected {}, got {}). Ignoring.", this.count, valueArray.length);
        } else {
            this.floatValues.position(0);
            this.floatValues.put(valueArray);
            this.floatValues.position(0);
            this.markDirty();
        }
    }

    @Override
    public final void setMat2x2(float m00, float m01, float m10, float m11) {
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m10);
        this.floatValues.put(3, m11);
        this.markDirty();
    }

    @Override
    public final void setMat2x3(float m00, float m01, float m02, float m10, float m11, float m12) {
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m10);
        this.floatValues.put(4, m11);
        this.floatValues.put(5, m12);
        this.markDirty();
    }

    @Override
    public final void setMat2x4(
        float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13
    ) {
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m03);
        this.floatValues.put(4, m10);
        this.floatValues.put(5, m11);
        this.floatValues.put(6, m12);
        this.floatValues.put(7, m13);
        this.markDirty();
    }

    @Override
    public final void setMat3x2(float m00, float m01, float m10, float m11, float m20, float m21) {
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m10);
        this.floatValues.put(3, m11);
        this.floatValues.put(4, m20);
        this.floatValues.put(5, m21);
        this.markDirty();
    }

    @Override
    public final void setMat3x3(
        float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22
    ) {
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m10);
        this.floatValues.put(4, m11);
        this.floatValues.put(5, m12);
        this.floatValues.put(6, m20);
        this.floatValues.put(7, m21);
        this.floatValues.put(8, m22);
        this.markDirty();
    }

    @Override
    public final void setMat3x4(
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
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m03);
        this.floatValues.put(4, m10);
        this.floatValues.put(5, m11);
        this.floatValues.put(6, m12);
        this.floatValues.put(7, m13);
        this.floatValues.put(8, m20);
        this.floatValues.put(9, m21);
        this.floatValues.put(10, m22);
        this.floatValues.put(11, m23);
        this.markDirty();
    }

    @Override
    public final void setMat4x2(
        float m00, float m01, float m02, float m03, float m10, float m11, float m12, float m13
    ) {
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m03);
        this.floatValues.put(4, m10);
        this.floatValues.put(5, m11);
        this.floatValues.put(6, m12);
        this.floatValues.put(7, m13);
        this.markDirty();
    }

    @Override
    public final void setMat4x3(
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
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m03);
        this.floatValues.put(4, m10);
        this.floatValues.put(5, m11);
        this.floatValues.put(6, m12);
        this.floatValues.put(7, m13);
        this.floatValues.put(8, m20);
        this.floatValues.put(9, m21);
        this.floatValues.put(10, m22);
        this.floatValues.put(11, m23);
        this.markDirty();
    }

    @Override
    public final void setMat4x4(
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
        this.floatValues.position(0);
        this.floatValues.put(0, m00);
        this.floatValues.put(1, m01);
        this.floatValues.put(2, m02);
        this.floatValues.put(3, m03);
        this.floatValues.put(4, m10);
        this.floatValues.put(5, m11);
        this.floatValues.put(6, m12);
        this.floatValues.put(7, m13);
        this.floatValues.put(8, m20);
        this.floatValues.put(9, m21);
        this.floatValues.put(10, m22);
        this.floatValues.put(11, m23);
        this.floatValues.put(12, m30);
        this.floatValues.put(13, m31);
        this.floatValues.put(14, m32);
        this.floatValues.put(15, m33);
        this.markDirty();
    }

    @Override
    public final void set(Matrix4f matrix) {
        this.floatValues.position(0);
        matrix.get(this.floatValues);
        this.markDirty();
    }

    @Override
    public final void set(Matrix3f matrix) {
        this.floatValues.position(0);
        matrix.get(this.floatValues);
        this.markDirty();
    }

    public void upload() {
        if (!this.dirty) {
        }

        this.dirty = false;
        if (this.type <= 3) {
            this.uploadAsInteger();
        } else if (this.type <= 7) {
            this.uploadAsFloat();
        } else {
            if (this.type > 10) {
                LOGGER.warn("Uniform.upload called, but type value ({}) is not a valid type. Ignoring.", this.type);
                return;
            }

            this.uploadAsMatrix();
        }
    }

    private void uploadAsInteger() {
        this.intValues.rewind();
        switch (this.type) {
            case 0:
                RenderSystem.glUniform1(this.location, this.intValues);
                break;
            case 1:
                RenderSystem.glUniform2(this.location, this.intValues);
                break;
            case 2:
                RenderSystem.glUniform3(this.location, this.intValues);
                break;
            case 3:
                RenderSystem.glUniform4(this.location, this.intValues);
                break;
            default:
                LOGGER.warn("Uniform.upload called, but count value ({}) is  not in the range of 1 to 4. Ignoring.", this.count);
        }
    }

    private void uploadAsFloat() {
        this.floatValues.rewind();
        switch (this.type) {
            case 4:
                RenderSystem.glUniform1(this.location, this.floatValues);
                break;
            case 5:
                RenderSystem.glUniform2(this.location, this.floatValues);
                break;
            case 6:
                RenderSystem.glUniform3(this.location, this.floatValues);
                break;
            case 7:
                RenderSystem.glUniform4(this.location, this.floatValues);
                break;
            default:
                LOGGER.warn("Uniform.upload called, but count value ({}) is not in the range of 1 to 4. Ignoring.", this.count);
        }
    }

    private void uploadAsMatrix() {
        this.floatValues.clear();
        switch (this.type) {
            case 8:
                RenderSystem.glUniformMatrix2(this.location, false, this.floatValues);
                break;
            case 9:
                RenderSystem.glUniformMatrix3(this.location, false, this.floatValues);
                break;
            case 10:
                RenderSystem.glUniformMatrix4(this.location, false, this.floatValues);
        }
    }

    public int getLocation() {
        return this.location;
    }

    public int getCount() {
        return this.count;
    }

    public int getType() {
        return this.type;
    }

    public IntBuffer getIntBuffer() {
        return this.intValues;
    }

    public FloatBuffer getFloatBuffer() {
        return this.floatValues;
    }
}
