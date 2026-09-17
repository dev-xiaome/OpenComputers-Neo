package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

/**
 * A carver which creates Minecraft's most common cave types.
 */
public class CaveWorldCarver extends WorldCarver<CaveCarverConfiguration> {
    public CaveWorldCarver(Codec<CaveCarverConfiguration> codec) {
        super(codec);
    }

    public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    /**
     * Carves the given chunk with caves that originate from the given {@code chunkPos}.
     * This method is invoked 289 times in order to generate each chunk (once for every position in an 8 chunk radius, or 17x17 chunk area, centered around the target chunk).
     *
     * @see net.minecraft.world.level.chunk.ChunkGenerator#applyCarvers
     *
     * @param chunk    The chunk to be carved
     * @param chunkPos The chunk position this carver is being called from
     */
    public boolean carve(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        RandomSource random,
        Aquifer aquifer,
        ChunkPos chunkPos,
        CarvingMask carvingMask
    ) {
        int i = SectionPos.sectionToBlockCoord(this.getRange() * 2 - 1);
        int j = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for (int k = 0; k < j; k++) {
            double d0 = (double)chunkPos.getBlockX(random.nextInt(16));
            double d1 = (double)config.y.sample(random, context);
            double d2 = (double)chunkPos.getBlockZ(random.nextInt(16));
            double d3 = (double)config.horizontalRadiusMultiplier.sample(random);
            double d4 = (double)config.verticalRadiusMultiplier.sample(random);
            double d5 = (double)config.floorLevel.sample(random);
            WorldCarver.CarveSkipChecker worldcarver$carveskipchecker = (p_159202_, p_159203_, p_159204_, p_159205_, p_159206_) -> shouldSkip(
                    p_159203_, p_159204_, p_159205_, d5
                );
            int l = 1;
            if (random.nextInt(4) == 0) {
                double d6 = (double)config.yScale.sample(random);
                float f1 = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(context, config, chunk, biomeAccessor, aquifer, d0, d1, d2, f1, d6, carvingMask, worldcarver$carveskipchecker);
                l += random.nextInt(4);
            }

            for (int k1 = 0; k1 < l; k1++) {
                float f = random.nextFloat() * (float) (Math.PI * 2);
                float f3 = (random.nextFloat() - 0.5F) / 4.0F;
                float f2 = this.getThickness(random);
                int i1 = i - random.nextInt(i / 4);
                int j1 = 0;
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    random.nextLong(),
                    aquifer,
                    d0,
                    d1,
                    d2,
                    d3,
                    d4,
                    f2,
                    f,
                    f3,
                    0,
                    i1,
                    this.getYScale(),
                    carvingMask,
                    worldcarver$carveskipchecker
                );
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return f;
    }

    protected double getYScale() {
        return 1.0;
    }

    protected void createRoom(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        float radius,
        double horizontalVerticalRatio,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        double d0 = 1.5 + (double)(Mth.sin((float) (Math.PI / 2)) * radius);
        double d1 = d0 * horizontalVerticalRatio;
        this.carveEllipsoid(context, config, chunk, biomeAccessor, aquifer, x + 1.0, y, z, d0, d1, carvingMask, skipChecker);
    }

    protected void createTunnel(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> biomeAccessor,
        long seed,
        Aquifer aquifer,
        double x,
        double y,
        double z,
        double horizontalRadiusMultiplier,
        double verticalRadiusMultiplier,
        float thickness,
        float yaw,
        float pitch,
        int branchIndex,
        int branchCount,
        double horizontalVerticalRatio,
        CarvingMask carvingMask,
        WorldCarver.CarveSkipChecker skipChecker
    ) {
        RandomSource randomsource = RandomSource.create(seed);
        int i = randomsource.nextInt(branchCount / 2) + branchCount / 4;
        boolean flag = randomsource.nextInt(6) == 0;
        float f = 0.0F;
        float f1 = 0.0F;

        for (int j = branchIndex; j < branchCount; j++) {
            double d0 = 1.5 + (double)(Mth.sin((float) Math.PI * (float)j / (float)branchCount) * thickness);
            double d1 = d0 * horizontalVerticalRatio;
            float f2 = Mth.cos(pitch);
            x += (double)(Mth.cos(yaw) * f2);
            y += (double)Mth.sin(pitch);
            z += (double)(Mth.sin(yaw) * f2);
            pitch *= flag ? 0.92F : 0.7F;
            pitch += f1 * 0.1F;
            yaw += f * 0.1F;
            f1 *= 0.9F;
            f *= 0.75F;
            f1 += (randomsource.nextFloat() - randomsource.nextFloat()) * randomsource.nextFloat() * 2.0F;
            f += (randomsource.nextFloat() - randomsource.nextFloat()) * randomsource.nextFloat() * 4.0F;
            if (j == i && thickness > 1.0F) {
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    randomsource.nextLong(),
                    aquifer,
                    x,
                    y,
                    z,
                    horizontalRadiusMultiplier,
                    verticalRadiusMultiplier,
                    randomsource.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) (Math.PI / 2),
                    pitch / 3.0F,
                    j,
                    branchCount,
                    1.0,
                    carvingMask,
                    skipChecker
                );
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    randomsource.nextLong(),
                    aquifer,
                    x,
                    y,
                    z,
                    horizontalRadiusMultiplier,
                    verticalRadiusMultiplier,
                    randomsource.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) (Math.PI / 2),
                    pitch / 3.0F,
                    j,
                    branchCount,
                    1.0,
                    carvingMask,
                    skipChecker
                );
                return;
            }

            if (randomsource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, j, branchCount, thickness)) {
                    return;
                }

                this.carveEllipsoid(
                    context,
                    config,
                    chunk,
                    biomeAccessor,
                    aquifer,
                    x,
                    y,
                    z,
                    d0 * horizontalRadiusMultiplier,
                    d1 * verticalRadiusMultiplier,
                    carvingMask,
                    skipChecker
                );
            }
        }
    }

    private static boolean shouldSkip(double relative, double relativeY, double relativeZ, double minrelativeY) {
        return relativeY <= minrelativeY ? true : relative * relative + relativeY * relativeY + relativeZ * relativeZ >= 1.0;
    }
}
