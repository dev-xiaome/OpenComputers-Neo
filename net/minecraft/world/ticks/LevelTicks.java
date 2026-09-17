package net.minecraft.world.ticks;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class LevelTicks<T> implements LevelTickAccess<T> {
    private static final Comparator<LevelChunkTicks<?>> CONTAINER_DRAIN_ORDER = (p_193246_, p_193247_) -> ScheduledTick.INTRA_TICK_DRAIN_ORDER
            .compare(p_193246_.peek(), p_193247_.peek());
    private final LongPredicate tickCheck;
    private final Supplier<ProfilerFiller> profiler;
    private final Long2ObjectMap<LevelChunkTicks<T>> allContainers = new Long2ObjectOpenHashMap<>();
    private final Long2LongMap nextTickForContainer = Util.make(new Long2LongOpenHashMap(), p_193262_ -> p_193262_.defaultReturnValue(Long.MAX_VALUE));
    private final Queue<LevelChunkTicks<T>> containersToTick = new PriorityQueue<>(CONTAINER_DRAIN_ORDER);
    private final Queue<ScheduledTick<T>> toRunThisTick = new ArrayDeque<>();
    private final List<ScheduledTick<T>> alreadyRunThisTick = new ArrayList<>();
    private final Set<ScheduledTick<?>> toRunThisTickSet = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    private final BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> chunkScheduleUpdater = (p_193249_, p_193250_) -> {
        if (p_193250_.equals(p_193249_.peek())) {
            this.updateContainerScheduling(p_193250_);
        }
    };

    public LevelTicks(LongPredicate tickCheck, Supplier<ProfilerFiller> profiler) {
        this.tickCheck = tickCheck;
        this.profiler = profiler;
    }

    public void addContainer(ChunkPos chunkPos, LevelChunkTicks<T> chunkTicks) {
        long i = chunkPos.toLong();
        this.allContainers.put(i, chunkTicks);
        ScheduledTick<T> scheduledtick = chunkTicks.peek();
        if (scheduledtick != null) {
            this.nextTickForContainer.put(i, scheduledtick.triggerTick());
        }

        chunkTicks.setOnTickAdded(this.chunkScheduleUpdater);
    }

    public void removeContainer(ChunkPos chunkPos) {
        long i = chunkPos.toLong();
        LevelChunkTicks<T> levelchunkticks = this.allContainers.remove(i);
        this.nextTickForContainer.remove(i);
        if (levelchunkticks != null) {
            levelchunkticks.setOnTickAdded(null);
        }
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        long i = ChunkPos.asLong(tick.pos());
        LevelChunkTicks<T> levelchunkticks = this.allContainers.get(i);
        if (levelchunkticks == null) {
            Util.pauseInIde(new IllegalStateException("Trying to schedule tick in not loaded position " + tick.pos()));
        } else {
            levelchunkticks.schedule(tick);
        }
    }

    public void tick(long gameTime, int maxAllowedTicks, BiConsumer<BlockPos, T> ticker) {
        ProfilerFiller profilerfiller = this.profiler.get();
        profilerfiller.push("collect");
        this.collectTicks(gameTime, maxAllowedTicks, profilerfiller);
        profilerfiller.popPush("run");
        profilerfiller.incrementCounter("ticksToRun", this.toRunThisTick.size());
        this.runCollectedTicks(ticker);
        profilerfiller.popPush("cleanup");
        this.cleanupAfterTick();
        profilerfiller.pop();
    }

    private void collectTicks(long gameTime, int maxAllowedTicks, ProfilerFiller profiler) {
        this.sortContainersToTick(gameTime);
        profiler.incrementCounter("containersToTick", this.containersToTick.size());
        this.drainContainers(gameTime, maxAllowedTicks);
        this.rescheduleLeftoverContainers();
    }

    private void sortContainersToTick(long gameTime) {
        ObjectIterator<Entry> objectiterator = Long2LongMaps.fastIterator(this.nextTickForContainer);

        while (objectiterator.hasNext()) {
            Entry entry = objectiterator.next();
            long i = entry.getLongKey();
            long j = entry.getLongValue();
            if (j <= gameTime) {
                LevelChunkTicks<T> levelchunkticks = this.allContainers.get(i);
                if (levelchunkticks == null) {
                    objectiterator.remove();
                } else {
                    ScheduledTick<T> scheduledtick = levelchunkticks.peek();
                    if (scheduledtick == null) {
                        objectiterator.remove();
                    } else if (scheduledtick.triggerTick() > gameTime) {
                        entry.setValue(scheduledtick.triggerTick());
                    } else if (this.tickCheck.test(i)) {
                        objectiterator.remove();
                        this.containersToTick.add(levelchunkticks);
                    }
                }
            }
        }
    }

    private void drainContainers(long gameTime, int maxAllowedTicks) {
        LevelChunkTicks<T> levelchunkticks;
        while (this.canScheduleMoreTicks(maxAllowedTicks) && (levelchunkticks = this.containersToTick.poll()) != null) {
            ScheduledTick<T> scheduledtick = levelchunkticks.poll();
            this.scheduleForThisTick(scheduledtick);
            this.drainFromCurrentContainer(this.containersToTick, levelchunkticks, gameTime, maxAllowedTicks);
            ScheduledTick<T> scheduledtick1 = levelchunkticks.peek();
            if (scheduledtick1 != null) {
                if (scheduledtick1.triggerTick() <= gameTime && this.canScheduleMoreTicks(maxAllowedTicks)) {
                    this.containersToTick.add(levelchunkticks);
                } else {
                    this.updateContainerScheduling(scheduledtick1);
                }
            }
        }
    }

    private void rescheduleLeftoverContainers() {
        for (LevelChunkTicks<T> levelchunkticks : this.containersToTick) {
            this.updateContainerScheduling(levelchunkticks.peek());
        }
    }

    private void updateContainerScheduling(ScheduledTick<T> tick) {
        this.nextTickForContainer.put(ChunkPos.asLong(tick.pos()), tick.triggerTick());
    }

    private void drainFromCurrentContainer(Queue<LevelChunkTicks<T>> containersToTick, LevelChunkTicks<T> levelChunkTicks, long gameTime, int maxAllowedTicks) {
        if (this.canScheduleMoreTicks(maxAllowedTicks)) {
            LevelChunkTicks<T> levelchunkticks = containersToTick.peek();
            ScheduledTick<T> scheduledtick = levelchunkticks != null ? levelchunkticks.peek() : null;

            while (this.canScheduleMoreTicks(maxAllowedTicks)) {
                ScheduledTick<T> scheduledtick1 = levelChunkTicks.peek();
                if (scheduledtick1 == null
                    || scheduledtick1.triggerTick() > gameTime
                    || scheduledtick != null && ScheduledTick.INTRA_TICK_DRAIN_ORDER.compare(scheduledtick1, scheduledtick) > 0) {
                    break;
                }

                levelChunkTicks.poll();
                this.scheduleForThisTick(scheduledtick1);
            }
        }
    }

    private void scheduleForThisTick(ScheduledTick<T> tick) {
        this.toRunThisTick.add(tick);
    }

    private boolean canScheduleMoreTicks(int maxAllowedTicks) {
        return this.toRunThisTick.size() < maxAllowedTicks;
    }

    private void runCollectedTicks(BiConsumer<BlockPos, T> ticker) {
        while (!this.toRunThisTick.isEmpty()) {
            ScheduledTick<T> scheduledtick = this.toRunThisTick.poll();
            if (!this.toRunThisTickSet.isEmpty()) {
                this.toRunThisTickSet.remove(scheduledtick);
            }

            this.alreadyRunThisTick.add(scheduledtick);
            ticker.accept(scheduledtick.pos(), scheduledtick.type());
        }
    }

    private void cleanupAfterTick() {
        this.toRunThisTick.clear();
        this.containersToTick.clear();
        this.alreadyRunThisTick.clear();
        this.toRunThisTickSet.clear();
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        LevelChunkTicks<T> levelchunkticks = this.allContainers.get(ChunkPos.asLong(pos));
        return levelchunkticks != null && levelchunkticks.hasScheduledTick(pos, type);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        this.calculateTickSetIfNeeded();
        return this.toRunThisTickSet.contains(ScheduledTick.probe(type, pos));
    }

    private void calculateTickSetIfNeeded() {
        if (this.toRunThisTickSet.isEmpty() && !this.toRunThisTick.isEmpty()) {
            this.toRunThisTickSet.addAll(this.toRunThisTick);
        }
    }

    private void forContainersInArea(BoundingBox area, LevelTicks.PosAndContainerConsumer<T> action) {
        int i = SectionPos.posToSectionCoord((double)area.minX());
        int j = SectionPos.posToSectionCoord((double)area.minZ());
        int k = SectionPos.posToSectionCoord((double)area.maxX());
        int l = SectionPos.posToSectionCoord((double)area.maxZ());

        for (int i1 = i; i1 <= k; i1++) {
            for (int j1 = j; j1 <= l; j1++) {
                long k1 = ChunkPos.asLong(i1, j1);
                LevelChunkTicks<T> levelchunkticks = this.allContainers.get(k1);
                if (levelchunkticks != null) {
                    action.accept(k1, levelchunkticks);
                }
            }
        }
    }

    public void clearArea(BoundingBox area) {
        Predicate<ScheduledTick<T>> predicate = p_193241_ -> area.isInside(p_193241_.pos());
        this.forContainersInArea(area, (p_193276_, p_193277_) -> {
            ScheduledTick<T> scheduledtick = p_193277_.peek();
            p_193277_.removeIf(predicate);
            ScheduledTick<T> scheduledtick1 = p_193277_.peek();
            if (scheduledtick1 != scheduledtick) {
                if (scheduledtick1 != null) {
                    this.updateContainerScheduling(scheduledtick1);
                } else {
                    this.nextTickForContainer.remove(p_193276_);
                }
            }
        });
        this.alreadyRunThisTick.removeIf(predicate);
        this.toRunThisTick.removeIf(predicate);
    }

    public void copyArea(BoundingBox area, Vec3i offset) {
        this.copyAreaFrom(this, area, offset);
    }

    public void copyAreaFrom(LevelTicks<T> levelTicks, BoundingBox area, Vec3i offset) {
        List<ScheduledTick<T>> list = new ArrayList<>();
        Predicate<ScheduledTick<T>> predicate = p_200922_ -> area.isInside(p_200922_.pos());
        levelTicks.alreadyRunThisTick.stream().filter(predicate).forEach(list::add);
        levelTicks.toRunThisTick.stream().filter(predicate).forEach(list::add);
        levelTicks.forContainersInArea(area, (p_200931_, p_200932_) -> p_200932_.getAll().filter(predicate).forEach(list::add));
        LongSummaryStatistics longsummarystatistics = list.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long i = longsummarystatistics.getMin();
        long j = longsummarystatistics.getMax();
        list.forEach(
            p_193260_ -> this.schedule(
                    new ScheduledTick<>(
                        p_193260_.type(),
                        p_193260_.pos().offset(offset),
                        p_193260_.triggerTick(),
                        p_193260_.priority(),
                        p_193260_.subTickOrder() - i + j + 1L
                    )
                )
        );
    }

    @Override
    public int count() {
        return this.allContainers.values().stream().mapToInt(TickAccess::count).sum();
    }

    @FunctionalInterface
    interface PosAndContainerConsumer<T> {
        void accept(long pos, LevelChunkTicks<T> container);
    }
}
