package net.minecraft.world.level.block;

import com.mojang.math.OctahedralGroup;
import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

public enum Rotation implements StringRepresentable {
    NONE("none", OctahedralGroup.IDENTITY),
    CLOCKWISE_90("clockwise_90", OctahedralGroup.ROT_90_Y_NEG),
    CLOCKWISE_180("180", OctahedralGroup.ROT_180_FACE_XZ),
    COUNTERCLOCKWISE_90("counterclockwise_90", OctahedralGroup.ROT_90_Y_POS);

    public static final Codec<Rotation> CODEC = StringRepresentable.fromEnum(Rotation::values);
    private final String id;
    private final OctahedralGroup rotation;

    private Rotation(String id, OctahedralGroup rotation) {
        this.id = id;
        this.rotation = rotation;
    }

    public Rotation getRotated(Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                switch (this) {
                    case NONE:
                        return CLOCKWISE_180;
                    case CLOCKWISE_90:
                        return COUNTERCLOCKWISE_90;
                    case CLOCKWISE_180:
                        return NONE;
                    case COUNTERCLOCKWISE_90:
                        return CLOCKWISE_90;
                }
            case COUNTERCLOCKWISE_90:
                switch (this) {
                    case NONE:
                        return COUNTERCLOCKWISE_90;
                    case CLOCKWISE_90:
                        return NONE;
                    case CLOCKWISE_180:
                        return CLOCKWISE_90;
                    case COUNTERCLOCKWISE_90:
                        return CLOCKWISE_180;
                }
            case CLOCKWISE_90:
                switch (this) {
                    case NONE:
                        return CLOCKWISE_90;
                    case CLOCKWISE_90:
                        return CLOCKWISE_180;
                    case CLOCKWISE_180:
                        return COUNTERCLOCKWISE_90;
                    case COUNTERCLOCKWISE_90:
                        return NONE;
                }
            default:
                return this;
        }
    }

    public OctahedralGroup rotation() {
        return this.rotation;
    }

    public Direction rotate(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return facing;
        } else {
            switch (this) {
                case CLOCKWISE_90:
                    return facing.getClockWise();
                case CLOCKWISE_180:
                    return facing.getOpposite();
                case COUNTERCLOCKWISE_90:
                    return facing.getCounterClockWise();
                default:
                    return facing;
            }
        }
    }

    public int rotate(int rotation, int positionCount) {
        switch (this) {
            case CLOCKWISE_90:
                return (rotation + positionCount / 4) % positionCount;
            case CLOCKWISE_180:
                return (rotation + positionCount / 2) % positionCount;
            case COUNTERCLOCKWISE_90:
                return (rotation + positionCount * 3 / 4) % positionCount;
            default:
                return rotation;
        }
    }

    /**
     * Chooses a random rotation.
     */
    public static Rotation getRandom(RandomSource random) {
        return Util.getRandom(values(), random);
    }

    /**
     * Get a list of all rotations in random order.
     */
    public static List<Rotation> getShuffled(RandomSource random) {
        return Util.shuffledCopy(values(), random);
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
