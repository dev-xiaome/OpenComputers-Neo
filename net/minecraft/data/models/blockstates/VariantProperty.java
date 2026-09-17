package net.minecraft.data.models.blockstates;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Function;

public class VariantProperty<T> {
    final String key;
    final Function<T, JsonElement> serializer;

    public VariantProperty(String key, Function<T, JsonElement> serializer) {
        this.key = key;
        this.serializer = serializer;
    }

    public VariantProperty<T>.Value withValue(T value) {
        return new VariantProperty.Value(value);
    }

    @Override
    public String toString() {
        return this.key;
    }

    public class Value {
        private final T value;

        public Value(T value) {
            this.value = value;
        }

        public VariantProperty<T> getKey() {
            return VariantProperty.this;
        }

        public void addToVariant(JsonObject jsonObject) {
            jsonObject.add(VariantProperty.this.key, VariantProperty.this.serializer.apply(this.value));
        }

        @Override
        public String toString() {
            return VariantProperty.this.key + "=" + this.value;
        }
    }
}
