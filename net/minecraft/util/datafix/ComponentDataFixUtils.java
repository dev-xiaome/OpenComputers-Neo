package net.minecraft.util.datafix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Optional;
import net.minecraft.util.GsonHelper;

public class ComponentDataFixUtils {
    private static final String EMPTY_CONTENTS = createTextComponentJson("");

    public static <T> Dynamic<T> createPlainTextComponent(DynamicOps<T> ops, String text) {
        String s = createTextComponentJson(text);
        return new Dynamic<>(ops, ops.createString(s));
    }

    public static <T> Dynamic<T> createEmptyComponent(DynamicOps<T> ops) {
        return new Dynamic<>(ops, ops.createString(EMPTY_CONTENTS));
    }

    private static String createTextComponentJson(String text) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("text", text);
        return GsonHelper.toStableString(jsonobject);
    }

    public static <T> Dynamic<T> createTranslatableComponent(DynamicOps<T> ops, String translationKey) {
        JsonObject jsonobject = new JsonObject();
        jsonobject.addProperty("translate", translationKey);
        return new Dynamic<>(ops, ops.createString(GsonHelper.toStableString(jsonobject)));
    }

    public static <T> Dynamic<T> wrapLiteralStringAsComponent(Dynamic<T> dynamic) {
        return DataFixUtils.orElse(dynamic.asString().map(p_304989_ -> createPlainTextComponent(dynamic.getOps(), p_304989_)).result(), dynamic);
    }

    public static Dynamic<?> rewriteFromLenient(Dynamic<?> dynamic) {
        Optional<String> optional = dynamic.asString().result();
        if (optional.isEmpty()) {
            return dynamic;
        } else {
            String s = optional.get();
            if (!s.isEmpty() && !s.equals("null")) {
                char c0 = s.charAt(0);
                char c1 = s.charAt(s.length() - 1);
                if (c0 == '"' && c1 == '"' || c0 == '{' && c1 == '}' || c0 == '[' && c1 == ']') {
                    try {
                        JsonElement jsonelement = JsonParser.parseString(s);
                        if (jsonelement.isJsonPrimitive()) {
                            return createPlainTextComponent(dynamic.getOps(), jsonelement.getAsString());
                        }

                        return dynamic.createString(GsonHelper.toStableString(jsonelement));
                    } catch (JsonParseException jsonparseexception) {
                    }
                }

                return createPlainTextComponent(dynamic.getOps(), s);
            } else {
                return createEmptyComponent(dynamic.getOps());
            }
        }
    }

    public static Optional<String> extractTranslationString(String data) {
        try {
            JsonElement jsonelement = JsonParser.parseString(data);
            if (jsonelement.isJsonObject()) {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                JsonElement jsonelement1 = jsonobject.get("translate");
                if (jsonelement1 != null && jsonelement1.isJsonPrimitive()) {
                    return Optional.of(jsonelement1.getAsString());
                }
            }
        } catch (JsonParseException jsonparseexception) {
        }

        return Optional.empty();
    }
}
