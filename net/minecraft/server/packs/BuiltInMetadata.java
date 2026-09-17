package net.minecraft.server.packs;

import java.util.Map;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;

public class BuiltInMetadata {
    private static final BuiltInMetadata EMPTY = new BuiltInMetadata(Map.of());
    private final Map<MetadataSectionSerializer<?>, ?> values;

    private BuiltInMetadata(Map<MetadataSectionSerializer<?>, ?> values) {
        this.values = values;
    }

    public <T> T get(MetadataSectionSerializer<T> serializer) {
        return (T)this.values.get(serializer);
    }

    public static BuiltInMetadata of() {
        return EMPTY;
    }

    public static <T> BuiltInMetadata of(MetadataSectionSerializer<T> serializer, T value) {
        return new BuiltInMetadata(Map.of(serializer, value));
    }

    public static <T1, T2> BuiltInMetadata of(MetadataSectionSerializer<T1> serializer1, T1 value1, MetadataSectionSerializer<T2> serializer2, T2 value2) {
        return new BuiltInMetadata(Map.of(serializer1, value1, serializer2, (T1)value2));
    }
}
