package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum Mirror implements StringRepresentable {
    NONE("none", OctahedralGroup.IDENTITY),
    LEFT_RIGHT("left_right", OctahedralGroup.INVERT_Z),
    FRONT_BACK("front_back", OctahedralGroup.INVERT_X);

    public static final Codec<Mirror> CODEC = StringRepresentable.fromEnum(Mirror::values);
    private final String id;
    private final Component symbol;
    private final OctahedralGroup rotation;

    private Mirror(String id, OctahedralGroup rotation) {
        this.id = id;
        this.symbol = Component.translatable("mirror." + id);
        this.rotation = rotation;
    }

    /**
     * Mirrors the given rotation like specified by this mirror. Rotations start at 0 and go up to rotationCount-1. 0 is front, rotationCount/2 is back.
     */
    public int mirror(int rotation, int rotationCount) {
        int i = rotationCount / 2;
        int j = rotation > i ? rotation - rotationCount : rotation;
        switch (this) {
            case LEFT_RIGHT:
                return (i - j + rotationCount) % rotationCount;
            case FRONT_BACK:
                return (rotationCount - j) % rotationCount;
            default:
                return rotation;
        }
    }

    /**
     * Determines the rotation that is equivalent to this mirror if the rotating object faces in the given direction
     */
    public Rotation getRotation(Direction facing) {
        Direction.Axis direction$axis = facing.getAxis();
        return (this != LEFT_RIGHT || direction$axis != Direction.Axis.Z) && (this != FRONT_BACK || direction$axis != Direction.Axis.X)
            ? Rotation.NONE
            : Rotation.CLOCKWISE_180;
    }

    /**
     * Mirror the given facing according to this mirror
     */
    public Direction mirror(Direction facing) {
        if (this == FRONT_BACK && facing.getAxis() == Direction.Axis.X) {
            return facing.getOpposite();
        } else {
            return this == LEFT_RIGHT && facing.getAxis() == Direction.Axis.Z ? facing.getOpposite() : facing;
        }
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Component symbol() {
        return this.symbol;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
