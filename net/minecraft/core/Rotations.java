package net.minecraft.core;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public class Rotations {
    public static final StreamCodec<ByteBuf, Rotations> STREAM_CODEC = new StreamCodec<ByteBuf, Rotations>() {
        public Rotations decode(ByteBuf p_320504_) {
            return new Rotations(p_320504_.readFloat(), p_320504_.readFloat(), p_320504_.readFloat());
        }

        public void encode(ByteBuf p_320561_, Rotations p_320041_) {
            p_320561_.writeFloat(p_320041_.x);
            p_320561_.writeFloat(p_320041_.y);
            p_320561_.writeFloat(p_320041_.z);
        }
    };
    /**
     * Rotation on the X axis
     */
    protected final float x;
    /**
     * Rotation on the Y axis
     */
    protected final float y;
    /**
     * Rotation on the Z axis
     */
    protected final float z;

    public Rotations(float x, float y, float z) {
        this.x = !Float.isInfinite(x) && !Float.isNaN(x) ? x % 360.0F : 0.0F;
        this.y = !Float.isInfinite(y) && !Float.isNaN(y) ? y % 360.0F : 0.0F;
        this.z = !Float.isInfinite(z) && !Float.isNaN(z) ? z % 360.0F : 0.0F;
    }

    public Rotations(ListTag tag) {
        this(tag.getFloat(0), tag.getFloat(1), tag.getFloat(2));
    }

    public ListTag save() {
        ListTag listtag = new ListTag();
        listtag.add(FloatTag.valueOf(this.x));
        listtag.add(FloatTag.valueOf(this.y));
        listtag.add(FloatTag.valueOf(this.z));
        return listtag;
    }

    @Override
    public boolean equals(Object other) {
        return !(other instanceof Rotations rotations) ? false : this.x == rotations.x && this.y == rotations.y && this.z == rotations.z;
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getZ() {
        return this.z;
    }

    public float getWrappedX() {
        return Mth.wrapDegrees(this.x);
    }

    public float getWrappedY() {
        return Mth.wrapDegrees(this.y);
    }

    public float getWrappedZ() {
        return Mth.wrapDegrees(this.z);
    }
}
