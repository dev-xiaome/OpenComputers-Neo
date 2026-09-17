package net.minecraft.world.level.chunk.status;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

public record ChunkStep(
    ChunkStatus targetStatus, ChunkDependencies directDependencies, ChunkDependencies accumulatedDependencies, int blockStateWriteRadius, ChunkStatusTask task
) {
    public int getAccumulatedRadiusOf(ChunkStatus status) {
        return status == this.targetStatus ? 0 : this.accumulatedDependencies.getRadiusOf(status);
    }

    public CompletableFuture<ChunkAccess> apply(WorldGenContext worldGenContext, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        if (chunk.getPersistedStatus().isBefore(this.targetStatus)) {
            ProfiledDuration profiledduration = JvmProfiler.INSTANCE
                .onChunkGenerate(chunk.getPos(), worldGenContext.level().dimension(), this.targetStatus.getName());
            return this.task.doWork(worldGenContext, this, cache, chunk).thenApply(p_347625_ -> this.completeChunkGeneration(p_347625_, profiledduration));
        } else {
            return this.task.doWork(worldGenContext, this, cache, chunk);
        }
    }

    private ChunkAccess completeChunkGeneration(ChunkAccess chunk, @Nullable ProfiledDuration duration) {
        if (chunk instanceof ProtoChunk protochunk && protochunk.getPersistedStatus().isBefore(this.targetStatus)) {
            protochunk.setPersistedStatus(this.targetStatus);
        }

        if (duration != null) {
            duration.finish();
        }

        return chunk;
    }

    public static class Builder {
        private final ChunkStatus status;
        @Nullable
        private final ChunkStep parent;
        private ChunkStatus[] directDependenciesByRadius;
        private int blockStateWriteRadius = -1;
        private ChunkStatusTask task = ChunkStatusTasks::passThrough;

        protected Builder(ChunkStatus status) {
            if (status.getParent() != status) {
                throw new IllegalArgumentException("Not starting with the first status: " + status);
            } else {
                this.status = status;
                this.parent = null;
                this.directDependenciesByRadius = new ChunkStatus[0];
            }
        }

        protected Builder(ChunkStatus status, ChunkStep parent) {
            if (parent.targetStatus.getIndex() != status.getIndex() - 1) {
                throw new IllegalArgumentException("Out of order status: " + status);
            } else {
                this.status = status;
                this.parent = parent;
                this.directDependenciesByRadius = new ChunkStatus[]{parent.targetStatus};
            }
        }

        public ChunkStep.Builder addRequirement(ChunkStatus status, int radius) {
            if (status.isOrAfter(this.status)) {
                throw new IllegalArgumentException("Status " + status + " can not be required by " + this.status);
            } else {
                ChunkStatus[] achunkstatus = this.directDependenciesByRadius;
                int i = radius + 1;
                if (i > achunkstatus.length) {
                    this.directDependenciesByRadius = new ChunkStatus[i];
                    Arrays.fill(this.directDependenciesByRadius, status);
                }

                for (int j = 0; j < Math.min(i, achunkstatus.length); j++) {
                    this.directDependenciesByRadius[j] = ChunkStatus.max(achunkstatus[j], status);
                }

                return this;
            }
        }

        public ChunkStep.Builder blockStateWriteRadius(int blockStateWriteRadius) {
            this.blockStateWriteRadius = blockStateWriteRadius;
            return this;
        }

        public ChunkStep.Builder setTask(ChunkStatusTask task) {
            this.task = task;
            return this;
        }

        public ChunkStep build() {
            return new ChunkStep(
                this.status,
                new ChunkDependencies(ImmutableList.copyOf(this.directDependenciesByRadius)),
                new ChunkDependencies(ImmutableList.copyOf(this.buildAccumulatedDependencies())),
                this.blockStateWriteRadius,
                this.task
            );
        }

        private ChunkStatus[] buildAccumulatedDependencies() {
            if (this.parent == null) {
                return this.directDependenciesByRadius;
            } else {
                int i = this.getRadiusOfParent(this.parent.targetStatus);
                ChunkDependencies chunkdependencies = this.parent.accumulatedDependencies;
                ChunkStatus[] achunkstatus = new ChunkStatus[Math.max(i + chunkdependencies.size(), this.directDependenciesByRadius.length)];

                for (int j = 0; j < achunkstatus.length; j++) {
                    int k = j - i;
                    if (k < 0 || k >= chunkdependencies.size()) {
                        achunkstatus[j] = this.directDependenciesByRadius[j];
                    } else if (j >= this.directDependenciesByRadius.length) {
                        achunkstatus[j] = chunkdependencies.get(k);
                    } else {
                        achunkstatus[j] = ChunkStatus.max(this.directDependenciesByRadius[j], chunkdependencies.get(k));
                    }
                }

                return achunkstatus;
            }
        }

        private int getRadiusOfParent(ChunkStatus status) {
            for (int i = this.directDependenciesByRadius.length - 1; i >= 0; i--) {
                if (this.directDependenciesByRadius[i].isOrAfter(status)) {
                    return i;
                }
            }

            return 0;
        }
    }
}
