package com.mojang.blaze3d.vertex;

import it.unimi.dsi.fastutil.ints.IntConsumer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.mutable.MutableLong;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public class MeshData implements AutoCloseable {
    private final ByteBufferBuilder.Result vertexBuffer;
    @Nullable
    private ByteBufferBuilder.Result indexBuffer;
    private final MeshData.DrawState drawState;

    public MeshData(ByteBufferBuilder.Result vertexBuffer, MeshData.DrawState drawState) {
        this.vertexBuffer = vertexBuffer;
        this.drawState = drawState;
    }

    private static Vector3f[] unpackQuadCentroids(ByteBuffer byteBuffer, int vertexCount, VertexFormat format) {
        int i = format.getOffset(VertexFormatElement.POSITION);
        if (i == -1) {
            throw new IllegalArgumentException("Cannot identify quad centers with no position element");
        } else {
            FloatBuffer floatbuffer = byteBuffer.asFloatBuffer();
            int j = format.getVertexSize() / 4;
            int k = j * 4;
            int l = vertexCount / 4;
            Vector3f[] avector3f = new Vector3f[l];

            for (int i1 = 0; i1 < l; i1++) {
                int j1 = i1 * k + i;
                int k1 = j1 + j * 2;
                float f = floatbuffer.get(j1 + 0);
                float f1 = floatbuffer.get(j1 + 1);
                float f2 = floatbuffer.get(j1 + 2);
                float f3 = floatbuffer.get(k1 + 0);
                float f4 = floatbuffer.get(k1 + 1);
                float f5 = floatbuffer.get(k1 + 2);
                avector3f[i1] = new Vector3f((f + f3) / 2.0F, (f1 + f4) / 2.0F, (f2 + f5) / 2.0F);
            }

            return avector3f;
        }
    }

    public ByteBuffer vertexBuffer() {
        return this.vertexBuffer.byteBuffer();
    }

    @Nullable
    public ByteBuffer indexBuffer() {
        return this.indexBuffer != null ? this.indexBuffer.byteBuffer() : null;
    }

    public MeshData.DrawState drawState() {
        return this.drawState;
    }

    @Nullable
    public MeshData.SortState sortQuads(ByteBufferBuilder bufferBuilder, VertexSorting sorting) {
        if (this.drawState.mode() != VertexFormat.Mode.QUADS) {
            return null;
        } else {
            Vector3f[] avector3f = unpackQuadCentroids(this.vertexBuffer.byteBuffer(), this.drawState.vertexCount(), this.drawState.format());
            MeshData.SortState meshdata$sortstate = new MeshData.SortState(avector3f, this.drawState.indexType());
            this.indexBuffer = meshdata$sortstate.buildSortedIndexBuffer(bufferBuilder, sorting);
            return meshdata$sortstate;
        }
    }

    @Override
    public void close() {
        this.vertexBuffer.close();
        if (this.indexBuffer != null) {
            this.indexBuffer.close();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record DrawState(VertexFormat format, int vertexCount, int indexCount, VertexFormat.Mode mode, VertexFormat.IndexType indexType) {
    }

    @OnlyIn(Dist.CLIENT)
    public static record SortState(Vector3f[] centroids, VertexFormat.IndexType indexType) {
        @Nullable
        public ByteBufferBuilder.Result buildSortedIndexBuffer(ByteBufferBuilder bufferBuilder, VertexSorting sorting) {
            int[] aint = sorting.sort(this.centroids);
            long i = bufferBuilder.reserve(aint.length * 6 * this.indexType.bytes);
            IntConsumer intconsumer = this.indexWriter(i, this.indexType);

            for (int j : aint) {
                intconsumer.accept(j * 4 + 0);
                intconsumer.accept(j * 4 + 1);
                intconsumer.accept(j * 4 + 2);
                intconsumer.accept(j * 4 + 2);
                intconsumer.accept(j * 4 + 3);
                intconsumer.accept(j * 4 + 0);
            }

            return bufferBuilder.build();
        }

        private IntConsumer indexWriter(long index, VertexFormat.IndexType type) {
            MutableLong mutablelong = new MutableLong(index);

            return switch (type) {
                case SHORT -> p_350656_ -> MemoryUtil.memPutShort(mutablelong.getAndAdd(2L), (short)p_350656_);
                case INT -> p_350913_ -> MemoryUtil.memPutInt(mutablelong.getAndAdd(4L), p_350913_);
            };
        }
    }
}
