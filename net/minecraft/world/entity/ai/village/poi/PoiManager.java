package net.minecraft.world.entity.ai.village.poi;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;

public class PoiManager extends SectionStorage<PoiSection> {
    public static final int MAX_VILLAGE_DISTANCE = 6;
    public static final int VILLAGE_SECTION_SIZE = 1;
    private final PoiManager.DistanceTracker distanceTracker;
    private final LongSet loadedChunks = new LongOpenHashSet();

    public PoiManager(
        RegionStorageInfo info,
        Path folder,
        DataFixer fixerUpper,
        boolean sync,
        RegistryAccess registryAccess,
        ChunkIOErrorReporter errorReporter,
        LevelHeightAccessor levelHeightAccessor
    ) {
        super(
            new SimpleRegionStorage(info, folder, fixerUpper, sync, DataFixTypes.POI_CHUNK),
            PoiSection::codec,
            PoiSection::new,
            registryAccess,
            errorReporter,
            levelHeightAccessor
        );
        this.distanceTracker = new PoiManager.DistanceTracker();
    }

    public void add(BlockPos pos, Holder<PoiType> type) {
        this.getOrCreate(SectionPos.asLong(pos)).add(pos, type);
    }

    public void remove(BlockPos pos) {
        this.getOrLoad(SectionPos.asLong(pos)).ifPresent(p_148657_ -> p_148657_.remove(pos));
    }

