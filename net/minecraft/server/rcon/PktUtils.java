package net.minecraft.server.rcon;

import java.nio.charset.StandardCharsets;

public class PktUtils {
    public static final int MAX_PACKET_SIZE = 1460;
    public static final char[] HEX_CHAR = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * Read a null-terminated string from the given byte array
     */
    public static String stringFromByteArray(byte[] input, int offset, int length) {
        int i = length - 1;
        int j = offset > i ? i : offset;

        while (0 != input[j] && j < i) {
            j++;
        }

        return new String(input, offset, j - offset, StandardCharsets.UTF_8);
    }

    /**
     * Read 4 bytes from the
     */
    public static int intFromByteArray(byte[] input, int offset) {
        return intFromByteArray(input, offset, input.length);
    }

    /**
     * Read 4 bytes from the given array in little-endian format and return them as an int
     */
    public static int intFromByteArray(byte[] input, int offset, int length) {
        return 0 > length - offset - 4
            ? 0
            : input[offset + 3] << 24 | (input[offset + 2] & 0xFF) << 16 | (input[offset + 1] & 0xFF) << 8 | input[offset] & 0xFF;
    }

    /**
     * Read 4 bytes from the given array in big-endian format and return them as an int
     */
    public static int intFromNetworkByteArray(byte[] input, int offset, int length) {
        return 0 > length - offset - 4
            ? 0
            : input[offset] << 24 | (input[offset + 1] & 0xFF) << 16 | (input[offset + 2] & 0xFF) << 8 | input[offset + 3] & 0xFF;
    }

    /**
     * Returns a String representation of the byte in hexadecimal format
     */
    public static String toHexString(byte input) {
        return "" + HEX_CHAR[(input & 240) >>> 4] + HEX_CHAR[input & 15];
    }
}
