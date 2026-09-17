package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import javax.crypto.Cipher;

/**
 * Channel handler that handles protocol decryption.
 *
 * @see Connection#setEncryptionKey
 */
public class CipherDecoder extends MessageToMessageDecoder<ByteBuf> {
    private final CipherBase cipher;

    public CipherDecoder(Cipher cipher) {
        this.cipher = new CipherBase(cipher);
    }

    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        out.add(this.cipher.decipher(context, in));
    }
}
