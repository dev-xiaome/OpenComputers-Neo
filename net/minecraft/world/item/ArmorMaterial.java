package net.minecraft.world.item;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.crafting.Ingredient;

public record ArmorMaterial(
    Map<ArmorItem.Type, Integer> defense,
    int enchantmentValue,
    Holder<SoundEvent> equipSound,
    Supplier<Ingredient> repairIngredient,
    List<ArmorMaterial.Layer> layers,
    float toughness,
    float knockbackResistance
) {
    public static final Codec<Holder<ArmorMaterial>> CODEC = BuiltInRegistries.ARMOR_MATERIAL.holderByNameCodec();

    public int getDefense(ArmorItem.Type type) {
        return this.defense.getOrDefault(type, 0);
    }

    public static final class Layer {
        private final ResourceLocation assetName;
        private final String suffix;
        private final boolean dyeable;
        private final ResourceLocation innerTexture;
        private final ResourceLocation outerTexture;

        public Layer(ResourceLocation assetName, String suffix, boolean dyeable) {
            this.assetName = assetName;
            this.suffix = suffix;
            this.dyeable = dyeable;
            this.innerTexture = this.resolveTexture(true);
            this.outerTexture = this.resolveTexture(false);
        }

        public Layer(ResourceLocation assetName) {
            this(assetName, "", false);
        }

        private ResourceLocation resolveTexture(boolean innerTexture) {
            return this.assetName
                .withPath(p_324187_ -> "textures/models/armor/" + this.assetName.getPath() + "_layer_" + (innerTexture ? 2 : 1) + this.suffix + ".png");
        }

        public ResourceLocation texture(boolean innerTexture) {
            return innerTexture ? this.innerTexture : this.outerTexture;
        }

        public boolean dyeable() {
            return this.dyeable;
        }
    }
}
