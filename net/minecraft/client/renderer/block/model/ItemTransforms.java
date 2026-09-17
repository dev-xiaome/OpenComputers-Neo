package net.minecraft.client.renderer.block.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemTransforms {
    public static final ItemTransforms NO_TRANSFORMS = new ItemTransforms();
    public final ItemTransform thirdPersonLeftHand;
    public final ItemTransform thirdPersonRightHand;
    public final ItemTransform firstPersonLeftHand;
    public final ItemTransform firstPersonRightHand;
    public final ItemTransform head;
    public final ItemTransform gui;
    public final ItemTransform ground;
    public final ItemTransform fixed;
    public final com.google.common.collect.ImmutableMap<ItemDisplayContext, ItemTransform> moddedTransforms;

    private ItemTransforms() {
        this(
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM,
            ItemTransform.NO_TRANSFORM
        );
    }

    public ItemTransforms(ItemTransforms transforms) {
        this.thirdPersonLeftHand = transforms.thirdPersonLeftHand;
        this.thirdPersonRightHand = transforms.thirdPersonRightHand;
        this.firstPersonLeftHand = transforms.firstPersonLeftHand;
        this.firstPersonRightHand = transforms.firstPersonRightHand;
        this.head = transforms.head;
        this.gui = transforms.gui;
        this.ground = transforms.ground;
        this.fixed = transforms.fixed;
        this.moddedTransforms = transforms.moddedTransforms;
    }

    @Deprecated
    public ItemTransforms(
        ItemTransform thirdPersonLeftHand,
        ItemTransform thirdPersonRightHand,
        ItemTransform firstPersonLeftHand,
        ItemTransform firstPersonRightHand,
        ItemTransform head,
        ItemTransform gui,
        ItemTransform ground,
        ItemTransform fixed
    ) {
        this(thirdPersonLeftHand, thirdPersonRightHand, firstPersonLeftHand, firstPersonRightHand, head, gui, ground, fixed, com.google.common.collect.ImmutableMap.of());
    }

    public ItemTransforms(
        ItemTransform thirdPersonLeftHand,
        ItemTransform thirdPersonRightHand,
        ItemTransform firstPersonLeftHand,
        ItemTransform firstPersonRightHand,
        ItemTransform head,
        ItemTransform gui,
        ItemTransform ground,
        ItemTransform fixed,
        com.google.common.collect.ImmutableMap<ItemDisplayContext, ItemTransform> moddedTransforms
    ) {
        this.thirdPersonLeftHand = thirdPersonLeftHand;
        this.thirdPersonRightHand = thirdPersonRightHand;
        this.firstPersonLeftHand = firstPersonLeftHand;
        this.firstPersonRightHand = firstPersonRightHand;
        this.head = head;
        this.gui = gui;
        this.ground = ground;
        this.fixed = fixed;
        this.moddedTransforms = moddedTransforms;
    }

    public ItemTransform getTransform(ItemDisplayContext displayContext) {
        if (displayContext.isModded()) {
            ItemTransform moddedTransform = moddedTransforms.get(displayContext);
            if (moddedTransform != null) {
                return moddedTransform;
            }
            ItemDisplayContext moddedFallback = displayContext.fallback();
            if (moddedFallback == null) {
                return ItemTransform.NO_TRANSFORM;
            }
            displayContext = moddedFallback;
        }
        return switch (displayContext) {
            case THIRD_PERSON_LEFT_HAND -> this.thirdPersonLeftHand;
            case THIRD_PERSON_RIGHT_HAND -> this.thirdPersonRightHand;
            case FIRST_PERSON_LEFT_HAND -> this.firstPersonLeftHand;
            case FIRST_PERSON_RIGHT_HAND -> this.firstPersonRightHand;
            case HEAD -> this.head;
            case GUI -> this.gui;
            case GROUND -> this.ground;
            case FIXED -> this.fixed;
            default -> ItemTransform.NO_TRANSFORM;
        };
    }

    public boolean hasTransform(ItemDisplayContext displayContext) {
        return this.getTransform(displayContext) != ItemTransform.NO_TRANSFORM;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Deserializer implements JsonDeserializer<ItemTransforms> {
        public ItemTransforms deserialize(JsonElement json, Type p_type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonobject = json.getAsJsonObject();
            ItemTransform itemtransform = this.getTransform(context, jsonobject, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            ItemTransform itemtransform1 = this.getTransform(context, jsonobject, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            if (itemtransform1 == ItemTransform.NO_TRANSFORM) {
                itemtransform1 = itemtransform;
            }

            ItemTransform itemtransform2 = this.getTransform(context, jsonobject, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            ItemTransform itemtransform3 = this.getTransform(context, jsonobject, ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            if (itemtransform3 == ItemTransform.NO_TRANSFORM) {
                itemtransform3 = itemtransform2;
            }

            ItemTransform itemtransform4 = this.getTransform(context, jsonobject, ItemDisplayContext.HEAD);
            ItemTransform itemtransform5 = this.getTransform(context, jsonobject, ItemDisplayContext.GUI);
            ItemTransform itemtransform6 = this.getTransform(context, jsonobject, ItemDisplayContext.GROUND);
            ItemTransform itemtransform7 = this.getTransform(context, jsonobject, ItemDisplayContext.FIXED);

            var builder = com.google.common.collect.ImmutableMap.<ItemDisplayContext, ItemTransform>builder();
            for (ItemDisplayContext type : ItemDisplayContext.values()) {
                if (type.isModded()) {
                    var transform = this.getTransform(context, jsonobject, type);
                    var fallbackType = type;
                    while (transform == ItemTransform.NO_TRANSFORM && fallbackType.fallback() != null) {
                        fallbackType = fallbackType.fallback();
                        transform = this.getTransform(context, jsonobject, fallbackType);
                    }
                    if (transform != ItemTransform.NO_TRANSFORM){
                        builder.put(type, transform);
                    }
                }
            }

            return new ItemTransforms(itemtransform1, itemtransform, itemtransform3, itemtransform2, itemtransform4, itemtransform5, itemtransform6, itemtransform7, builder.build());
        }

        private ItemTransform getTransform(JsonDeserializationContext deserializationContext, JsonObject json, ItemDisplayContext displayContext) {
            String s = displayContext.getSerializedName();
            return json.has(s) ? deserializationContext.deserialize(json.get(s), ItemTransform.class) : ItemTransform.NO_TRANSFORM;
        }
    }
}
