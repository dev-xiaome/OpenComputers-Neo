package net.minecraft.world.level.biome;

import com.google.common.hash.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.util.Mth;

public class BiomeManager {
    public static final int CHUNK_CENTER_QUART = QuartPos.fromBlock(8);
    private static final int ZOOM_BITS = 2;
    private static final int ZOOM = 4;
    private static final int ZOOM_MASK = 3;
    private final BiomeManager.NoiseBiomeSource noiseBiomeSource;
    private final long biomeZoomSeed;

    public BiomeManager(BiomeManager.NoiseBiomeSource noiseBiomeSource, long biomeZoomSeed) {
        this.noiseBiomeSource = noiseBiomeSource;
        this.biomeZoomSeed = biomeZoomSeed;
    }

    public static long obfuscateSeed(long seed) {
        return Hashing.sha256().hashLong(seed).asLong();
    }

    public BiomeManager withDifferentSource(BiomeManager.NoiseBiomeSource newSource) {
        return new BiomeManager(newSource, this.biomeZoomSeed);
    }

    public Holder<Biome> getBiome(BlockPos pos) {
        int i = pos.getX() - 2;
        int j = pos.getY() - 2;
        int k = pos.getZ() - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        double d0 = (double)(i & 3) / 4.0;
        double d1 = (double)(j & 3) / 4.0;
        double d2 = (double)(k & 3) / 4.0;
        int k1 = 0;
        double d3 = Double.POSITIVE_INFINITY;

        for (int l1 = 0; l1 < 8; l1++) {
            boolean flag = (l1 & 4) == 0;
            boolean flag1 = (l1 & 2) == 0;
            boolean flag2 = (l1 & 1) == 0;
            int i2 = flag ? l : l + 1;
            int j2 = flag1 ? i1 : i1 + 1;
            int k2 = flag2 ? j1 : j1 + 1;
            double d4 = flag ? d0 : d0 - 1.0;
            double d5 = flag1 ? d1 : d1 - 1.0;
            double d6 = flag2 ? d2 : d2 - 1.0;
            double d7 = getFiddledDistance(this.biomeZoomSeed, i2, j2, k2, d4, d5, d6);
            if (d3 > d7) {
                k1 = l1;
                d3 = d7;
            }
        }

        int l2 = (k1 & 4) == 0 ? l : l + 1;
        int i3 = (k1 & 2) == 0 ? i1 : i1 + 1;
        int j3 = (k1 & 1) == 0 ? j1 : j1 + 1;
        return this.noiseBiomeSource.getNoiseBiome(l2, i3, j3);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(double x, double y, double z) {
        int i = QuartPos.fromBlock(Mth.floor(x));
        int j = QuartPos.fromBlock(Mth.floor(y));
        int k = QuartPos.fromBlock(Mth.floor(z));
        return this.getNoiseBiomeAtQuart(i, j, k);
    }

    public Holder<Biome> getNoiseBiomeAtPosition(BlockPos pos) {
        int i = QuartPos.fromBlock(pos.getX());
        int j = QuartPos.fromBlock(pos.getY());
        int k = QuartPos.fromBlock(pos.getZ());
        return this.getNoiseBiomeAtQuart(i, j, k);
    }

    public Holder<Biome> getNoiseBiomeAtQuart(int x, int y, int z) {
        return this.noiseBiomeSource.getNoiseBiome(x, y, z);
    }

    private static double getFiddledDistance(long seed, int x, int y, int z, double xNoise, double yNoise, double zNoise) {
        long $$7 = LinearCongruentialGenerator.next(seed, (long)x);
        $$7 = LinearCongruentialGenerator.next($$7, (long)y);
        $$7 = LinearCongruentialGenerator.next($$7, (long)z);
        $$7 = LinearCongruentialGenerator.next($$7, (long)x);
        $$7 = LinearCongruentialGenerator.next($$7, (long)y);
        $$7 = LinearCongruentialGenerator.next($$7, (long)z);
        double d0 = getFiddle($$7);
        $$7 = LinearCongruentialGenerator.next($$7, seed);
        double d1 = getFiddle($$7);
        $$7 = LinearCongruentialGenerator.next($$7, seed);
        double d2 = getFiddle($$7);
        return Mth.square(zNoise + d2) + Mth.square(yNoise + d1) + Mth.square(xNoise + d0);
    }

    private static double getFiddle(long seed) {
        double d0 = (double)Math.floorMod(seed >> 24, 1024) / 1024.0;
        return (d0 - 0.5) * 0.9;
    }

    public interface NoiseBiomeSource {
        /**
         * Gets the biome at the given quart positions.
         * Note that the coordinates passed into this method are 1/4 the scale of block coordinates.
         */
        Holder<Biome> getNoiseBiome(int x, int y, int z);
    }
}