    public long getCountInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        return this.getInRange(typePredicate, pos, distance, status).count();
    }

    public boolean existsAtPosition(ResourceKey<PoiType> type, BlockPos pos) {
        return this.exists(pos, p_217879_ -> p_217879_.is(type));
    }

    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        int i = Math.floorDiv(distance, 16) + 1;
        return ChunkPos.rangeClosed(new ChunkPos(pos), i).flatMap(p_217938_ -> this.getInChunk(typePredicate, p_217938_, status)).filter(p_217971_ -> {
            BlockPos blockpos = p_217971_.getPos();
            return Math.abs(blockpos.getX() - pos.getX()) <= distance && Math.abs(blockpos.getZ() - pos.getZ()) <= distance;
        });
    }

    public Stream<PoiRecord> getInRange(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        int i = distance * distance;
        return this.getInSquare(typePredicate, pos, distance, status).filter(p_217906_ -> p_217906_.getPos().distSqr(pos) <= (double)i);
    }

    @VisibleForDebug
    public Stream<PoiRecord> getInChunk(Predicate<Holder<PoiType>> typePredicate, ChunkPos posChunk, PoiManager.Occupancy status) {
        return IntStream.range(this.levelHeightAccessor.getMinSection(), this.levelHeightAccessor.getMaxSection())
            .boxed()
            .map(p_217886_ -> this.getOrLoad(SectionPos.of(posChunk, p_217886_).asLong()))
            .filter(Optional::isPresent)
            .flatMap(p_217942_ -> p_217942_.get().getRecords(typePredicate, status));
    }

    public Stream<BlockPos> findAll(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.getInRange(typePredicate, pos, distance, status).map(PoiRecord::getPos).filter(posPredicate);
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllWithType(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.getInRange(typePredicate, pos, distance, status)
            .filter(p_217982_ -> posPredicate.test(p_217982_.getPos()))
            .map(p_217990_ -> Pair.of(p_217990_.getPoiType(), p_217990_.getPos()));
    }

    public Stream<Pair<Holder<PoiType>, BlockPos>> findAllClosestFirstWithType(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.findAllWithType(typePredicate, posPredicate, pos, distance, status)
            .sorted(Comparator.comparingDouble(p_217915_ -> p_217915_.getSecond().distSqr(pos)));
    }

    public Optional<BlockPos> find(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.findAll(typePredicate, posPredicate, pos, distance, status).findFirst();
    }

    public Optional<BlockPos> findClosest(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status) {
        return this.getInRange(typePredicate, pos, distance, status)
            .map(PoiRecord::getPos)
            .min(Comparator.comparingDouble(p_217977_ -> p_217977_.distSqr(pos)));
    }

    public Optional<Pair<Holder<PoiType>, BlockPos>> findClosestWithType(
        Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.getInRange(typePredicate, pos, distance, status)
            .min(Comparator.comparingDouble(p_217909_ -> p_217909_.getPos().distSqr(pos)))
            .map(p_217959_ -> Pair.of(p_217959_.getPoiType(), p_217959_.getPos()));
    }

    public Optional<BlockPos> findClosest(
        Predicate<Holder<PoiType>> typePredicate, Predicate<BlockPos> posPredicate, BlockPos pos, int distance, PoiManager.Occupancy status
    ) {
        return this.getInRange(typePredicate, pos, distance, status)
            .map(PoiRecord::getPos)
            .filter(posPredicate)
            .min(Comparator.comparingDouble(p_217918_ -> p_217918_.distSqr(pos)));
    }

    public Optional<BlockPos> take(Predicate<Holder<PoiType>> typePredicate, BiPredicate<Holder<PoiType>, BlockPos> combinedTypePosPredicate, BlockPos pos, int distance) {
        return this.getInRange(typePredicate, pos, distance, PoiManager.Occupancy.HAS_SPACE)
            .filter(p_217934_ -> combinedTypePosPredicate.test(p_217934_.getPoiType(), p_217934_.getPos()))
            .findFirst()
            .map(p_217881_ -> {
                p_217881_.acquireTicket();
                return p_217881_.getPos();
            });
    }

    public Optional<BlockPos> getRandom(
        Predicate<Holder<PoiType>> typePredicate,
        Predicate<BlockPos> posPredicate,
        PoiManager.Occupancy status,
        BlockPos pos,
        int distance,
        RandomSource random
    ) {
        List<PoiRecord> list = Util.toShuffledList(this.getInRange(typePredicate, pos, distance, status), random);
        return list.stream().filter(p_217945_ -> posPredicate.test(p_217945_.getPos())).findFirst().map(PoiRecord::getPos);
    }

    public boolean release(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos))
            .map(p_217993_ -> p_217993_.release(pos))
            .orElseThrow(() -> Util.pauseInIde(new IllegalStateException("POI never registered at " + pos)));
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> typePredicate) {
        return this.getOrLoad(SectionPos.asLong(pos)).map(p_217925_ -> p_217925_.exists(pos, typePredicate)).orElse(false);
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).flatMap(p_217974_ -> p_217974_.getType(pos));
    }

    @Deprecated
    @VisibleForDebug
    public int getFreeTickets(BlockPos pos) {
        return this.getOrLoad(SectionPos.asLong(pos)).map(p_217912_ -> p_217912_.getFreeTickets(pos)).orElse(0);
    }

    public int sectionsToVillage(SectionPos sectionPos) {
        this.distanceTracker.runAllUpdates();
        return this.distanceTracker.getLevel(sectionPos.asLong());
    }

    boolean isVillageCenter(long chunkPos) {
        Optional<PoiSection> optional = this.get(chunkPos);
        return optional == null
            ? false
            : optional.<Boolean>map(
                    p_217883_ -> p_217883_.getRecords(p_217927_ -> p_217927_.is(PoiTypeTags.VILLAGE), PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent()
                )
                .orElse(false);
    }

    @Override
    public void tick(BooleanSupplier aheadOfTime) {
        super.tick(aheadOfTime);
        this.distanceTracker.runAllUpdates();
    }

    @Override
    protected void setDirty(long sectionPos) {
        super.setDirty(sectionPos);
        this.distanceTracker.update(sectionPos, this.distanceTracker.getLevelFromSource(sectionPos), false);
    }

    @Override
    protected void onSectionLoad(long sectionKey) {
        this.distanceTracker.update(sectionKey, this.distanceTracker.getLevelFromSource(sectionKey), false);
    }

    public void checkConsistencyWithBlocks(SectionPos sectionPos, LevelChunkSection levelChunkSection) {
        Util.ifElse(this.getOrLoad(sectionPos.asLong()), p_217898_ -> p_217898_.refresh(p_217967_ -> {
                if (mayHavePoi(levelChunkSection)) {
                    this.updateFromSection(levelChunkSection, sectionPos, p_217967_);
                }
            }), () -> {
            if (mayHavePoi(levelChunkSection)) {
                PoiSection poisection = this.getOrCreate(sectionPos.asLong());
                this.updateFromSection(levelChunkSection, sectionPos, poisection::add);
            }
        });
    }

    private static boolean mayHavePoi(LevelChunkSection section) {
        return section.maybeHas(PoiTypes::hasPoi);
    }

    private void updateFromSection(LevelChunkSection section, SectionPos sectionPos, BiConsumer<BlockPos, Holder<PoiType>> posToTypeConsumer) {
        sectionPos.blocksInside()
            .forEach(
                p_217902_ -> {
                    BlockState blockstate = section.getBlockState(
                        SectionPos.sectionRelative(p_217902_.getX()),
                        SectionPos.sectionRelative(p_217902_.getY()),
                        SectionPos.sectionRelative(p_217902_.getZ())
                    );
                    PoiTypes.forState(blockstate).ifPresent(p_217931_ -> posToTypeConsumer.accept(p_217902_, (Holder<PoiType>)p_217931_));
                }
            );
    }

    public void ensureLoadedAndValid(LevelReader levelReader, BlockPos pos, int coordinateOffset) {
        SectionPos.aroundChunk(
                new ChunkPos(pos), Math.floorDiv(coordinateOffset, 16), this.levelHeightAccessor.getMinSection(), this.levelHeightAccessor.getMaxSection()
            )
            .map(p_217979_ -> Pair.of(p_217979_, this.getOrLoad(p_217979_.asLong())))
            .filter(p_217963_ -> !p_217963_.getSecond().map(PoiSection::isValid).orElse(false))
            .map(p_217891_ -> p_217891_.getFirst().chunk())
            .filter(p_217961_ -> this.loadedChunks.add(p_217961_.toLong()))
            .forEach(p_330057_ -> levelReader.getChunk(p_330057_.x, p_330057_.z, ChunkStatus.EMPTY));
    }

    final class DistanceTracker extends SectionTracker {
        private final Long2ByteMap levels = new Long2ByteOpenHashMap();

        protected DistanceTracker() {
            super(7, 16, 256);
            this.levels.defaultReturnValue((byte)7);
        }

        @Override
        protected int getLevelFromSource(long pos) {
            return PoiManager.this.isVillageCenter(pos) ? 0 : 7;
        }

        @Override
        protected int getLevel(long sectionPos) {
            return this.levels.get(sectionPos);
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            if (level > 6) {
                this.levels.remove(sectionPos);
            } else {
                this.levels.put(sectionPos, (byte)level);
            }
        }

        public void runAllUpdates() {
            super.runUpdates(Integer.MAX_VALUE);
        }
    }

    public static enum Occupancy {
        HAS_SPACE(PoiRecord::hasSpace),
        IS_OCCUPIED(PoiRecord::isOccupied),
        ANY(p_27223_ -> true);

        private final Predicate<? super PoiRecord> test;

        private Occupancy(Predicate<? super PoiRecord> test) {
            this.test = test;
        }

        public Predicate<? super PoiRecord> getTest() {
            return this.test;
        }
    }
}
