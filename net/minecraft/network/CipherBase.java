package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;

public class CipherBase {
    private final Cipher cipher;
    private byte[] heapIn = new byte[0];
    private byte[] heapOut = new byte[0];

    protected CipherBase(Cipher cipher) {
        this.cipher = cipher;
    }

    private byte[] bufToByte(ByteBuf buffer) {
        int i = buffer.readableBytes();
        if (this.heapIn.length < i) {
            this.heapIn = new byte[i];
        }

        buffer.readBytes(this.heapIn, 0, i);
        return this.heapIn;
    }

    protected ByteBuf decipher(ChannelHandlerContext ctx, ByteBuf buffer) throws ShortBufferException {
        int i = buffer.readableBytes();
        byte[] abyte = this.bufToByte(buffer);
        ByteBuf bytebuf = ctx.alloc().heapBuffer(this.cipher.getOutputSize(i));
        bytebuf.writerIndex(this.cipher.update(abyte, 0, i, bytebuf.array(), bytebuf.arrayOffset()));
        return bytebuf;
    }

    protected void encipher(ByteBuf input, ByteBuf out) throws ShortBufferException {
        int i = input.readableBytes();
        byte[] abyte = this.bufToByte(input);
        int j = this.cipher.getOutputSize(i);
        if (this.heapOut.length < j) {
            this.heapOut = new byte[j];
        }

        out.writeBytes(this.heapOut, 0, this.cipher.update(abyte, 0, i, this.heapOut));
    }
}
