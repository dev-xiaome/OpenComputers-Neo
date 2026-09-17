package com.mojang.blaze3d.vertex;

import java.nio.ByteOrder;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public class BufferBuilder implements VertexConsumer {
    private static final long NOT_BUILDING = -1L;
    private static final long UNKNOWN_ELEMENT = -1L;
    private static final boolean IS_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private final ByteBufferBuilder buffer;
    private long vertexPointer = -1L;
    private int vertices;
    private final VertexFormat format;
    private final VertexFormat.Mode mode;
    private final boolean fastFormat;
    private final boolean fullFormat;
    private final int vertexSize;
    private final int initialElementsToFill;
    private final int[] offsetsByElement;
    private int elementsToFill;
    private boolean building = true;

    public BufferBuilder(ByteBufferBuilder buffer, VertexFormat.Mode mode, VertexFormat format) {
        if (!format.contains(VertexFormatElement.POSITION)) {
            throw new IllegalArgumentException("Cannot build mesh with no position element");
        } else {
            this.buffer = buffer;
            this.mode = mode;
            this.format = format;
            this.vertexSize = format.getVertexSize();
            this.initialElementsToFill = format.getElementsMask() & ~VertexFormatElement.POSITION.mask();
            this.offsetsByElement = format.getOffsetsByElement();
            boolean flag = format == DefaultVertexFormat.NEW_ENTITY;
            boolean flag1 = format == DefaultVertexFormat.BLOCK;
            this.fastFormat = flag || flag1;
            this.fullFormat = flag;
        }
    }

    @Nullable
    public MeshData build() {
        this.ensureBuilding();
        this.endLastVertex();
        MeshData meshdata = this.storeMesh();
        this.building = false;
        this.vertexPointer = -1L;
        return meshdata;
    }

    public MeshData buildOrThrow() {
        MeshData meshdata = this.build();
        if (meshdata == null) {
            throw new IllegalStateException("BufferBuilder was empty");
        } else {
            return meshdata;
        }
    }

    private void ensureBuilding() {
        if (!this.building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    private MeshData storeMesh() {
        if (this.vertices == 0) {
            return null;
        } else {
            ByteBufferBuilder.Result bytebufferbuilder$result = this.buffer.build();
            if (bytebufferbuilder$result == null) {
                return null;
            } else {
                int i = this.mode.indexCount(this.vertices);
                VertexFormat.IndexType vertexformat$indextype = VertexFormat.IndexType.least(this.vertices);
                return new MeshData(bytebufferbuilder$result, new MeshData.DrawState(this.format, this.vertices, i, this.mode, vertexformat$indextype));
            }
        }
    }

    private long beginVertex() {
        this.ensureBuilding();
        this.endLastVertex();
        this.vertices++;
        long i = this.buffer.reserve(this.vertexSize);
        this.vertexPointer = i;
        return i;
    }

    private long beginElement(VertexFormatElement element) {
        int i = this.elementsToFill;
        int j = i & ~element.mask();
        if (j == i) {
            return -1L;
        } else {
            this.elementsToFill = j;
            long k = this.vertexPointer;
            if (k == -1L) {
                throw new IllegalArgumentException("Not currently building vertex");
            } else {
                return k + (long)this.offsetsByElement[element.id()];
            }
        }
    }

    private void endLastVertex() {
        if (this.vertices != 0) {
            if (this.elementsToFill != 0) {
                String s = VertexFormatElement.elementsFromMask(this.elementsToFill).map(this.format::getElementName).collect(Collectors.joining(", "));
                throw new IllegalStateException("Missing elements in vertex: " + s);
            } else {
                if (this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP) {
                    long i = this.buffer.reserve(this.vertexSize);
                    MemoryUtil.memCopy(i - (long)this.vertexSize, i, (long)this.vertexSize);
                    this.vertices++;
                }
            }
        }
    }

    private static void putRgba(long pointer, int color) {
        int i = FastColor.ABGR32.fromArgb32(color);
        MemoryUtil.memPutInt(pointer, IS_LITTLE_ENDIAN ? i : Integer.reverseBytes(i));
    }

    private static void putPackedUv(long pointer, int packedUv) {
        if (IS_LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(pointer, packedUv);
        } else {
            MemoryUtil.memPutShort(pointer, (short)(packedUv & 65535));
            MemoryUtil.memPutShort(pointer + 2L, (short)(packedUv >> 16 & 65535));
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        long i = this.beginVertex() + (long)this.offsetsByElement[VertexFormatElement.POSITION.id()];
        this.elementsToFill = this.initialElementsToFill;
        MemoryUtil.memPutFloat(i, x);
        MemoryUtil.memPutFloat(i + 4L, y);
        MemoryUtil.memPutFloat(i + 8L, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        long i = this.beginElement(VertexFormatElement.COLOR);
        if (i != -1L) {
            MemoryUtil.memPutByte(i, (byte)red);
            MemoryUtil.memPutByte(i + 1L, (byte)green);
            MemoryUtil.memPutByte(i + 2L, (byte)blue);
            MemoryUtil.memPutByte(i + 3L, (byte)alpha);
        }

        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        long i = this.beginElement(VertexFormatElement.COLOR);
        if (i != -1L) {
            putRgba(i, color);
        }

        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        long i = this.beginElement(VertexFormatElement.UV0);
        if (i != -1L) {
            MemoryUtil.memPutFloat(i, u);
            MemoryUtil.memPutFloat(i + 4L, v);
        }

        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this.uvShort((short)u, (short)v, VertexFormatElement.UV1);
    }

    @Override
    public VertexConsumer setOverlay(int packedOverlay) {
        long i = this.beginElement(VertexFormatElement.UV1);
        if (i != -1L) {
            putPackedUv(i, packedOverlay);
        }

        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this.uvShort((short)u, (short)v, VertexFormatElement.UV2);
    }

    @Override
    public VertexConsumer setLight(int packedLight) {
        long i = this.beginElement(VertexFormatElement.UV2);
        if (i != -1L) {
            putPackedUv(i, packedLight);
        }

        return this;
    }

    private VertexConsumer uvShort(short u, short v, VertexFormatElement element) {
        long i = this.beginElement(element);
        if (i != -1L) {
            MemoryUtil.memPutShort(i, u);
            MemoryUtil.memPutShort(i + 2L, v);
        }

        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        long i = this.beginElement(VertexFormatElement.NORMAL);
        if (i != -1L) {
            MemoryUtil.memPutByte(i, normalIntValue(normalX));
            MemoryUtil.memPutByte(i + 1L, normalIntValue(normalY));
            MemoryUtil.memPutByte(i + 2L, normalIntValue(normalZ));
        }

        return this;
    }

    private static byte normalIntValue(float value) {
        return (byte)((int)(Mth.clamp(value, -1.0F, 1.0F) * 127.0F) & 0xFF);
    }

    @Override
    public void addVertex(
        float x,
        float y,
        float z,
        int color,
        float u,
        float v,
        int packedOverlay,
        int packedLight,
        float normalX,
        float normalY,
        float normalZ
    ) {
        if (this.fastFormat) {
            long i = this.beginVertex();
            MemoryUtil.memPutFloat(i + 0L, x);
            MemoryUtil.memPutFloat(i + 4L, y);
            MemoryUtil.memPutFloat(i + 8L, z);
            putRgba(i + 12L, color);
            MemoryUtil.memPutFloat(i + 16L, u);
            MemoryUtil.memPutFloat(i + 20L, v);
            long j;
            if (this.fullFormat) {
                putPackedUv(i + 24L, packedOverlay);
                j = i + 28L;
            } else {
                j = i + 24L;
            }

            putPackedUv(j + 0L, packedLight);
            MemoryUtil.memPutByte(j + 4L, normalIntValue(normalX));
            MemoryUtil.memPutByte(j + 5L, normalIntValue(normalY));
            MemoryUtil.memPutByte(j + 6L, normalIntValue(normalZ));
        } else {
            VertexConsumer.super.addVertex(
                x, y, z, color, u, v, packedOverlay, packedLight, normalX, normalY, normalZ
            );
        }
    }
}
