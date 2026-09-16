import li.cil.oc.util.DFPWM;
import org.junit.Test;

import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;

public class DfpwmCompatibilityTest {
    @Test
    public void decoderMatchesAsieLibImplementation() throws Exception {
        byte[] encoded = new byte[4096];
        for (int i = 0; i < encoded.length; i++) encoded[i] = (byte) ((i * 73 + (i >>> 3) * 19) & 0xFF);
        byte[] decoded = new byte[encoded.length * 8];

        new DFPWM().decompress(decoded, encoded, 0, 0, encoded.length);

        byte[] digest = MessageDigest.getInstance("SHA-256").digest(decoded);
        StringBuilder actual = new StringBuilder();
        for (byte value : digest) actual.append(String.format("%02x", value & 0xFF));
        assertEquals("5de3198bde8b415ec9831a1e9ec11842b5e9f4011870cd27f3c196c1989a32c3", actual.toString());
    }
}
