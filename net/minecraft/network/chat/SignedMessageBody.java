package net.minecraft.network.chat;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.time.Instant;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.SignatureUpdater;

public record SignedMessageBody(String content, Instant timeStamp, long salt, LastSeenMessages lastSeen) {
    public static final MapCodec<SignedMessageBody> MAP_CODEC = RecordCodecBuilder.mapCodec(
        p_253722_ -> p_253722_.group(
                    Codec.STRING.fieldOf("content").forGetter(SignedMessageBody::content),
                    ExtraCodecs.INSTANT_ISO8601.fieldOf("time_stamp").forGetter(SignedMessageBody::timeStamp),
                    Codec.LONG.fieldOf("salt").forGetter(SignedMessageBody::salt),
                    LastSeenMessages.CODEC.optionalFieldOf("last_seen", LastSeenMessages.EMPTY).forGetter(SignedMessageBody::lastSeen)
                )
                .apply(p_253722_, SignedMessageBody::new)
    );

    public static SignedMessageBody unsigned(String content) {
        return new SignedMessageBody(content, Instant.now(), 0L, LastSeenMessages.EMPTY);
    }

    public void updateSignature(SignatureUpdater.Output output) throws SignatureException {
        output.update(Longs.toByteArray(this.salt));
        output.update(Longs.toByteArray(this.timeStamp.getEpochSecond()));
        byte[] abyte = this.content.getBytes(StandardCharsets.UTF_8);
        output.update(Ints.toByteArray(abyte.length));
        output.update(abyte);
        this.lastSeen.updateSignature(output);
    }

    public SignedMessageBody.Packed pack(MessageSignatureCache signatureCache) {
        return new SignedMessageBody.Packed(this.content, this.timeStamp, this.salt, this.lastSeen.pack(signatureCache));
    }

    public static record Packed(String content, Instant timeStamp, long salt, LastSeenMessages.Packed lastSeen) {
        public Packed(FriendlyByteBuf p_251620_) {
            this(p_251620_.readUtf(256), p_251620_.readInstant(), p_251620_.readLong(), new LastSeenMessages.Packed(p_251620_));
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(this.content, 256);
            buffer.writeInstant(this.timeStamp);
            buffer.writeLong(this.salt);
            this.lastSeen.write(buffer);
        }

        public Optional<SignedMessageBody> unpack(MessageSignatureCache signatureCache) {
            return this.lastSeen.unpack(signatureCache).map(p_249065_ -> new SignedMessageBody(this.content, this.timeStamp, this.salt, p_249065_));
        }
    }
}
