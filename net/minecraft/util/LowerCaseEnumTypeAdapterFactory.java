package net.minecraft.util;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

public class LowerCaseEnumTypeAdapterFactory implements TypeAdapterFactory {
    @Nullable
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        Class<T> oclass = (Class<T>)typeToken.getRawType();
        if (!oclass.isEnum()) {
            return null;
        } else {
            final Map<String, T> map = Maps.newHashMap();

            for (T t : oclass.getEnumConstants()) {
                map.put(this.toLowercase(t), t);
            }

            return new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter writer, T value) throws IOException {
                    if (value == null) {
                        writer.nullValue();
                    } else {
                        writer.value(LowerCaseEnumTypeAdapterFactory.this.toLowercase(value));
                    }
                }

                @Nullable
                @Override
                public T read(JsonReader reader) throws IOException {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                        return null;
                    } else {
                        return map.get(reader.nextString());
                    }
                }
            };
        }
    }

    String toLowercase(Object object) {
        return object instanceof Enum ? ((Enum)object).name().toLowerCase(Locale.ROOT) : object.toString().toLowerCase(Locale.ROOT);
    }
}
