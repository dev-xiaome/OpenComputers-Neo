package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class LocalCoordinates implements Coordinates {
    public static final char PREFIX_LOCAL_COORDINATE = '^';
    private final double left;
    private final double up;
    private final double forwards;

    public LocalCoordinates(double left, double up, double forwards) {
        this.left = left;
        this.up = up;
        this.forwards = forwards;
    }

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        Vec2 vec2 = source.getRotation();
        Vec3 vec3 = source.getAnchor().apply(source);
        float f = Mth.cos((vec2.y + 90.0F) * (float) (Math.PI / 180.0));
        float f1 = Mth.sin((vec2.y + 90.0F) * (float) (Math.PI / 180.0));
        float f2 = Mth.cos(-vec2.x * (float) (Math.PI / 180.0));
        float f3 = Mth.sin(-vec2.x * (float) (Math.PI / 180.0));
        float f4 = Mth.cos((-vec2.x + 90.0F) * (float) (Math.PI / 180.0));
        float f5 = Mth.sin((-vec2.x + 90.0F) * (float) (Math.PI / 180.0));
        Vec3 vec31 = new Vec3((double)(f * f2), (double)f3, (double)(f1 * f2));
        Vec3 vec32 = new Vec3((double)(f * f4), (double)f5, (double)(f1 * f4));
        Vec3 vec33 = vec31.cross(vec32).scale(-1.0);
        double d0 = vec31.x * this.forwards + vec32.x * this.up + vec33.x * this.left;
        double d1 = vec31.y * this.forwards + vec32.y * this.up + vec33.y * this.left;
        double d2 = vec31.z * this.forwards + vec32.z * this.up + vec33.z * this.left;
        return new Vec3(vec3.x + d0, vec3.y + d1, vec3.z + d2);
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        return Vec2.ZERO;
    }

    @Override
    public boolean isXRelative() {
        return true;
    }

    @Override
    public boolean isYRelative() {
        return true;
    }

    @Override
    public boolean isZRelative() {
        return true;
    }

    public static LocalCoordinates parse(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        double d0 = readDouble(reader, i);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            double d1 = readDouble(reader, i);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                double d2 = readDouble(reader, i);
                return new LocalCoordinates(d0, d1, d2);
            } else {
                reader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    private static double readDouble(StringReader reader, int start) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw WorldCoordinate.ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else if (reader.peek() != '^') {
            reader.setCursor(start);
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else {
            reader.skip();
            return reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof LocalCoordinates localcoordinates)
                ? false
                : this.left == localcoordinates.left && this.up == localcoordinates.up && this.forwards == localcoordinates.forwards;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.left, this.up, this.forwards);
    }
}
