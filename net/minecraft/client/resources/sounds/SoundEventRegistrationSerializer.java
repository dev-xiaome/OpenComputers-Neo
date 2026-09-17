package net.minecraft.client.resources.sounds;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.Validate;

@OnlyIn(Dist.CLIENT)
public class SoundEventRegistrationSerializer implements JsonDeserializer<SoundEventRegistration> {
    private static final FloatProvider DEFAULT_FLOAT = ConstantFloat.of(1.0F);

    public SoundEventRegistration deserialize(JsonElement p_119827_, Type p_119828_, JsonDeserializationContext p_119829_) throws JsonParseException {
        JsonObject jsonobject = GsonHelper.convertToJsonObject(p_119827_, "entry");
        boolean flag = GsonHelper.getAsBoolean(jsonobject, "replace", false);
        String s = GsonHelper.getAsString(jsonobject, "subtitle", null);
        List<Sound> list = this.getSounds(jsonobject);
        return new SoundEventRegistration(list, flag, s);
    }

    private List<Sound> getSounds(JsonObject object) {
        List<Sound> list = Lists.newArrayList();
        if (object.has("sounds")) {
            JsonArray jsonarray = GsonHelper.getAsJsonArray(object, "sounds");

            for (int i = 0; i < jsonarray.size(); i++) {
                JsonElement jsonelement = jsonarray.get(i);
                if (GsonHelper.isStringValue(jsonelement)) {
                    ResourceLocation resourcelocation = ResourceLocation.parse(GsonHelper.convertToString(jsonelement, "sound"));
                    list.add(new Sound(resourcelocation, DEFAULT_FLOAT, DEFAULT_FLOAT, 1, Sound.Type.FILE, false, false, 16));
                } else {
                    list.add(this.getSound(GsonHelper.convertToJsonObject(jsonelement, "sound")));
                }
            }
        }

        return list;
    }

    private Sound getSound(JsonObject object) {
        ResourceLocation resourcelocation = ResourceLocation.parse(GsonHelper.getAsString(object, "name"));
        Sound.Type sound$type = this.getType(object, Sound.Type.FILE);
        float f = GsonHelper.getAsFloat(object, "volume", 1.0F);
        Validate.isTrue(f > 0.0F, "Invalid volume");
        float f1 = GsonHelper.getAsFloat(object, "pitch", 1.0F);
        Validate.isTrue(f1 > 0.0F, "Invalid pitch");
        int i = GsonHelper.getAsInt(object, "weight", 1);
        Validate.isTrue(i > 0, "Invalid weight");
        boolean flag = GsonHelper.getAsBoolean(object, "preload", false);
        boolean flag1 = GsonHelper.getAsBoolean(object, "stream", false);
        int j = GsonHelper.getAsInt(object, "attenuation_distance", 16);
        return new Sound(resourcelocation, ConstantFloat.of(f), ConstantFloat.of(f1), i, sound$type, flag1, flag, j);
    }

    private Sound.Type getType(JsonObject object, Sound.Type defaultValue) {
        Sound.Type sound$type = defaultValue;
        if (object.has("type")) {
            sound$type = Sound.Type.getByName(GsonHelper.getAsString(object, "type"));
            Validate.notNull(sound$type, "Invalid type");
        }

        return sound$type;
    }
}
