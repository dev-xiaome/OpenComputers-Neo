package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

public class WorldCoordinate {
    private static final char PREFIX_RELATIVE = '~';
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));
    private final boolean relative;
    private final double value;

    public WorldCoordinate(boolean relative, double value) {
        this.relative = relative;
        this.value = value;
    }

    public double get(double coord) {
        return this.relative ? this.value + coord : this.value;
    }

    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        } else {
            boolean flag = isRelative(reader);
            int i = reader.getCursor();
            double d0 = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
            String s = reader.getString().substring(i, reader.getCursor());
            if (flag && s.isEmpty()) {
                return new WorldCoordinate(true, 0.0);
            } else {
                if (!s.contains(".") && !flag && centerCorrect) {
                    d0 += 0.5;
                }

                return new WorldCoordinate(flag, d0);
            }
        }
    }

    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        } else if (!reader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(reader);
        } else {
            boolean flag = isRelative(reader);
            double d0;
            if (reader.canRead() && reader.peek() != ' ') {
                d0 = flag ? reader.readDouble() : (double)reader.readInt();
            } else {
                d0 = 0.0;
            }

            return new WorldCoordinate(flag, d0);
        }
    }

    public static boolean isRelative(StringReader reader) {
        boolean flag;
        if (reader.peek() == '~') {
            flag = true;
            reader.skip();
        } else {
            flag = false;
        }

        return flag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof WorldCoordinate worldcoordinate)) {
            return false;
        } else {
            return this.relative != worldcoordinate.relative ? false : Double.compare(worldcoordinate.value, this.value) == 0;
        }
    }

    @Override
    public int hashCode() {
        int i = this.relative ? 1 : 0;
        long j = Double.doubleToLongBits(this.value);
        return 31 * i + (int)(j ^ j >>> 32);
    }

    public boolean isRelative() {
        return this.relative;
    }
}
