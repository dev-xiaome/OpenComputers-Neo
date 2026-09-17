package net.minecraft.world.level.levelgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

public class Heightmap {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Predicate<BlockState> NOT_AIR = p_284913_ -> !p_284913_.isAir();
    static final Predicate<BlockState> MATERIAL_MOTION_BLOCKING = BlockBehaviour.BlockStateBase::blocksMotion;
    private final BitStorage data;
    private final Predicate<BlockState> isOpaque;
    private final ChunkAccess chunk;

    public Heightmap(ChunkAccess chunk, Heightmap.Types type) {
        this.isOpaque = type.isOpaque();
        this.chunk = chunk;
        int i = Mth.ceillog2(chunk.getHeight() + 1);
        this.data = new SimpleBitStorage(i, 256);
    }

    public static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types) {
        int i = types.size();
        ObjectList<Heightmap> objectlist = new ObjectArrayList<>(i);
        ObjectListIterator<Heightmap> objectlistiterator = objectlist.iterator();
        int j = chunk.getHighestSectionPosition() + 16;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; k++) {
            for (int l = 0; l < 16; l++) {
                for (Heightmap.Types heightmap$types : types) {
                    objectlist.add(chunk.getOrCreateHeightmapUnprimed(heightmap$types));
                }

                for (int i1 = j - 1; i1 >= chunk.getMinBuildHeight(); i1--) {
                    blockpos$mutableblockpos.set(k, i1, l);
                    BlockState blockstate = chunk.getBlockState(blockpos$mutableblockpos);
                    if (!blockstate.is(Blocks.AIR)) {
                        while (objectlistiterator.hasNext()) {
                            Heightmap heightmap = objectlistiterator.next();
                            if (heightmap.isOpaque.test(blockstate)) {
                                heightmap.setHeight(k, l, i1 + 1);
                                objectlistiterator.remove();
                            }
                        }

                        if (objectlist.isEmpty()) {
                            break;
                        }

                        objectlistiterator.back(i);
                    }
                }
            }
        }
    }

    public boolean update(int x, int y, int z, BlockState state) {
        int i = this.getFirstAvailable(x, z);
        if (y <= i - 2) {
            return false;
        } else {
            if (this.isOpaque.test(state)) {
                if (y >= i) {
                    this.setHeight(x, z, y + 1);
                    return true;
                }
            } else if (i - 1 == y) {
                BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

                for (int j = y - 1; j >= this.chunk.getMinBuildHeight(); j--) {
                    blockpos$mutableblockpos.set(x, j, z);
                    if (this.isOpaque.test(this.chunk.getBlockState(blockpos$mutableblockpos))) {
                        this.setHeight(x, z, j + 1);
                        return true;
                    }
                }

                this.setHeight(x, z, this.chunk.getMinBuildHeight());
                return true;
            }

            return false;
        }
    }

    public int getFirstAvailable(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z));
    }

    public int getHighestTaken(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z)) - 1;
    }

    private int getFirstAvailable(int index) {
        return this.data.get(index) + this.chunk.getMinBuildHeight();
    }

    private void setHeight(int x, int z, int value) {
        this.data.set(getIndex(x, z), value - this.chunk.getMinBuildHeight());
    }

    public void setRawData(ChunkAccess chunk, Heightmap.Types type, long[] data) {
        long[] along = this.data.getRaw();
        if (along.length == data.length) {
            System.arraycopy(data, 0, along, 0, data.length);
        } else {
            LOGGER.warn(
                "Ignoring heightmap data for chunk " + chunk.getPos() + ", size does not match; expected: " + along.length + ", got: " + data.length
            );
            primeHeightmaps(chunk, EnumSet.of(type));
        }
    }

    public long[] getRawData() {
        return this.data.getRaw();
    }

    private static int getIndex(int x, int z) {
        return x + z * 16;
    }

    public static enum Types implements StringRepresentable {
        WORLD_SURFACE_WG("WORLD_SURFACE_WG", Heightmap.Usage.WORLDGEN, Heightmap.NOT_AIR),
        WORLD_SURFACE("WORLD_SURFACE", Heightmap.Usage.CLIENT, Heightmap.NOT_AIR),
        OCEAN_FLOOR_WG("OCEAN_FLOOR_WG", Heightmap.Usage.WORLDGEN, Heightmap.MATERIAL_MOTION_BLOCKING),
        OCEAN_FLOOR("OCEAN_FLOOR", Heightmap.Usage.LIVE_WORLD, Heightmap.MATERIAL_MOTION_BLOCKING),
        MOTION_BLOCKING("MOTION_BLOCKING", Heightmap.Usage.CLIENT, p_284915_ -> p_284915_.blocksMotion() || !p_284915_.getFluidState().isEmpty()),
        MOTION_BLOCKING_NO_LEAVES(
            "MOTION_BLOCKING_NO_LEAVES",
            Heightmap.Usage.LIVE_WORLD,
            p_284914_ -> (p_284914_.blocksMotion() || !p_284914_.getFluidState().isEmpty()) && !(p_284914_.getBlock() instanceof LeavesBlock)
        );

        public static final Codec<Heightmap.Types> CODEC = StringRepresentable.fromEnum(Heightmap.Types::values);
        private final String serializationKey;
        private final Heightmap.Usage usage;
        private final Predicate<BlockState> isOpaque;

        private Types(String serializationKey, Heightmap.Usage usage, Predicate<BlockState> isOpaque) {
            this.serializationKey = serializationKey;
            this.usage = usage;
            this.isOpaque = isOpaque;
        }

        public String getSerializationKey() {
            return this.serializationKey;
        }

        public boolean sendToClient() {
            return this.usage == Heightmap.Usage.CLIENT;
        }

        public boolean keepAfterWorldgen() {
            return this.usage != Heightmap.Usage.WORLDGEN;
        }

        public Predicate<BlockState> isOpaque() {
            return this.isOpaque;
        }

        @Override
        public String getSerializedName() {
            return this.serializationKey;
        }
    }

    public static enum Usage {
        WORLDGEN,
        LIVE_WORLD,
        CLIENT;
    }
}
