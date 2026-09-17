package net.minecraft.advancements.critereon;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

public record WrappedMinMaxBounds(@Nullable Float min, @Nullable Float max) {
    public static final WrappedMinMaxBounds ANY = new WrappedMinMaxBounds(null, null);
    public static final SimpleCommandExceptionType ERROR_INTS_ONLY = new SimpleCommandExceptionType(Component.translatable("argument.range.ints"));

    public static WrappedMinMaxBounds exactly(float value) {
        return new WrappedMinMaxBounds(value, value);
    }

    public static WrappedMinMaxBounds between(float min, float max) {
        return new WrappedMinMaxBounds(min, max);
    }

    public static WrappedMinMaxBounds atLeast(float min) {
        return new WrappedMinMaxBounds(min, null);
    }

    public static WrappedMinMaxBounds atMost(float max) {
        return new WrappedMinMaxBounds(null, max);
    }

    public boolean matches(float value) {
        if (this.min != null && this.max != null && this.min > this.max && this.min > value && this.max < value) {
            return false;
        } else {
            return this.min != null && this.min > value ? false : this.max == null || !(this.max < value);
        }
    }

    public boolean matchesSqr(double value) {
        if (this.min != null
            && this.max != null
            && this.min > this.max
            && (double)(this.min * this.min) > value
            && (double)(this.max * this.max) < value) {
            return false;
        } else {
            return this.min != null && (double)(this.min * this.min) > value ? false : this.max == null || !((double)(this.max * this.max) < value);
        }
    }

    public JsonElement serializeToJson() {
        if (this == ANY) {
            return JsonNull.INSTANCE;
        } else if (this.min != null && this.max != null && this.min.equals(this.max)) {
            return new JsonPrimitive(this.min);
        } else {
            JsonObject jsonobject = new JsonObject();
            if (this.min != null) {
                jsonobject.addProperty("min", this.min);
            }

            if (this.max != null) {
                jsonobject.addProperty("max", this.min);
            }

            return jsonobject;
        }
    }

    public static WrappedMinMaxBounds fromJson(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ANY;
        } else if (GsonHelper.isNumberValue(json)) {
            float f2 = GsonHelper.convertToFloat(json, "value");
            return new WrappedMinMaxBounds(f2, f2);
        } else {
            JsonObject jsonobject = GsonHelper.convertToJsonObject(json, "value");
            Float f = jsonobject.has("min") ? GsonHelper.getAsFloat(jsonobject, "min") : null;
            Float f1 = jsonobject.has("max") ? GsonHelper.getAsFloat(jsonobject, "max") : null;
            return new WrappedMinMaxBounds(f, f1);
        }
    }

    public static WrappedMinMaxBounds fromReader(StringReader reader, boolean isFloatingPoint) throws CommandSyntaxException {
        return fromReader(reader, isFloatingPoint, p_164413_ -> p_164413_);
    }

    public static WrappedMinMaxBounds fromReader(StringReader reader, boolean isFloatingPoint, Function<Float, Float> valueFactory) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
        } else {
            int i = reader.getCursor();
            Float f = optionallyFormat(readNumber(reader, isFloatingPoint), valueFactory);
            Float f1;
            if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                reader.skip();
                reader.skip();
                f1 = optionallyFormat(readNumber(reader, isFloatingPoint), valueFactory);
                if (f == null && f1 == null) {
                    reader.setCursor(i);
                    throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
                }
            } else {
                if (!isFloatingPoint && reader.canRead() && reader.peek() == '.') {
                    reader.setCursor(i);
                    throw ERROR_INTS_ONLY.createWithContext(reader);
                }

                f1 = f;
            }

            if (f == null && f1 == null) {
                reader.setCursor(i);
                throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
            } else {
                return new WrappedMinMaxBounds(f, f1);
            }
        }
    }

    @Nullable
    private static Float readNumber(StringReader reader, boolean isFloatingPoint) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedNumber(reader, isFloatingPoint)) {
            reader.skip();
        }

        String s = reader.getString().substring(i, reader.getCursor());
        if (s.isEmpty()) {
            return null;
        } else {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException numberformatexception) {
                if (isFloatingPoint) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(reader, s);
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, s);
                }
            }
        }
    }

    private static boolean isAllowedNumber(StringReader reader, boolean isFloatingPoint) {
        char c0 = reader.peek();
        if ((c0 < '0' || c0 > '9') && c0 != '-') {
            return isFloatingPoint && c0 == '.' ? !reader.canRead(2) || reader.peek(1) != '.' : false;
        } else {
            return true;
        }
    }

    @Nullable
    private static Float optionallyFormat(@Nullable Float value, Function<Float, Float> valueFactory) {
        return value == null ? null : valueFactory.apply(value);
    }
}
