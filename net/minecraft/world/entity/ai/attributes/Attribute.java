package net.minecraft.world.entity.ai.attributes;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Defines an entity attribute. These are properties of entities that can be dynamically modified.
 * @see net.minecraft.core.Registry#ATTRIBUTE
 */
public class Attribute implements net.neoforged.neoforge.common.extensions.IAttributeExtension {
    public static final Codec<Holder<Attribute>> CODEC = BuiltInRegistries.ATTRIBUTE.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Attribute>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ATTRIBUTE);
    /**
     * The default value of the attribute.
     */
    private final double defaultValue;
    /**
     * Whether the value of this attribute should be kept in sync on the client.
     */
    private boolean syncable;
    /**
     * A description Id for the attribute. This is most commonly used as the localization key.
     */
    private final String descriptionId;
    private Attribute.Sentiment sentiment = Attribute.Sentiment.POSITIVE;

    protected Attribute(String descriptionId, double defaultValue) {
        this.defaultValue = defaultValue;
        this.descriptionId = descriptionId;
    }

    public double getDefaultValue() {
        return this.defaultValue;
    }

    public boolean isClientSyncable() {
        return this.syncable;
    }

    /**
     * Sets whether the attribute value should be synced to the client.
     * @return The same attribute instance being modified.
     *
     * @param watch Whether the attribute value should be kept in sync.
     */
    public Attribute setSyncable(boolean watch) {
        this.syncable = watch;
        return this;
    }

    public Attribute setSentiment(Attribute.Sentiment sentiment) {
        this.sentiment = sentiment;
        return this;
    }

    /**
     * Sanitizes the value of the attribute to fit within the expected parameter range of the attribute.
     * @return The sanitized attribute value.
     *
     * @param value The value of the attribute to sanitize.
     */
    public double sanitizeValue(double value) {
        return value;
    }

    public String getDescriptionId() {
        return this.descriptionId;
    }

    public ChatFormatting getStyle(boolean isPositive) {
        return this.sentiment.getStyle(isPositive);
    }

    // Neo: Patch in the default implementation of IAttributeExtension#getMergedStyle since we need access to Attribute#sentiment

    protected static final net.minecraft.network.chat.TextColor MERGED_RED = net.minecraft.network.chat.TextColor.fromRgb(0xF93131);
    protected static final net.minecraft.network.chat.TextColor MERGED_BLUE = net.minecraft.network.chat.TextColor.fromRgb(0x7A7AF9);
    protected static final net.minecraft.network.chat.TextColor MERGED_GRAY = net.minecraft.network.chat.TextColor.fromRgb(0xCCCCCC);

    @Override
    public net.minecraft.network.chat.TextColor getMergedStyle(boolean isPositive) {
        return switch (this.sentiment) {
            case POSITIVE -> isPositive ? MERGED_BLUE : MERGED_RED;
            case NEGATIVE -> isPositive ? MERGED_RED : MERGED_BLUE;
            case NEUTRAL -> MERGED_GRAY;
        };
    }

    public static enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE;

        public ChatFormatting getStyle(boolean isPositive) {
            return switch (this) {
                case POSITIVE -> isPositive ? ChatFormatting.BLUE : ChatFormatting.RED;
                case NEUTRAL -> ChatFormatting.GRAY;
                case NEGATIVE -> isPositive ? ChatFormatting.RED : ChatFormatting.BLUE;
            };
        }
    }
}
