package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public abstract class DistanceManager {
    static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    final Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk = new Long2ObjectOpenHashMap<>();
    final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets = new Long2ObjectOpenHashMap<>();
    private final DistanceManager.ChunkTicketTracker ticketTracker = new DistanceManager.ChunkTicketTracker();
    private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);
    private final TickingTracker tickingTicketsTracker = new TickingTracker();
    private final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(32);
    final Set<ChunkHolder> chunksToUpdateFutures = Sets.newHashSet();
    final ChunkTaskPriorityQueueSorter ticketThrottler;
    final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> ticketThrottlerInput;
    final ProcessorHandle<ChunkTaskPriorityQueueSorter.Release> ticketThrottlerReleaser;
    final LongSet ticketsToRelease = new LongOpenHashSet();
    final Executor mainThreadExecutor;
    private long ticketTickCounter;
    private int simulationDistance = 10;

    private final Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> forcedTickets = new Long2ObjectOpenHashMap<>();

    protected DistanceManager(Executor dispatcher, Executor mainThreadExecutor) {
        ProcessorHandle<Runnable> processorhandle = ProcessorHandle.of("player ticket throttler", mainThreadExecutor::execute);
        ChunkTaskPriorityQueueSorter chunktaskpriorityqueuesorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(processorhandle), dispatcher, 4);
        this.ticketThrottler = chunktaskpriorityqueuesorter;
        this.ticketThrottlerInput = chunktaskpriorityqueuesorter.getProcessor(processorhandle, true);
        this.ticketThrottlerReleaser = chunktaskpriorityqueuesorter.getReleaseProcessor(processorhandle);
        this.mainThreadExecutor = mainThreadExecutor;
    }

    protected void purgeStaleTickets() {
        this.ticketTickCounter++;
        ObjectIterator<Entry<SortedArraySet<Ticket<?>>>> objectiterator = this.tickets.long2ObjectEntrySet().fastIterator();

        while (objectiterator.hasNext()) {
            Entry<SortedArraySet<Ticket<?>>> entry = objectiterator.next();
            Iterator<Ticket<?>> iterator = entry.getValue().iterator();
            boolean flag = false;

            while (iterator.hasNext()) {
                Ticket<?> ticket = iterator.next();
                if (ticket.timedOut(this.ticketTickCounter)) {
                    iterator.remove();
                    flag = true;
                    this.tickingTicketsTracker.removeTicket(entry.getLongKey(), ticket);
                }
            }

            if (flag) {
                this.ticketTracker.update(entry.getLongKey(), getTicketLevelAt(entry.getValue()), false);
            }

            if (entry.getValue().isEmpty()) {
                objectiterator.remove();
            }
        }
    }

    /**
     * Gets the {@linkplain net.minecraft.server.level.Ticket#getTicketLevel level} of the ticket.
     */
    private static int getTicketLevelAt(SortedArraySet<Ticket<?>> tickets) {
        return !tickets.isEmpty() ? tickets.first().getTicketLevel() : ChunkLevel.MAX_LEVEL + 1;
    }

    protected abstract boolean isChunkToRemove(long chunkPos);

    @Nullable
    protected abstract ChunkHolder getChunk(long chunkPos);

    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(long chunkPos, int newLevel, @Nullable ChunkHolder holder, int oldLevel);

    public boolean runAllUpdates(ChunkMap chunkMap) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        this.tickingTicketsTracker.runAllUpdates();
        this.playerTicketManager.runAllUpdates();
        int i = Integer.MAX_VALUE - this.ticketTracker.runDistanceUpdates(Integer.MAX_VALUE);
        boolean flag = i != 0;
        if (flag) {
        }

        if (!this.chunksToUpdateFutures.isEmpty()) {
            this.chunksToUpdateFutures.forEach(p_347062_ -> p_347062_.updateHighestAllowedStatus(chunkMap));
            this.chunksToUpdateFutures.forEach(p_183908_ -> p_183908_.updateFutures(chunkMap, this.mainThreadExecutor));
            this.chunksToUpdateFutures.clear();
            return true;
        } else {
            if (!this.ticketsToRelease.isEmpty()) {
                LongIterator longiterator = this.ticketsToRelease.iterator();

                while (longiterator.hasNext()) {
                    long j = longiterator.nextLong();
                    if (this.getTickets(j).stream().anyMatch(p_183910_ -> p_183910_.getType() == TicketType.PLAYER)) {
                        ChunkHolder chunkholder = chunkMap.getUpdatingChunkIfPresent(j);
                        if (chunkholder == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<ChunkResult<LevelChunk>> completablefuture = chunkholder.getEntityTickingChunkFuture();
                        completablefuture.thenAccept(
                            p_331640_ -> this.mainThreadExecutor.execute(() -> this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                                    }, j, false)))
                        );
                    }
                }

                this.ticketsToRelease.clear();
            }

            return flag;
        }
    }

    void addTicket(long chunkPos, Ticket<?> p_ticket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(chunkPos);
        int i = getTicketLevelAt(sortedarrayset);
        Ticket<?> ticket = sortedarrayset.addOrGet(p_ticket);
        ticket.setCreatedTick(this.ticketTickCounter);
        if (p_ticket.getTicketLevel() < i) {
            this.ticketTracker.update(chunkPos, p_ticket.getTicketLevel(), true);
        }
        if (p_ticket.isForceTicks()) {
             SortedArraySet<Ticket<?>> tickets = forcedTickets.computeIfAbsent(chunkPos, e -> SortedArraySet.create(4));
             tickets.addOrGet(ticket);
        }
    }

    void removeTicket(long chunkPos, Ticket<?> ticket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(chunkPos);
        if (sortedarrayset.remove(ticket)) {
        }

        if (sortedarrayset.isEmpty()) {
            this.tickets.remove(chunkPos);
        }

        this.ticketTracker.update(chunkPos, getTicketLevelAt(sortedarrayset), false);

        if (ticket.isForceTicks()) {
             SortedArraySet<Ticket<?>> tickets = forcedTickets.get(chunkPos);
             if (tickets != null) {
                  tickets.remove(ticket);
             }
        }
    }

    public <T> void addTicket(TicketType<T> type, ChunkPos pos, int level, T value) {
        this.addTicket(pos.toLong(), new Ticket<>(type, level, value));
    }

    public <T> void removeTicket(TicketType<T> type, ChunkPos pos, int level, T value) {
        Ticket<T> ticket = new Ticket<>(type, level, value);
        this.removeTicket(pos.toLong(), ticket);
    }

    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        addRegionTicket(type, pos, distance, value, false);
    }
    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks) {
        Ticket<T> ticket = new Ticket<>(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - distance, value, forceTicks);
        long i = pos.toLong();
        this.addTicket(i, ticket);
        this.tickingTicketsTracker.addTicket(i, ticket);
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value) {
        removeRegionTicket(type, pos, distance, value, false);
    }
    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int distance, T value, boolean forceTicks) {
        Ticket<T> ticket = new Ticket<>(type, ChunkLevel.byStatus(FullChunkStatus.FULL) - distance, value, forceTicks);
        long i = pos.toLong();
        this.removeTicket(i, ticket);
        this.tickingTicketsTracker.removeTicket(i, ticket);
    }

    private SortedArraySet<Ticket<?>> getTickets(long chunkPos) {
        return this.tickets.computeIfAbsent(chunkPos, p_183923_ -> SortedArraySet.create(4));
    }

    protected void updateChunkForced(ChunkPos pos, boolean add) {
        Ticket<ChunkPos> ticket = new Ticket<>(TicketType.FORCED, ChunkMap.FORCED_TICKET_LEVEL, pos);
        long i = pos.toLong();
        if (add) {
            this.addTicket(i, ticket);
            this.tickingTicketsTracker.addTicket(i, ticket);
        } else {
            this.removeTicket(i, ticket);
            this.tickingTicketsTracker.removeTicket(i, ticket);
        }
    }

    public void addPlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkpos = sectionPos.chunk();
        long i = chunkpos.toLong();
        this.playersPerChunk.computeIfAbsent(i, p_183921_ -> new ObjectOpenHashSet<>()).add(player);
        this.naturalSpawnChunkCounter.update(i, 0, true);
        this.playerTicketManager.update(i, 0, true);
        this.tickingTicketsTracker.addTicket(TicketType.PLAYER, chunkpos, this.getPlayerTicketLevel(), chunkpos);
    }

    public void removePlayer(SectionPos sectionPos, ServerPlayer player) {
        ChunkPos chunkpos = sectionPos.chunk();
        long i = chunkpos.toLong();
        ObjectSet<ServerPlayer> objectset = this.playersPerChunk.get(i);
        objectset.remove(player);
        if (objectset.isEmpty()) {
            this.playersPerChunk.remove(i);
            this.naturalSpawnChunkCounter.update(i, Integer.MAX_VALUE, false);
            this.playerTicketManager.update(i, Integer.MAX_VALUE, false);
            this.tickingTicketsTracker.removeTicket(TicketType.PLAYER, chunkpos, this.getPlayerTicketLevel(), chunkpos);
        }
    }

    private int getPlayerTicketLevel() {
        return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - this.simulationDistance);
    }

    public boolean inEntityTickingRange(long chunkPos) {
        return ChunkLevel.isEntityTicking(this.tickingTicketsTracker.getLevel(chunkPos));
    }

    public boolean inBlockTickingRange(long chunkPos) {
        return ChunkLevel.isBlockTicking(this.tickingTicketsTracker.getLevel(chunkPos));
    }

    protected String getTicketDebugString(long chunkPos) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(chunkPos);
        return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().toString() : "no_ticket";
    }

    protected void updatePlayerTickets(int viewDistance) {
        this.playerTicketManager.updateViewDistance(viewDistance);
    }

    public void updateSimulationDistance(int simulationDistance) {
        if (simulationDistance != this.simulationDistance) {
            this.simulationDistance = simulationDistance;
            this.tickingTicketsTracker.replacePlayerTicketsLevel(this.getPlayerTicketLevel());
        }
    }

    public int getNaturalSpawnChunkCount() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.size();
    }

    public boolean hasPlayersNearby(long chunkPos) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.containsKey(chunkPos);
    }

    public String getDebugStatus() {
        return this.ticketThrottler.getDebugStatus();
    }

    public boolean shouldForceTicks(long chunkPos) {
         SortedArraySet<Ticket<?>> tickets = forcedTickets.get(chunkPos);
         return tickets != null && !tickets.isEmpty();
    }

    private void dumpTickets(String filename) {
        try (FileOutputStream fileoutputstream = new FileOutputStream(new File(filename))) {
            for (Entry<SortedArraySet<Ticket<?>>> entry : this.tickets.long2ObjectEntrySet()) {
                ChunkPos chunkpos = new ChunkPos(entry.getLongKey());

                for (Ticket<?> ticket : entry.getValue()) {
                    fileoutputstream.write(
                        (chunkpos.x + "\t" + chunkpos.z + "\t" + ticket.getType() + "\t" + ticket.getTicketLevel() + "\t\n").getBytes(StandardCharsets.UTF_8)
                    );
                }
            }
        } catch (IOException ioexception) {
            LOGGER.error("Failed to dump tickets to {}", filename, ioexception);
        }
    }

    @VisibleForTesting
    TickingTracker tickingTracker() {
        return this.tickingTicketsTracker;
    }

    public void removeTicketsOnClosing() {
        ImmutableSet<TicketType<?>> immutableset = ImmutableSet.of(TicketType.UNKNOWN, TicketType.POST_TELEPORT);
        ObjectIterator<Entry<SortedArraySet<Ticket<?>>>> objectiterator = this.tickets.long2ObjectEntrySet().fastIterator();

        while (objectiterator.hasNext()) {
            Entry<SortedArraySet<Ticket<?>>> entry = objectiterator.next();
            Iterator<Ticket<?>> iterator = entry.getValue().iterator();
            boolean flag = false;

            while (iterator.hasNext()) {
                Ticket<?> ticket = iterator.next();
                if (!immutableset.contains(ticket.getType())) {
                    iterator.remove();
                    flag = true;
                    this.tickingTicketsTracker.removeTicket(entry.getLongKey(), ticket);
                }
            }

            if (flag) {
                this.ticketTracker.update(entry.getLongKey(), getTicketLevelAt(entry.getValue()), false);
            }

            if (entry.getValue().isEmpty()) {
                objectiterator.remove();
            }
        }
    }

    public boolean hasTickets() {
        return !this.tickets.isEmpty();
    }

    class ChunkTicketTracker extends ChunkTracker {
        private static final int MAX_LEVEL = ChunkLevel.MAX_LEVEL + 1;

        public ChunkTicketTracker() {
            super(MAX_LEVEL + 1, 16, 256);
        }

        @Override
        protected int getLevelFromSource(long pos) {
            SortedArraySet<Ticket<?>> sortedarrayset = DistanceManager.this.tickets.get(pos);
            if (sortedarrayset == null) {
                return Integer.MAX_VALUE;
            } else {
                return sortedarrayset.isEmpty() ? Integer.MAX_VALUE : sortedarrayset.first().getTicketLevel();
            }
        }

        @Override
        protected int getLevel(long sectionPos) {
            if (!DistanceManager.this.isChunkToRemove(sectionPos)) {
                ChunkHolder chunkholder = DistanceManager.this.getChunk(sectionPos);
                if (chunkholder != null) {
                    return chunkholder.getTicketLevel();
                }
            }

            return MAX_LEVEL;
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            ChunkHolder chunkholder = DistanceManager.this.getChunk(sectionPos);
            int i = chunkholder == null ? MAX_LEVEL : chunkholder.getTicketLevel();
            if (i != level) {
                chunkholder = DistanceManager.this.updateChunkScheduling(sectionPos, level, chunkholder, i);
                if (chunkholder != null) {
                    DistanceManager.this.chunksToUpdateFutures.add(chunkholder);
                }
            }
        }

        public int runDistanceUpdates(int toUpdateCount) {
            return this.runUpdates(toUpdateCount);
        }
    }

    class FixedPlayerDistanceChunkTracker extends ChunkTracker {
        /**
         * Chunks that are at most {@link #range} chunks away from the closest player.
         */
        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(int maxDistance) {
            super(maxDistance + 2, 16, 256);
            this.maxDistance = maxDistance;
            this.chunks.defaultReturnValue((byte)(maxDistance + 2));
        }

        @Override
        protected int getLevel(long sectionPos) {
            return this.chunks.get(sectionPos);
        }

        @Override
        protected void setLevel(long sectionPos, int level) {
            byte b0;
            if (level > this.maxDistance) {
                b0 = this.chunks.remove(sectionPos);
            } else {
                b0 = this.chunks.put(sectionPos, (byte)level);
            }

            this.onLevelChange(sectionPos, b0, level);
        }

        /**
         * Called after {@link PlayerChunkTracker#setLevel(long, int)} puts/removes chunk into/from {@link #chunksInRange}.
         *
         * @param oldLevel Previous level of the chunk if it was smaller than {@link #
         *                 range}, {@code range + 2} otherwise.
         */
        protected void onLevelChange(long chunkPos, int oldLevel, int newLevel) {
        }

        @Override
        protected int getLevelFromSource(long pos) {
            return this.havePlayer(pos) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long chunkPos) {
            ObjectSet<ServerPlayer> objectset = DistanceManager.this.playersPerChunk.get(chunkPos);
            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }

        private void dumpChunks(String filename) {
            try (FileOutputStream fileoutputstream = new FileOutputStream(new File(filename))) {
                for (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry entry : this.chunks.long2ByteEntrySet()) {
                    ChunkPos chunkpos = new ChunkPos(entry.getLongKey());
                    String s = Byte.toString(entry.getByteValue());
                    fileoutputstream.write((chunkpos.x + "\t" + chunkpos.z + "\t" + s + "\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ioexception) {
                DistanceManager.LOGGER.error("Failed to dump chunks to {}", filename, ioexception);
            }
        }
    }

    class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {
        private int viewDistance;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected PlayerTicketTracker(int maxDistance) {
            super(maxDistance);
            this.viewDistance = 0;
            this.queueLevels.defaultReturnValue(maxDistance + 2);
        }

        /**
         * Called after {@link PlayerChunkTracker#setLevel(long, int)} puts/removes chunk into/from {@link #chunksInRange}.
         *
         * @param oldLevel Previous level of the chunk if it was smaller than {@link #
         *                 range}, {@code range + 2} otherwise.
         */
        @Override
        protected void onLevelChange(long chunkPos, int oldLevel, int newLevel) {
            this.toUpdate.add(chunkPos);
        }

        public void updateViewDistance(int viewDistance) {
            for (it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry entry : this.chunks.long2ByteEntrySet()) {
                byte b0 = entry.getByteValue();
                long i = entry.getLongKey();
                this.onLevelChange(i, b0, this.haveTicketFor(b0), b0 <= viewDistance);
            }

            this.viewDistance = viewDistance;
        }

        private void onLevelChange(long chunkPos, int level, boolean hadTicket, boolean hasTicket) {
            if (hadTicket != hasTicket) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, DistanceManager.PLAYER_TICKET_LEVEL, new ChunkPos(chunkPos));
                if (hasTicket) {
                    DistanceManager.this.ticketThrottlerInput
                        .tell(ChunkTaskPriorityQueueSorter.message(() -> DistanceManager.this.mainThreadExecutor.execute(() -> {
                                if (this.haveTicketFor(this.getLevel(chunkPos))) {
                                    DistanceManager.this.addTicket(chunkPos, ticket);
                                    DistanceManager.this.ticketsToRelease.add(chunkPos);
                                } else {
                                    DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                                    }, chunkPos, false));
                                }
                            }), chunkPos, () -> level));
                } else {
                    DistanceManager.this.ticketThrottlerReleaser
                        .tell(
                            ChunkTaskPriorityQueueSorter.release(
                                () -> DistanceManager.this.mainThreadExecutor.execute(() -> DistanceManager.this.removeTicket(chunkPos, ticket)),
                                chunkPos,
                                true
                            )
                        );
                }
            }
        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.queueLevels.get(i);
                    int k = this.getLevel(i);
                    if (j != k) {
                        DistanceManager.this.ticketThrottler.onLevelChange(new ChunkPos(i), () -> this.queueLevels.get(i), k, p_140928_ -> {
                            if (p_140928_ >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(i);
                            } else {
                                this.queueLevels.put(i, p_140928_);
                            }
                        });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }
        }

        private boolean haveTicketFor(int level) {
            return level <= this.viewDistance;
        }
    }
}
