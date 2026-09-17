package net.minecraft.server.packs.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

public interface MetadataSectionType<T> extends MetadataSectionSerializer<T> {
    JsonObject toJson(T data);

    static <T> MetadataSectionType<T> fromCodec(final String name, final Codec<T> codec) {
        return new MetadataSectionType<T>() {
            @Override
            public String getMetadataSectionName() {
                return name;
            }

            @Override
            public T fromJson(JsonObject p_249450_) {
                return codec.parse(JsonOps.INSTANCE, p_249450_).getOrThrow(JsonParseException::new);
            }

            @Override
            public JsonObject toJson(T p_250691_) {
                return codec.encodeStart(JsonOps.INSTANCE, p_250691_).getOrThrow(IllegalArgumentException::new).getAsJsonObject();
            }
        };
    }
}
