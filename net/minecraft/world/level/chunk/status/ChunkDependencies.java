package net.minecraft.world.level.chunk.status;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.Locale;

public final class ChunkDependencies {
    private final ImmutableList<ChunkStatus> dependencyByRadius;
    private final int[] radiusByDependency;

    public ChunkDependencies(ImmutableList<ChunkStatus> dependencyByRadius) {
        this.dependencyByRadius = dependencyByRadius;
        int i = dependencyByRadius.isEmpty() ? 0 : dependencyByRadius.getFirst().getIndex() + 1;
        this.radiusByDependency = new int[i];

        for (int j = 0; j < dependencyByRadius.size(); j++) {
            ChunkStatus chunkstatus = dependencyByRadius.get(j);
            int k = chunkstatus.getIndex();

            for (int l = 0; l <= k; l++) {
                this.radiusByDependency[l] = j;
            }
        }
    }

    @VisibleForTesting
    public ImmutableList<ChunkStatus> asList() {
        return this.dependencyByRadius;
    }

    public int size() {
        return this.dependencyByRadius.size();
    }

    public int getRadiusOf(ChunkStatus status) {
        int i = status.getIndex();
        if (i >= this.radiusByDependency.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Requesting a ChunkStatus(%s) outside of dependency range(%s)", status, this.dependencyByRadius)
            );
        } else {
            return this.radiusByDependency[i];
        }
    }

    public int getRadius() {
        return Math.max(0, this.dependencyByRadius.size() - 1);
    }

    public ChunkStatus get(int radius) {
        return this.dependencyByRadius.get(radius);
    }

    @Override
    public String toString() {
        return this.dependencyByRadius.toString();
    }
}
