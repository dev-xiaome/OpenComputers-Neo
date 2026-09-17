package net.minecraft.world.item.armortrim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

public class ArmorTrim implements TooltipProvider {
    public static final Codec<ArmorTrim> CODEC = RecordCodecBuilder.create(
        p_337943_ -> p_337943_.group(
                    TrimMaterial.CODEC.fieldOf("material").forGetter(ArmorTrim::material),
                    TrimPattern.CODEC.fieldOf("pattern").forGetter(ArmorTrim::pattern),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(p_330108_ -> p_330108_.showInTooltip)
                )
                .apply(p_337943_, ArmorTrim::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorTrim> STREAM_CODEC = StreamCodec.composite(
        TrimMaterial.STREAM_CODEC,
        ArmorTrim::material,
        TrimPattern.STREAM_CODEC,
        ArmorTrim::pattern,
        ByteBufCodecs.BOOL,
        p_330107_ -> p_330107_.showInTooltip,
        ArmorTrim::new
    );
    private static final Component UPGRADE_TITLE = Component.translatable(
            Util.makeDescriptionId("item", ResourceLocation.withDefaultNamespace("smithing_template.upgrade"))
        )
        .withStyle(ChatFormatting.GRAY);
    private final Holder<TrimMaterial> material;
    private final Holder<TrimPattern> pattern;
    private final boolean showInTooltip;
    private final Function<Holder<ArmorMaterial>, ResourceLocation> innerTexture;
    private final Function<Holder<ArmorMaterial>, ResourceLocation> outerTexture;

    private ArmorTrim(
        Holder<TrimMaterial> material,
        Holder<TrimPattern> pattern,
        boolean showInTooltip,
        Function<Holder<ArmorMaterial>, ResourceLocation> innerTexture,
        Function<Holder<ArmorMaterial>, ResourceLocation> outerTexture
    ) {
        this.material = material;
        this.pattern = pattern;
        this.showInTooltip = showInTooltip;
        this.innerTexture = innerTexture;
        this.outerTexture = outerTexture;
    }

    public ArmorTrim(Holder<TrimMaterial> material, Holder<TrimPattern> pattern, boolean showInTooltip) {
        this.material = material;
        this.pattern = pattern;
        this.innerTexture = Util.memoize(p_335286_ -> {
            ResourceLocation resourcelocation = pattern.value().assetId();
            String s = getColorPaletteSuffix(material, p_335286_);
            return resourcelocation.withPath(p_266737_ -> "trims/models/armor/" + p_266737_ + "_leggings_" + s);
        });
        this.outerTexture = Util.memoize(p_335283_ -> {
            ResourceLocation resourcelocation = pattern.value().assetId();
            String s = getColorPaletteSuffix(material, p_335283_);
            return resourcelocation.withPath(p_266864_ -> "trims/models/armor/" + p_266864_ + "_" + s);
        });
        this.showInTooltip = showInTooltip;
    }

    public ArmorTrim(Holder<TrimMaterial> material, Holder<TrimPattern> pattern) {
        this(material, pattern, true);
    }

    private static String getColorPaletteSuffix(Holder<TrimMaterial> trimMaterial, Holder<ArmorMaterial> armorMaterial) {
        Map<Holder<ArmorMaterial>, String> map = trimMaterial.value().overrideArmorMaterials();
        String s = map.get(armorMaterial);
        return s != null ? s : trimMaterial.value().assetName();
    }

    public boolean hasPatternAndMaterial(Holder<TrimPattern> pattern, Holder<TrimMaterial> material) {
        return pattern.equals(this.pattern) && material.equals(this.material);
    }

    public Holder<TrimPattern> pattern() {
        return this.pattern;
    }

    public Holder<TrimMaterial> material() {
        return this.material;
    }

    public ResourceLocation innerTexture(Holder<ArmorMaterial> armorMaterial) {
        return this.innerTexture.apply(armorMaterial);
    }

    public ResourceLocation outerTexture(Holder<ArmorMaterial> armorMaterial) {
        return this.outerTexture.apply(armorMaterial);
    }

    @Override
    public boolean equals(Object other) {
        return !(other instanceof ArmorTrim armortrim)
            ? false
            : this.showInTooltip == armortrim.showInTooltip && this.pattern.equals(armortrim.pattern) && this.material.equals(armortrim.material);
    }

    @Override
    public int hashCode() {
        int i = this.material.hashCode();
        i = 31 * i + this.pattern.hashCode();
        return 31 * i + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag) {
        if (this.showInTooltip) {
            tooltipAdder.accept(UPGRADE_TITLE);
            tooltipAdder.accept(CommonComponents.space().append(this.pattern.value().copyWithStyle(this.material)));
            tooltipAdder.accept(CommonComponents.space().append(this.material.value().description()));
        }
    }

    public ArmorTrim withTooltip(boolean showInTooltip) {
        return new ArmorTrim(this.material, this.pattern, showInTooltip, this.innerTexture, this.outerTexture);
    }
}
