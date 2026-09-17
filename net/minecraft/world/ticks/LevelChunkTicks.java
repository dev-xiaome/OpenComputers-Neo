package net.minecraft.world.ticks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

public class LevelChunkTicks<T> implements SerializableTickContainer<T>, TickContainerAccess<T> {
    private final Queue<ScheduledTick<T>> tickQueue = new PriorityQueue<>(ScheduledTick.DRAIN_ORDER);
    @Nullable
    private List<SavedTick<T>> pendingTicks;
    private final Set<ScheduledTick<?>> ticksPerPosition = new ObjectOpenCustomHashSet<>(ScheduledTick.UNIQUE_TICK_HASH);
    @Nullable
    private BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded;

    public LevelChunkTicks() {
    }

    public LevelChunkTicks(List<SavedTick<T>> pendingTicks) {
        this.pendingTicks = pendingTicks;

        for (SavedTick<T> savedtick : pendingTicks) {
            this.ticksPerPosition.add(ScheduledTick.probe(savedtick.type(), savedtick.pos()));
        }
    }

    public void setOnTickAdded(@Nullable BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> onTickAdded) {
        this.onTickAdded = onTickAdded;
    }

    @Nullable
    public ScheduledTick<T> peek() {
        return this.tickQueue.peek();
    }

    @Nullable
    public ScheduledTick<T> poll() {
        ScheduledTick<T> scheduledtick = this.tickQueue.poll();
        if (scheduledtick != null) {
            this.ticksPerPosition.remove(scheduledtick);
        }

        return scheduledtick;
    }

    @Override
    public void schedule(ScheduledTick<T> tick) {
        if (this.ticksPerPosition.add(tick)) {
            this.scheduleUnchecked(tick);
        }
    }

    private void scheduleUnchecked(ScheduledTick<T> tick) {
        this.tickQueue.add(tick);
        if (this.onTickAdded != null) {
            this.onTickAdded.accept(this, tick);
        }
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        return this.ticksPerPosition.contains(ScheduledTick.probe(type, pos));
    }

    public void removeIf(Predicate<ScheduledTick<T>> predicate) {
        Iterator<ScheduledTick<T>> iterator = this.tickQueue.iterator();

        while (iterator.hasNext()) {
            ScheduledTick<T> scheduledtick = iterator.next();
            if (predicate.test(scheduledtick)) {
                iterator.remove();
                this.ticksPerPosition.remove(scheduledtick);
            }
        }
    }

    public Stream<ScheduledTick<T>> getAll() {
        return this.tickQueue.stream();
    }

    @Override
    public int count() {
        return this.tickQueue.size() + (this.pendingTicks != null ? this.pendingTicks.size() : 0);
    }

    public ListTag save(long gameTime, Function<T, String> idGetter) {
        ListTag listtag = new ListTag();
        if (this.pendingTicks != null) {
            for (SavedTick<T> savedtick : this.pendingTicks) {
                listtag.add(savedtick.save(idGetter));
            }
        }

        for (ScheduledTick<T> scheduledtick : this.tickQueue) {
            listtag.add(SavedTick.saveTick(scheduledtick, idGetter, gameTime));
        }

        return listtag;
    }

    public void unpack(long gameTime) {
        if (this.pendingTicks != null) {
            int i = -this.pendingTicks.size();

            for (SavedTick<T> savedtick : this.pendingTicks) {
                this.scheduleUnchecked(savedtick.unpack(gameTime, (long)(i++)));
            }
        }

        this.pendingTicks = null;
    }

    public static <T> LevelChunkTicks<T> load(ListTag tag, Function<String, Optional<T>> isParser, ChunkPos pos) {
        Builder<SavedTick<T>> builder = ImmutableList.builder();
        SavedTick.loadTickList(tag, isParser, pos, builder::add);
        return new LevelChunkTicks<>(builder.build());
    }
}
