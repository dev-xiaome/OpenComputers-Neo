package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Longs;
import java.util.concurrent.atomic.AtomicLong;

public final class RandomSupport {
    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;
    private static final HashFunction MD5_128 = Hashing.md5();
    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    @VisibleForTesting
    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public static RandomSupport.Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long i = seed ^ 7640891576956012809L;
        long j = i + -7046029254386353131L;
        return new RandomSupport.Seed128bit(i, j);
    }

    public static RandomSupport.Seed128bit upgradeSeedTo128bit(long seed) {
        return upgradeSeedTo128bitUnmixed(seed).mixed();
    }

    public static RandomSupport.Seed128bit seedFromHashOf(String string) {
        byte[] abyte = MD5_128.hashString(string, Charsets.UTF_8).asBytes();
        long i = Longs.fromBytes(abyte[0], abyte[1], abyte[2], abyte[3], abyte[4], abyte[5], abyte[6], abyte[7]);
        long j = Longs.fromBytes(abyte[8], abyte[9], abyte[10], abyte[11], abyte[12], abyte[13], abyte[14], abyte[15]);
        return new RandomSupport.Seed128bit(i, j);
    }

    public static long generateUniqueSeed() {
        return SEED_UNIQUIFIER.updateAndGet(p_224601_ -> p_224601_ * 1181783497276652981L) ^ System.nanoTime();
    }

    public static record Seed128bit(long seedLo, long seedHi) {
        public RandomSupport.Seed128bit xor(long seedLo, long seedHi) {
            return new RandomSupport.Seed128bit(this.seedLo ^ seedLo, this.seedHi ^ seedHi);
        }

        public RandomSupport.Seed128bit xor(RandomSupport.Seed128bit seed) {
            return this.xor(seed.seedLo, seed.seedHi);
        }

        public RandomSupport.Seed128bit mixed() {
            return new RandomSupport.Seed128bit(RandomSupport.mixStafford13(this.seedLo), RandomSupport.mixStafford13(this.seedHi));
        }
    }
}
