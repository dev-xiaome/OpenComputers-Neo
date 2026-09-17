package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;

public class Utf8String {
    public static String read(ByteBuf buffer, int maxLength) {
        int i = ByteBufUtil.utf8MaxBytes(maxLength);
        int j = VarInt.read(buffer);
        if (j > i) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + j + " > " + i + ")");
        } else if (j < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            int k = buffer.readableBytes();
            if (j > k) {
                throw new DecoderException("Not enough bytes in buffer, expected " + j + ", but got " + k);
            } else {
                String s = buffer.toString(buffer.readerIndex(), j, StandardCharsets.UTF_8);
                buffer.readerIndex(buffer.readerIndex() + j);
                if (s.length() > maxLength) {
                    throw new DecoderException("The received string length is longer than maximum allowed (" + s.length() + " > " + maxLength + ")");
                } else {
                    return s;
                }
            }
        }
    }

    public static void write(ByteBuf buffer, CharSequence string, int maxLength) {
        if (string.length() > maxLength) {
            throw new EncoderException("String too big (was " + string.length() + " characters, max " + maxLength + ")");
        } else {
            int i = ByteBufUtil.utf8MaxBytes(string);
            ByteBuf bytebuf = buffer.alloc().buffer(i);

            try {
                int j = ByteBufUtil.writeUtf8(bytebuf, string);
                int k = ByteBufUtil.utf8MaxBytes(maxLength);
                if (j > k) {
                    throw new EncoderException("String too big (was " + j + " bytes encoded, max " + k + ")");
                }

                VarInt.write(buffer, j);
                buffer.writeBytes(bytebuf);
            } finally {
                bytebuf.release();
            }
        }
    }
}
