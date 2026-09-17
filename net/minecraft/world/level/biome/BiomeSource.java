package net.minecraft.world.level.biome;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;

public abstract class BiomeSource implements BiomeResolver {
    public static final Codec<BiomeSource> CODEC = BuiltInRegistries.BIOME_SOURCE.byNameCodec().dispatchStable(BiomeSource::codec, Function.identity());
    private final Supplier<Set<Holder<Biome>>> possibleBiomes = Suppliers.memoize(
        () -> this.collectPossibleBiomes().distinct().collect(ImmutableSet.toImmutableSet())
    );

    protected BiomeSource() {
    }

    protected abstract MapCodec<? extends BiomeSource> codec();

    protected abstract Stream<Holder<Biome>> collectPossibleBiomes();

    public Set<Holder<Biome>> possibleBiomes() {
        return this.possibleBiomes.get();
    }

    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        int i = QuartPos.fromBlock(x - radius);
        int j = QuartPos.fromBlock(y - radius);
        int k = QuartPos.fromBlock(z - radius);
        int l = QuartPos.fromBlock(x + radius);
        int i1 = QuartPos.fromBlock(y + radius);
        int j1 = QuartPos.fromBlock(z + radius);
        int k1 = l - i + 1;
        int l1 = i1 - j + 1;
        int i2 = j1 - k + 1;
        Set<Holder<Biome>> set = Sets.newHashSet();

        for (int j2 = 0; j2 < i2; j2++) {
            for (int k2 = 0; k2 < k1; k2++) {
                for (int l2 = 0; l2 < l1; l2++) {
                    int i3 = i + k2;
                    int j3 = j + l2;
                    int k3 = k + j2;
                    set.add(this.getNoiseBiome(i3, j3, k3, sampler));
                }
            }
        }

        return set;
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x, int y, int z, int radius, Predicate<Holder<Biome>> biomePredicate, RandomSource random, Climate.Sampler sampler
    ) {
        return this.findBiomeHorizontal(x, y, z, radius, 1, biomePredicate, random, false, sampler);
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(
        BlockPos pos, int radius, int horizontalStep, int verticalStep, Predicate<Holder<Biome>> biomePredicate, Climate.Sampler sampler, LevelReader level
    ) {
        Set<Holder<Biome>> set = this.possibleBiomes().stream().filter(biomePredicate).collect(Collectors.toUnmodifiableSet());
        if (set.isEmpty()) {
            return null;
        } else {
            int i = Math.floorDiv(radius, horizontalStep);
            int[] aint = Mth.outFromOrigin(pos.getY(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight(), verticalStep).toArray();

            for (BlockPos.MutableBlockPos blockpos$mutableblockpos : BlockPos.spiralAround(BlockPos.ZERO, i, Direction.EAST, Direction.SOUTH)) {
                int j = pos.getX() + blockpos$mutableblockpos.getX() * horizontalStep;
                int k = pos.getZ() + blockpos$mutableblockpos.getZ() * horizontalStep;
                int l = QuartPos.fromBlock(j);
                int i1 = QuartPos.fromBlock(k);

                for (int j1 : aint) {
                    int k1 = QuartPos.fromBlock(j1);
                    Holder<Biome> holder = this.getNoiseBiome(l, k1, i1, sampler);
                    if (set.contains(holder)) {
                        return Pair.of(new BlockPos(j, j1, k), holder);
                    }
                }
            }

            return null;
        }
    }

    @Nullable
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(
        int x,
        int y,
        int z,
        int radius,
        int increment,
        Predicate<Holder<Biome>> biomePredicate,
        RandomSource random,
        boolean findClosest,
        Climate.Sampler sampler
    ) {
        int i = QuartPos.fromBlock(x);
        int j = QuartPos.fromBlock(z);
        int k = QuartPos.fromBlock(radius);
        int l = QuartPos.fromBlock(y);
        Pair<BlockPos, Holder<Biome>> pair = null;
        int i1 = 0;
        int j1 = findClosest ? 0 : k;
        int k1 = j1;

        while (k1 <= k) {
            for (int l1 = SharedConstants.debugGenerateSquareTerrainWithoutNoise ? 0 : -k1; l1 <= k1; l1 += increment) {
                boolean flag = Math.abs(l1) == k1;

                for (int i2 = -k1; i2 <= k1; i2 += increment) {
                    if (findClosest) {
                        boolean flag1 = Math.abs(i2) == k1;
                        if (!flag1 && !flag) {
                            continue;
                        }
                    }

                    int k2 = i + i2;
                    int j2 = j + l1;
                    Holder<Biome> holder = this.getNoiseBiome(k2, l, j2, sampler);
                    if (biomePredicate.test(holder)) {
                        if (pair == null || random.nextInt(i1 + 1) == 0) {
                            BlockPos blockpos = new BlockPos(QuartPos.toBlock(k2), y, QuartPos.toBlock(j2));
                            if (findClosest) {
                                return Pair.of(blockpos, holder);
                            }

                            pair = Pair.of(blockpos, holder);
                        }

                        i1++;
                    }
                }
            }

            k1 += increment;
        }

        return pair;
    }

    @Override
    public abstract Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler);

    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
    }
}
