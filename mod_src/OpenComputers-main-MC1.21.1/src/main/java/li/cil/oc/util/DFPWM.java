package li.cil.oc.util;

/**
 * Byte-for-byte port of the DFPWM decoder shaded into the working
 * Computronics 1.12.2-1.6.6 runtime JAR.
 *
 * The decoder emits signed 8-bit PCM, exactly as AsieLib did. Callers that
 * feed OpenAL's MONO8 format must flip the sign bit before queueing samples.
 */
public final class DFPWM {
    private static final int RESP_PREC = 10;
    private static final int LPF_STRENGTH = 140;

    private int response;
    private int level;
    private boolean lastbit;
    private int flastlevel;
    private int lpflevel;

    public void decompress(byte[] dest, byte[] src, int destoffs, int srcoffs, int len) {
        for (int i = 0; i < len; i++) {
            if (srcoffs >= src.length) return;
            int d = src[srcoffs++] & 0xFF;
            for (int j = 0; j < 8; j++) {
                boolean curbit = (d & 1) != 0;
                boolean previousBit = lastbit;
                int target = curbit ? 127 : -128;
                int nextLevel = level + ((response * (target - level) + 512) >> RESP_PREC);
                if (nextLevel == level && level != target)
                    nextLevel += target == 127 ? 1 : -1;
                int responseTarget = curbit == lastbit ? 1023 : 0;
                int nextResponse = response;
                if (response != responseTarget)
                    nextResponse += curbit == lastbit ? 1 : -1;
                if (nextResponse < 8)
                    nextResponse = 8;
                level = nextLevel;
                response = nextResponse;
                lastbit = curbit;
                int blevel = (byte) (curbit == previousBit ? level : ((flastlevel + level + 1) >> 1));
                flastlevel = level;
                lpflevel += ((LPF_STRENGTH * (blevel - lpflevel) + 128) >> 8);
                dest[destoffs++] = (byte) lpflevel;
                d >>= 1;
            }
        }
    }
}
