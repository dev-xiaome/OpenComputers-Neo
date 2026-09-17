package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.SignatureUpdater;
import net.minecraft.util.SignatureValidator;

public record PlayerChatMessage(
    SignedMessageLink link, @Nullable MessageSignature signature, SignedMessageBody signedBody, @Nullable Component unsignedContent, FilterMask filterMask
) {
    public static final MapCodec<PlayerChatMessage> MAP_CODEC = RecordCodecBuilder.mapCodec(
        p_304165_ -> p_304165_.group(
                    SignedMessageLink.CODEC.fieldOf("link").forGetter(PlayerChatMessage::link),
                    MessageSignature.CODEC.optionalFieldOf("signature").forGetter(p_253459_ -> Optional.ofNullable(p_253459_.signature)),
                    SignedMessageBody.MAP_CODEC.forGetter(PlayerChatMessage::signedBody),
                    ComponentSerialization.CODEC.optionalFieldOf("unsigned_content").forGetter(p_253458_ -> Optional.ofNullable(p_253458_.unsignedContent)),
                    FilterMask.CODEC.optionalFieldOf("filter_mask", FilterMask.PASS_THROUGH).forGetter(PlayerChatMessage::filterMask)
                )
                .apply(
                    p_304165_,
                    (p_253461_, p_253462_, p_253463_, p_253464_, p_253465_) -> new PlayerChatMessage(
                            p_253461_, p_253462_.orElse(null), p_253463_, p_253464_.orElse(null), p_253465_
                        )
                )
    );
    private static final UUID SYSTEM_SENDER = Util.NIL_UUID;
    public static final Duration MESSAGE_EXPIRES_AFTER_SERVER = Duration.ofMinutes(5L);
    public static final Duration MESSAGE_EXPIRES_AFTER_CLIENT = MESSAGE_EXPIRES_AFTER_SERVER.plus(Duration.ofMinutes(2L));

    public static PlayerChatMessage system(String content) {
        return unsigned(SYSTEM_SENDER, content);
    }

    public static PlayerChatMessage unsigned(UUID sender, String content) {
        SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(content);
        SignedMessageLink signedmessagelink = SignedMessageLink.unsigned(sender);
        return new PlayerChatMessage(signedmessagelink, null, signedmessagebody, null, FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage withUnsignedContent(Component message) {
        Component component = !message.equals(Component.literal(this.signedContent())) ? message : null;
        return new PlayerChatMessage(this.link, this.signature, this.signedBody, component, this.filterMask);
    }

    public PlayerChatMessage removeUnsignedContent() {
        return this.unsignedContent != null ? new PlayerChatMessage(this.link, this.signature, this.signedBody, null, this.filterMask) : this;
    }

    public PlayerChatMessage filter(FilterMask mask) {
        return this.filterMask.equals(mask) ? this : new PlayerChatMessage(this.link, this.signature, this.signedBody, this.unsignedContent, mask);
    }

    public PlayerChatMessage filter(boolean shouldFilter) {
        return this.filter(shouldFilter ? this.filterMask : FilterMask.PASS_THROUGH);
    }

    public PlayerChatMessage removeSignature() {
        SignedMessageBody signedmessagebody = SignedMessageBody.unsigned(this.signedContent());
        SignedMessageLink signedmessagelink = SignedMessageLink.unsigned(this.sender());
        return new PlayerChatMessage(signedmessagelink, null, signedmessagebody, this.unsignedContent, this.filterMask);
    }

    public static void updateSignature(SignatureUpdater.Output output, SignedMessageLink link, SignedMessageBody body) throws SignatureException {
        output.update(Ints.toByteArray(1));
        link.updateSignature(output);
        body.updateSignature(output);
    }

    public boolean verify(SignatureValidator validator) {
        return this.signature != null && this.signature.verify(validator, p_249861_ -> updateSignature(p_249861_, this.link, this.signedBody));
    }

    public String signedContent() {
        return this.signedBody.content();
    }

    public Component decoratedContent() {
        return Objects.requireNonNullElseGet(this.unsignedContent, () -> Component.literal(this.signedContent()));
    }

    public Instant timeStamp() {
        return this.signedBody.timeStamp();
    }

    public long salt() {
        return this.signedBody.salt();
    }

    public boolean hasExpiredServer(Instant timestamp) {
        return timestamp.isAfter(this.timeStamp().plus(MESSAGE_EXPIRES_AFTER_SERVER));
    }

    public boolean hasExpiredClient(Instant timestamp) {
        return timestamp.isAfter(this.timeStamp().plus(MESSAGE_EXPIRES_AFTER_CLIENT));
    }

    public UUID sender() {
        return this.link.sender();
    }

    public boolean isSystem() {
        return this.sender().equals(SYSTEM_SENDER);
    }

    public boolean hasSignature() {
        return this.signature != null;
    }

    public boolean hasSignatureFrom(UUID uuid) {
        return this.hasSignature() && this.link.sender().equals(uuid);
    }

    public boolean isFullyFiltered() {
        return this.filterMask.isFullyFiltered();
    }
}
