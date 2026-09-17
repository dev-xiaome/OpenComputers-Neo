package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

public interface ChunkTrackingView {
    ChunkTrackingView EMPTY = new ChunkTrackingView() {
        @Override
        public boolean contains(int p_294225_, int p_294897_, boolean p_294644_) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> p_295201_) {
        }
    };

    static ChunkTrackingView of(ChunkPos center, int viewDistance) {
        return new ChunkTrackingView.Positioned(center, viewDistance);
    }

    /**
     * Calculates the chunks that the player needs to drop in the {@code oldChunkTrackingView} and the chunks that need to be sent for the {@code newChunkTrackingView}. The chunks that overlap in both views can be kept.
     */
    static void difference(ChunkTrackingView oldChunkTrackingView, ChunkTrackingView newChunkTrackingView, Consumer<ChunkPos> chunkMarker, Consumer<ChunkPos> chunkDropper) {
        if (!oldChunkTrackingView.equals(newChunkTrackingView)) {
            if (oldChunkTrackingView instanceof ChunkTrackingView.Positioned chunktrackingview$positioned
                && newChunkTrackingView instanceof ChunkTrackingView.Positioned chunktrackingview$positioned1
                && chunktrackingview$positioned.squareIntersects(chunktrackingview$positioned1)) {
                int i = Math.min(chunktrackingview$positioned.minX(), chunktrackingview$positioned1.minX());
                int j = Math.min(chunktrackingview$positioned.minZ(), chunktrackingview$positioned1.minZ());
                int k = Math.max(chunktrackingview$positioned.maxX(), chunktrackingview$positioned1.maxX());
                int l = Math.max(chunktrackingview$positioned.maxZ(), chunktrackingview$positioned1.maxZ());

                for (int i1 = i; i1 <= k; i1++) {
                    for (int j1 = j; j1 <= l; j1++) {
                        boolean flag = chunktrackingview$positioned.contains(i1, j1);
                        boolean flag1 = chunktrackingview$positioned1.contains(i1, j1);
                        if (flag != flag1) {
                            if (flag1) {
                                chunkMarker.accept(new ChunkPos(i1, j1));
                            } else {
                                chunkDropper.accept(new ChunkPos(i1, j1));
                            }
                        }
                    }
                }

                return;
            }

            oldChunkTrackingView.forEach(chunkDropper);
            newChunkTrackingView.forEach(chunkMarker);
        }
    }

    default boolean contains(ChunkPos chunkPos) {
        return this.contains(chunkPos.x, chunkPos.z);
    }

    default boolean contains(int x, int z) {
        return this.contains(x, z, true);
    }

    boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder);

    void forEach(Consumer<ChunkPos> action);

    default boolean isInViewDistance(int x, int z) {
        return this.contains(x, z, false);
    }

    static boolean isInViewDistance(int centerX, int centerZ, int viewDistance, int x, int z) {
        return isWithinDistance(centerX, centerZ, viewDistance, x, z, false);
    }

    /**
     * Check if a chunk {@code (x,z)} is within a {@code viewDistance} which is centered on {@code (centerX, centerZ)}
     */
    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
        int i = Math.max(0, Math.abs(x - centerX) - 1);
        int j = Math.max(0, Math.abs(z - centerZ) - 1);
        long k = (long)Math.max(0, Math.max(i, j) - (includeOuterChunksAdjacentToViewBorder ? 1 : 0));
        long l = (long)Math.min(i, j);
        long i1 = l * l + k * k;
        int j1 = viewDistance * viewDistance;
        return i1 < (long)j1;
    }

    public static record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingView {
        int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        /**
         * Determines if another {@link ChunkTrackingView}'s bounds intersects with its own
         */
        @VisibleForTesting
        protected boolean squareIntersects(ChunkTrackingView.Positioned other) {
            return this.minX() <= other.maxX() && this.maxX() >= other.minX() && this.minZ() <= other.maxZ() && this.maxZ() >= other.minZ();
        }

        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return ChunkTrackingView.isWithinDistance(this.center.x, this.center.z, this.viewDistance, x, z, includeOuterChunksAdjacentToViewBorder);
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
            for (int i = this.minX(); i <= this.maxX(); i++) {
                for (int j = this.minZ(); j <= this.maxZ(); j++) {
                    if (this.contains(i, j)) {
                        action.accept(new ChunkPos(i, j));
                    }
                }
            }
        }
    }
}
