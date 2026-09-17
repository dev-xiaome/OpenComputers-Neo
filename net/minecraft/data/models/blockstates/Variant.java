package net.minecraft.data.models.blockstates;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class Variant implements Supplier<JsonElement> {
    private final Map<VariantProperty<?>, VariantProperty<?>.Value> values = Maps.newLinkedHashMap();

    public <T> Variant with(VariantProperty<T> property, T value) {
        VariantProperty<?>.Value variantproperty = this.values.put(property, property.withValue(value));
        if (variantproperty != null) {
            throw new IllegalStateException("Replacing value of " + variantproperty + " with " + value);
        } else {
            return this;
        }
    }

    public static Variant variant() {
        return new Variant();
    }

    public static Variant merge(Variant definition1, Variant definition2) {
        Variant variant = new Variant();
        variant.values.putAll(definition1.values);
        variant.values.putAll(definition2.values);
        return variant;
    }

    public JsonElement get() {
        JsonObject jsonobject = new JsonObject();
        this.values.values().forEach(p_125507_ -> p_125507_.addToVariant(jsonobject));
        return jsonobject;
    }

    public static JsonElement convertList(List<Variant> definitions) {
        if (definitions.size() == 1) {
            return definitions.get(0).get();
        } else {
            JsonArray jsonarray = new JsonArray();
            definitions.forEach(p_125504_ -> jsonarray.add(p_125504_.get()));
            return jsonarray;
        }
    }
}
