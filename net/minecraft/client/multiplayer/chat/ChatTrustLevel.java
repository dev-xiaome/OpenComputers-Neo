package net.minecraft.client.multiplayer.chat;

import com.mojang.serialization.Codec;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringRepresentable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum ChatTrustLevel implements StringRepresentable {
    SECURE("secure"),
    MODIFIED("modified"),
    NOT_SECURE("not_secure");

    public static final Codec<ChatTrustLevel> CODEC = StringRepresentable.fromEnum(ChatTrustLevel::values);
    private final String serializedName;

    private ChatTrustLevel(String serializedName) {
        this.serializedName = serializedName;
    }

    public static ChatTrustLevel evaluate(PlayerChatMessage chatMessage, Component decoratedServerContent, Instant timestamp) {
        if (!chatMessage.hasSignature() || chatMessage.hasExpiredClient(timestamp)) {
            return NOT_SECURE;
        } else {
            return isModified(chatMessage, decoratedServerContent) ? MODIFIED : SECURE;
        }
    }

    private static boolean isModified(PlayerChatMessage chatMessage, Component decoratedServerContent) {
        if (!decoratedServerContent.getString().contains(chatMessage.signedContent())) {
            return true;
        } else {
            Component component = chatMessage.unsignedContent();
            return component == null ? false : containsModifiedStyle(component);
        }
    }

    private static boolean containsModifiedStyle(Component chatMessage) {
        return chatMessage.<Boolean>visit((p_251711_, p_250844_) -> isModifiedStyle(p_251711_) ? Optional.of(true) : Optional.empty(), Style.EMPTY).orElse(false);
    }

    private static boolean isModifiedStyle(Style style) {
        return !style.getFont().equals(Style.DEFAULT_FONT);
    }

    public boolean isNotSecure() {
        return this == NOT_SECURE;
    }

    @Nullable
    public GuiMessageTag createTag(PlayerChatMessage chatMessage) {
        return switch (this) {
            case MODIFIED -> GuiMessageTag.chatModified(chatMessage.signedContent());
            case NOT_SECURE -> GuiMessageTag.chatNotSecure();
            default -> null;
        };
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
