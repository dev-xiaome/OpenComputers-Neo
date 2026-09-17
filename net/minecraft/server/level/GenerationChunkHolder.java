package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public abstract class GenerationChunkHolder {
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private static final ChunkResult<ChunkAccess> NOT_DONE_YET = ChunkResult.error("Not done yet");
    public static final ChunkResult<ChunkAccess> UNLOADED_CHUNK = ChunkResult.error("Unloaded chunk");
    public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_CHUNK);
    protected final ChunkPos pos;
    @Nullable
    private volatile ChunkStatus highestAllowedStatus;
    private final AtomicReference<ChunkStatus> startedWork = new AtomicReference<>();
    private final AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new AtomicReferenceArray<>(CHUNK_STATUSES.size());
    private final AtomicReference<ChunkGenerationTask> task = new AtomicReference<>();
    private final AtomicInteger generationRefCount = new AtomicInteger();
    public net.minecraft.world.level.chunk.LevelChunk currentlyLoading; // Forge: Used to bypass future chain when loading chunks.

    public GenerationChunkHolder(ChunkPos pos) {
        this.pos = pos;
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus targetStatus, ChunkMap chunkMap) {
        if (this.isStatusDisallowed(targetStatus)) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.getOrCreateFuture(targetStatus);
            if (completablefuture.isDone()) {
                return completablefuture;
            } else {
                ChunkGenerationTask chunkgenerationtask = this.task.get();
                if (chunkgenerationtask == null || targetStatus.isAfter(chunkgenerationtask.targetStatus)) {
                    this.rescheduleChunkTask(chunkMap, targetStatus);
                }

                return completablefuture;
            }
        }
    }

    CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep step, GeneratingChunkMap chunkMap, StaticCache2D<GenerationChunkHolder> cache) {
        if (this.isStatusDisallowed(step.targetStatus())) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            return this.acquireStatusBump(step.targetStatus()) ? chunkMap.applyStep(this, step, cache).handle((p_347506_, p_347622_) -> {
                if (p_347622_ != null) {
                    CrashReport crashreport = CrashReport.forThrowable(p_347622_, "Exception chunk generation/loading");
                    MinecraftServer.setFatalException(new ReportedException(crashreport));
                } else {
                    this.completeFuture(step.targetStatus(), p_347506_);
                }

                return ChunkResult.of(p_347506_);
            }) : this.getOrCreateFuture(step.targetStatus());
        }
    }

    protected void updateHighestAllowedStatus(ChunkMap chunkMap) {
        ChunkStatus chunkstatus = this.highestAllowedStatus;
        ChunkStatus chunkstatus1 = ChunkLevel.generationStatus(this.getTicketLevel());
        this.highestAllowedStatus = chunkstatus1;
        boolean flag = chunkstatus != null && (chunkstatus1 == null || chunkstatus1.isBefore(chunkstatus));
        if (flag) {
            this.failAndClearPendingFuturesBetween(chunkstatus1, chunkstatus);
            if (this.task.get() != null) {
                this.rescheduleChunkTask(chunkMap, this.findHighestStatusWithPendingFuture(chunkstatus1));
            }
        }
    }

    public void replaceProtoChunk(ImposterProtoChunk chunk) {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = CompletableFuture.completedFuture(ChunkResult.of(chunk));

        for (int i = 0; i < this.futures.length() - 1; i++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture1 = this.futures.get(i);
            Objects.requireNonNull(completablefuture1);
            ChunkAccess chunkaccess = completablefuture1.getNow(NOT_DONE_YET).orElse(null);
            if (!(chunkaccess instanceof ProtoChunk)) {
                throw new IllegalStateException("Trying to replace a ProtoChunk, but found " + chunkaccess);
            }

            if (!this.futures.compareAndSet(i, completablefuture1, completablefuture)) {
                throw new IllegalStateException("Future changed by other thread while trying to replace it");
            }
        }
    }

    void removeTask(ChunkGenerationTask task) {
        this.task.compareAndSet(task, null);
    }

    private void rescheduleChunkTask(ChunkMap chunkMap, @Nullable ChunkStatus targetStatus) {
        ChunkGenerationTask chunkgenerationtask;
        if (targetStatus != null) {
            chunkgenerationtask = chunkMap.scheduleGenerationTask(targetStatus, this.getPos());
        } else {
            chunkgenerationtask = null;
        }

        ChunkGenerationTask chunkgenerationtask1 = this.task.getAndSet(chunkgenerationtask);
        if (chunkgenerationtask1 != null) {
            chunkgenerationtask1.markForCancellation();
        }
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus targetStatus) {
        if (this.isStatusDisallowed(targetStatus)) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            int i = targetStatus.getIndex();
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(i);

            while (completablefuture == null) {
                CompletableFuture<ChunkResult<ChunkAccess>> completablefuture1 = new CompletableFuture<>();
                completablefuture = this.futures.compareAndExchange(i, null, completablefuture1);
                if (completablefuture == null) {
                    if (this.isStatusDisallowed(targetStatus)) {
                        this.failAndClearPendingFuture(i, completablefuture1);
                        return UNLOADED_CHUNK_FUTURE;
                    }

                    return completablefuture1;
                }
            }

            return completablefuture;
        }
    }

    private void failAndClearPendingFuturesBetween(@Nullable ChunkStatus highestAllowableStatus, ChunkStatus currentStatus) {
        int i = highestAllowableStatus == null ? 0 : highestAllowableStatus.getIndex() + 1;
        int j = currentStatus.getIndex();

        for (int k = i; k <= j; k++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(k);
            if (completablefuture != null) {
                this.failAndClearPendingFuture(k, completablefuture);
            }
        }
    }

    private void failAndClearPendingFuture(int status, CompletableFuture<ChunkResult<ChunkAccess>> future) {
        if (future.complete(UNLOADED_CHUNK) && !this.futures.compareAndSet(status, future, null)) {
            throw new IllegalStateException("Nothing else should replace the future here");
        }
    }

    private void completeFuture(ChunkStatus targetStatus, ChunkAccess chunkAccess) {
        ChunkResult<ChunkAccess> chunkresult = ChunkResult.of(chunkAccess);
        int i = targetStatus.getIndex();

        while (true) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(i);
            if (completablefuture == null) {
                if (this.futures.compareAndSet(i, null, CompletableFuture.completedFuture(chunkresult))) {
                    return;
                }
            } else {
                if (completablefuture.complete(chunkresult)) {
                    return;
                }

                if (completablefuture.getNow(NOT_DONE_YET).isSuccess()) {
                    throw new IllegalStateException("Trying to complete a future but found it to be completed successfully already");
                }

                Thread.yield();
            }
        }
    }

    @Nullable
    private ChunkStatus findHighestStatusWithPendingFuture(@Nullable ChunkStatus generationStatus) {
        if (generationStatus == null) {
            return null;
        } else {
            ChunkStatus chunkstatus = generationStatus;

            for (ChunkStatus chunkstatus1 = this.startedWork.get();
                chunkstatus1 == null || chunkstatus.isAfter(chunkstatus1);
                chunkstatus = chunkstatus.getParent()
            ) {
                if (this.futures.get(chunkstatus.getIndex()) != null) {
                    return chunkstatus;
                }

                if (chunkstatus == ChunkStatus.EMPTY) {
                    break;
                }
            }

            return null;
        }
    }

    private boolean acquireStatusBump(ChunkStatus status) {
        ChunkStatus chunkstatus = status == ChunkStatus.EMPTY ? null : status.getParent();
        ChunkStatus chunkstatus1 = this.startedWork.compareAndExchange(chunkstatus, status);
        if (chunkstatus1 == chunkstatus) {
            return true;
        } else if (chunkstatus1 != null && !status.isAfter(chunkstatus1)) {
            return false;
        } else {
            throw new IllegalStateException("Unexpected last startedWork status: " + chunkstatus1 + " while trying to start: " + status);
        }
    }

    private boolean isStatusDisallowed(ChunkStatus status) {
        ChunkStatus chunkstatus = this.highestAllowedStatus;
        return chunkstatus == null || status.isAfter(chunkstatus);
    }

    public void increaseGenerationRefCount() {
        this.generationRefCount.incrementAndGet();
    }

    public void decreaseGenerationRefCount() {
        int i = this.generationRefCount.decrementAndGet();
        if (i < 0) {
            throw new IllegalStateException("More releases than claims. Count: " + i);
        }
    }

    public int getGenerationRefCount() {
        return this.generationRefCount.get();
    }

    @Nullable
    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(status.getIndex());
        return completablefuture == null ? null : completablefuture.getNow(NOT_DONE_YET).orElse(null);
    }

    @Nullable
    public ChunkAccess getChunkIfPresent(ChunkStatus status) {
        return this.isStatusDisallowed(status) ? null : this.getChunkIfPresentUnchecked(status);
    }

    @Nullable
    public ChunkAccess getLatestChunk() {
        ChunkStatus chunkstatus = this.startedWork.get();
        if (chunkstatus == null) {
            return null;
        } else {
            ChunkAccess chunkaccess = this.getChunkIfPresentUnchecked(chunkstatus);
            return chunkaccess != null ? chunkaccess : this.getChunkIfPresentUnchecked(chunkstatus.getParent());
        }
    }

    @Nullable
    public ChunkStatus getPersistedStatus() {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(ChunkStatus.EMPTY.getIndex());
        ChunkAccess chunkaccess = completablefuture == null ? null : completablefuture.getNow(NOT_DONE_YET).orElse(null);
        return chunkaccess == null ? null : chunkaccess.getPersistedStatus();
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public FullChunkStatus getFullStatus() {
        return ChunkLevel.fullStatus(this.getTicketLevel());
    }

    public abstract int getTicketLevel();

    public abstract int getQueueLevel();

    @VisibleForDebug
    public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> list = new ArrayList<>();

        for (int i = 0; i < CHUNK_STATUSES.size(); i++) {
            list.add(Pair.of(CHUNK_STATUSES.get(i), this.futures.get(i)));
        }

        return list;
    }

    @Nullable
    @VisibleForDebug
    public ChunkStatus getLatestStatus() {
        for (int i = CHUNK_STATUSES.size() - 1; i >= 0; i--) {
            ChunkStatus chunkstatus = CHUNK_STATUSES.get(i);
            ChunkAccess chunkaccess = this.getChunkIfPresentUnchecked(chunkstatus);
            if (chunkaccess != null) {
                return chunkstatus;
            }
        }

        return null;
    }
}
