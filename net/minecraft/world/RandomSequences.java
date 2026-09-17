package net.minecraft.world;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class RandomSequences extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final long worldSeed;
    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<ResourceLocation, RandomSequence> sequences = new Object2ObjectOpenHashMap<>();

    public static SavedData.Factory<RandomSequences> factory(long seed) {
        return new SavedData.Factory<>(
            () -> new RandomSequences(seed), (p_293846_, p_324262_) -> load(seed, p_293846_), DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES
        );
    }

    public RandomSequences(long seed) {
        this.worldSeed = seed;
    }

    public RandomSource get(ResourceLocation location) {
        RandomSource randomsource = this.sequences.computeIfAbsent(location, this::createSequence).random();
        return new RandomSequences.DirtyMarkingRandomSource(randomsource);
    }

    private RandomSequence createSequence(ResourceLocation location) {
        return this.createSequence(location, this.salt, this.includeWorldSeed, this.includeSequenceId);
    }

    private RandomSequence createSequence(ResourceLocation location, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        long i = (includeWorldSeed ? this.worldSeed : 0L) ^ (long)salt;
        return new RandomSequence(i, includeSequenceId ? Optional.of(location) : Optional.empty());
    }

    public void forAllSequences(BiConsumer<ResourceLocation, RandomSequence> action) {
        this.sequences.forEach(action);
    }

    public void setSeedDefaults(int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.salt = salt;
        this.includeWorldSeed = includeWorldSeed;
        this.includeSequenceId = includeSequenceId;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("salt", this.salt);
        tag.putBoolean("include_world_seed", this.includeWorldSeed);
        tag.putBoolean("include_sequence_id", this.includeSequenceId);
        CompoundTag compoundtag = new CompoundTag();
        this.sequences
            .forEach(
                (p_337697_, p_337698_) -> compoundtag.put(
                        p_337697_.toString(), RandomSequence.CODEC.encodeStart(NbtOps.INSTANCE, p_337698_).result().orElseThrow()
                    )
            );
        tag.put("sequences", compoundtag);
        return tag;
    }

    private static boolean getBooleanWithDefault(CompoundTag tag, String key, boolean defaultValue) {
        return tag.contains(key, 1) ? tag.getBoolean(key) : defaultValue;
    }

    public static RandomSequences load(long seed, CompoundTag tag) {
        RandomSequences randomsequences = new RandomSequences(seed);
        randomsequences.setSeedDefaults(
            tag.getInt("salt"),
            getBooleanWithDefault(tag, "include_world_seed", true),
            getBooleanWithDefault(tag, "include_sequence_id", true)
        );
        CompoundTag compoundtag = tag.getCompound("sequences");

        for (String s : compoundtag.getAllKeys()) {
            try {
                RandomSequence randomsequence = RandomSequence.CODEC.decode(NbtOps.INSTANCE, compoundtag.get(s)).result().get().getFirst();
                randomsequences.sequences.put(ResourceLocation.parse(s), randomsequence);
            } catch (Exception exception) {
                LOGGER.error("Failed to load random sequence {}", s, exception);
            }
        }

        return randomsequences;
    }

    public int clear() {
        int i = this.sequences.size();
        this.sequences.clear();
        return i;
    }

    public void reset(ResourceLocation sequence) {
        this.sequences.put(sequence, this.createSequence(sequence));
    }

    public void reset(ResourceLocation sequence, int seed, boolean includeWorldSeed, boolean includeSequenceId) {
        this.sequences.put(sequence, this.createSequence(sequence, seed, includeWorldSeed, includeSequenceId));
    }

    class DirtyMarkingRandomSource implements RandomSource {
        private final RandomSource random;

        DirtyMarkingRandomSource(RandomSource random) {
            this.random = random;
        }

        @Override
        public RandomSource fork() {
            RandomSequences.this.setDirty();
            return this.random.fork();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            RandomSequences.this.setDirty();
            return this.random.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            RandomSequences.this.setDirty();
            this.random.setSeed(seed);
        }

        @Override
        public int nextInt() {
            RandomSequences.this.setDirty();
            return this.random.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            RandomSequences.this.setDirty();
            return this.random.nextInt(bound);
        }

        @Override
        public long nextLong() {
            RandomSequences.this.setDirty();
            return this.random.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            RandomSequences.this.setDirty();
            return this.random.nextBoolean();
        }

        @Override
        public float nextFloat() {
            RandomSequences.this.setDirty();
            return this.random.nextFloat();
        }

        @Override
        public double nextDouble() {
            RandomSequences.this.setDirty();
            return this.random.nextDouble();
        }

        @Override
        public double nextGaussian() {
            RandomSequences.this.setDirty();
            return this.random.nextGaussian();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else {
                return other instanceof RandomSequences.DirtyMarkingRandomSource randomsequences$dirtymarkingrandomsource
                    ? this.random.equals(randomsequences$dirtymarkingrandomsource.random)
                    : false;
            }
        }
    }
}
