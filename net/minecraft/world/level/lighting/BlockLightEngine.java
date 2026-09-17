package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class BlockLightEngine extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public BlockLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new BlockLightSectionStorage(chunkSource));
    }

    @VisibleForTesting
    public BlockLightEngine(LightChunkGetter chunkSource, BlockLightSectionStorage storage) {
        super(chunkSource, storage);
    }

    @Override
    protected void checkNode(long packedPos) {
        long i = SectionPos.blockToSection(packedPos);
        if (this.storage.storingLightForSection(i)) {
            BlockState blockstate = this.getState(this.mutablePos.set(packedPos));
            int j = this.getEmission(packedPos, blockstate);
            int k = this.storage.getStoredLevel(packedPos);
            if (j < k) {
                this.storage.setStoredLevel(packedPos, 0);
                this.enqueueDecrease(packedPos, LightEngine.QueueEntry.decreaseAllDirections(k));
            } else {
                this.enqueueDecrease(packedPos, PULL_LIGHT_IN_ENTRY);
            }

            if (j > 0) {
                this.enqueueIncrease(packedPos, LightEngine.QueueEntry.increaseLightFromEmission(j, isEmptyShape(blockstate)));
            }
        }
    }

    @Override
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockstate = null;

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                long i = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(i))) {
                    int j = this.storage.getStoredLevel(i);
                    int k = lightLevel - 1;
                    if (k > j) {
                        this.mutablePos.set(i);
                        BlockState blockstate1 = this.getState(this.mutablePos);
                        int l = lightLevel - this.getOpacity(blockstate1, this.mutablePos);
                        if (l > j) {
                            if (blockstate == null) {
                                blockstate = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(packedPos));
                            }

                            if (!this.shapeOccludes(packedPos, blockstate, i, blockstate1, direction)) {
                                this.storage.setStoredLevel(i, l);
                                if (l > 1) {
                                    this.enqueueIncrease(
                                        i, LightEngine.QueueEntry.increaseSkipOneDirection(l, isEmptyShape(blockstate1), direction.getOpposite())
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long packedPos, long lightLevel) {
        int i = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                long j = BlockPos.offset(packedPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(j))) {
                    int k = this.storage.getStoredLevel(j);
                    if (k != 0) {
                        if (k <= i - 1) {
                            BlockState blockstate = this.getState(this.mutablePos.set(j));
                            int l = this.getEmission(j, blockstate);
                            this.storage.setStoredLevel(j, 0);
                            if (l < k) {
                                this.enqueueDecrease(j, LightEngine.QueueEntry.decreaseSkipOneDirection(k, direction.getOpposite()));
                            }

                            if (l > 0) {
                                this.enqueueIncrease(j, LightEngine.QueueEntry.increaseLightFromEmission(l, isEmptyShape(blockstate)));
                            }
                        } else {
                            this.enqueueIncrease(j, LightEngine.QueueEntry.increaseOnlyOneDirection(k, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private int getEmission(long packedPos, BlockState state) {
        int i = state.getLightEmission(chunkSource.getLevel(), mutablePos);
        return i > 0 && this.storage.lightOnInSection(SectionPos.blockToSection(packedPos)) ? i : 0;
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        this.setLightEnabled(chunkPos, true);
        LightChunk lightchunk = this.chunkSource.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (lightchunk != null) {
            lightchunk.findBlockLightSources((p_285266_, p_285452_) -> {
                int i = p_285452_.getLightEmission(chunkSource.getLevel(), p_285266_);
                this.enqueueIncrease(p_285266_.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(p_285452_)));
            });
        }
    }
}
