package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Type;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class ItemTransform {
    public static final ItemTransform NO_TRANSFORM = new ItemTransform(new Vector3f(), new Vector3f(), new Vector3f(1.0F, 1.0F, 1.0F));
    public final Vector3f rotation;
    public final Vector3f translation;
    public final Vector3f scale;
    public final Vector3f rightRotation;

    public ItemTransform(Vector3f rotation, Vector3f translation, Vector3f scale) {
        this(rotation, translation, scale, new Vector3f());
    }

    public ItemTransform(Vector3f rotation, Vector3f translation, Vector3f scale, Vector3f rightRotation) {
        this.rotation = new Vector3f(rotation);
        this.translation = new Vector3f(translation);
        this.scale = new Vector3f(scale);
        this.rightRotation = new Vector3f(rightRotation);
    }

    public void apply(boolean leftHand, PoseStack poseStack) {
        if (this != NO_TRANSFORM) {
            float f = this.rotation.x();
            float f1 = this.rotation.y();
            float f2 = this.rotation.z();
            if (leftHand) {
                f1 = -f1;
                f2 = -f2;
            }

            int i = leftHand ? -1 : 1;
            poseStack.translate((float)i * this.translation.x(), this.translation.y(), this.translation.z());
            poseStack.mulPose(new Quaternionf().rotationXYZ(f * (float) (Math.PI / 180.0), f1 * (float) (Math.PI / 180.0), f2 * (float) (Math.PI / 180.0)));
            poseStack.scale(this.scale.x(), this.scale.y(), this.scale.z());
            poseStack.mulPose(net.neoforged.neoforge.common.util.TransformationHelper.quatFromXYZ(rightRotation.x(), rightRotation.y() * (leftHand ? -1 : 1), rightRotation.z() * (leftHand ? -1 : 1), true));
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (this.getClass() != other.getClass()) {
            return false;
        } else {
            ItemTransform itemtransform = (ItemTransform)other;
            return this.rotation.equals(itemtransform.rotation) && this.scale.equals(itemtransform.scale) && this.translation.equals(itemtransform.translation);
        }
    }

    @Override
    public int hashCode() {
        int i = this.rotation.hashCode();
        i = 31 * i + this.translation.hashCode();
        return 31 * i + this.scale.hashCode();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Deserializer implements JsonDeserializer<ItemTransform> {
        public static final Vector3f DEFAULT_ROTATION = new Vector3f(0.0F, 0.0F, 0.0F);
        public static final Vector3f DEFAULT_TRANSLATION = new Vector3f(0.0F, 0.0F, 0.0F);
        public static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);
        public static final float MAX_TRANSLATION = 5.0F;
        public static final float MAX_SCALE = 4.0F;

        public ItemTransform deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonobject = json.getAsJsonObject();
            Vector3f vector3f = this.getVector3f(jsonobject, "rotation", DEFAULT_ROTATION);
            Vector3f vector3f1 = this.getVector3f(jsonobject, "translation", DEFAULT_TRANSLATION);
            vector3f1.mul(0.0625F);
            vector3f1.set(Mth.clamp(vector3f1.x, -5.0F, 5.0F), Mth.clamp(vector3f1.y, -5.0F, 5.0F), Mth.clamp(vector3f1.z, -5.0F, 5.0F));
            Vector3f vector3f2 = this.getVector3f(jsonobject, "scale", DEFAULT_SCALE);
            vector3f2.set(Mth.clamp(vector3f2.x, -4.0F, 4.0F), Mth.clamp(vector3f2.y, -4.0F, 4.0F), Mth.clamp(vector3f2.z, -4.0F, 4.0F));
            Vector3f rightRotation = this.getVector3f(jsonobject, "right_rotation", DEFAULT_ROTATION);
            return new ItemTransform(vector3f, vector3f1, vector3f2, rightRotation);
        }

        private Vector3f getVector3f(JsonObject json, String key, Vector3f fallback) {
            if (!json.has(key)) {
                return fallback;
            } else {
                JsonArray jsonarray = GsonHelper.getAsJsonArray(json, key);
                if (jsonarray.size() != 3) {
                    throw new JsonParseException("Expected 3 " + key + " values, found: " + jsonarray.size());
                } else {
                    float[] afloat = new float[3];

                    for (int i = 0; i < afloat.length; i++) {
                        afloat[i] = GsonHelper.convertToFloat(jsonarray.get(i), key + "[" + i + "]");
                    }

                    return new Vector3f(afloat[0], afloat[1], afloat[2]);
                }
            }
        }
    }
}
