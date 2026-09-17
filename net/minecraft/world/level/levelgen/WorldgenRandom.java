package net.minecraft.world.level.levelgen;

import java.util.function.LongFunction;
import net.minecraft.util.RandomSource;

public class WorldgenRandom extends LegacyRandomSource {
    private final RandomSource randomSource;
    private int count;

    public WorldgenRandom(RandomSource randomSource) {
        super(0L);
        this.randomSource = randomSource;
    }

    public int getCount() {
        return this.count;
    }

    @Override
    public RandomSource fork() {
        return this.randomSource.fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    @Override
    public int next(int bits) {
        this.count++;
        return this.randomSource instanceof LegacyRandomSource legacyrandomsource
            ? legacyrandomsource.next(bits)
            : (int)(this.randomSource.nextLong() >>> 64 - bits);
    }

    @Override
    public synchronized void setSeed(long seed) {
        if (this.randomSource != null) {
            this.randomSource.setSeed(seed);
        }
    }

    /**
     * Seeds the current random for chunk decoration, including spawning mobs and for use in feature placement.
     * The coordinates correspond to the minimum block position within a given chunk.
     */
    public long setDecorationSeed(long levelSeed, int minChunkBlockX, int minChunkBlockZ) {
        this.setSeed(levelSeed);
        long i = this.nextLong() | 1L;
        long j = this.nextLong() | 1L;
        long k = (long)minChunkBlockX * i + (long)minChunkBlockZ * j ^ levelSeed;
        this.setSeed(k);
        return k;
    }

    /**
     * Seeds the current random for placing features.
     * Each feature is seeded differently in order to seem more random. However, it does not do a good job of this, and issues can arise from the salt being small with features that have the same decoration step and are close together in the feature lists.
     *
     * @param decorationSeed The seed computed by {@link #setDecorationSeed(long, int,
     *                       int)}
     * @param index          The cumulative index of the generating feature within the
     *                       biome's list of features.
     * @param decorationStep The ordinal of the {@link
     *                       net.minecraft.world.level.levelgen.GenerationStep.Decoration
     *                       } of the generating feature.
     */
    public void setFeatureSeed(long decorationSeed, int index, int decorationStep) {
        long i = decorationSeed + (long)index + (long)(10000 * decorationStep);
        this.setSeed(i);
    }

    /**
     * Seeds the current random for placing large features such as caves, strongholds, and mineshafts.
     *
     * @param baseSeed This is passed in as the level seed, or in some cases such as
     *                 carvers, as an offset from the level seed unique to each carver
     *                 .
     */
    public void setLargeFeatureSeed(long baseSeed, int chunkX, int chunkZ) {
        this.setSeed(baseSeed);
        long i = this.nextLong();
        long j = this.nextLong();
        long k = (long)chunkX * i ^ (long)chunkZ * j ^ baseSeed;
        this.setSeed(k);
    }

    /**
     * Seeds the current random for placing the starts of structure features.
     * The region coordinates are the region which the target chunk lies in. For example, witch hut regions are 32x32 chunks, so all chunks within that region would be seeded identically.
     * The size of the regions themselves are determined by the {@code spacing} of the structure settings.
     *
     * @param salt A salt unique to each structure.
     */
    public void setLargeFeatureWithSalt(long levelSeed, int regionX, int regionZ, int salt) {
        long i = (long)regionX * 341873128712L + (long)regionZ * 132897987541L + levelSeed + (long)salt;
        this.setSeed(i);
    }

    /**
     * Creates a new {@code RandomSource}, seeded for determining whether a chunk is a slime chunk or not.
     *
     * @param salt For vanilla slimes, this is always {@code 987234911L}
     */
    public static RandomSource seedSlimeChunk(int chunkX, int chunkZ, long levelSeed, long salt) {
        return RandomSource.create(
            levelSeed
                    + (long)(chunkX * chunkX * 4987142)
                    + (long)(chunkX * 5947611)
                    + (long)(chunkZ * chunkZ) * 4392871L
                    + (long)(chunkZ * 389711)
                ^ salt
        );
    }

    public static enum Algorithm {
        LEGACY(LegacyRandomSource::new),
        XOROSHIRO(XoroshiroRandomSource::new);

        private final LongFunction<RandomSource> constructor;

        private Algorithm(LongFunction<RandomSource> constructor) {
            this.constructor = constructor;
        }

        public RandomSource newInstance(long seed) {
            return this.constructor.apply(seed);
        }
    }
}
