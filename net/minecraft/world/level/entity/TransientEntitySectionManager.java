package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class TransientEntitySectionManager<T extends EntityAccess> {
    static final Logger LOGGER = LogUtils.getLogger();
    final LevelCallback<T> callbacks;
    final EntityLookup<T> entityStorage;
    final EntitySectionStorage<T> sectionStorage;
    private final LongSet tickingChunks = new LongOpenHashSet();
    private final LevelEntityGetter<T> entityGetter;

    public TransientEntitySectionManager(Class<T> clazz, LevelCallback<T> callbacks) {
        this.entityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(
            clazz, p_157647_ -> this.tickingChunks.contains(p_157647_) ? Visibility.TICKING : Visibility.TRACKED
        );
        this.callbacks = callbacks;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.entityStorage, this.sectionStorage);
    }

    public void startTicking(ChunkPos pos) {
        long i = pos.toLong();
        this.tickingChunks.add(i);
        this.sectionStorage.getExistingSectionsInChunk(i).forEach(p_157663_ -> {
            Visibility visibility = p_157663_.updateChunkStatus(Visibility.TICKING);
            if (!visibility.isTicking()) {
                p_157663_.getEntities().filter(p_157666_ -> !p_157666_.isAlwaysTicking()).forEach(this.callbacks::onTickingStart);
            }
        });
    }

    public void stopTicking(ChunkPos pos) {
        long i = pos.toLong();
        this.tickingChunks.remove(i);
        this.sectionStorage.getExistingSectionsInChunk(i).forEach(p_157656_ -> {
            Visibility visibility = p_157656_.updateChunkStatus(Visibility.TRACKED);
            if (visibility.isTicking()) {
                p_157656_.getEntities().filter(p_157661_ -> !p_157661_.isAlwaysTicking()).forEach(this.callbacks::onTickingEnd);
            }
        });
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public void addEntity(T entity) {
        this.entityStorage.add(entity);
        long i = SectionPos.asLong(entity.blockPosition());
        EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);
        entitysection.add(entity);
        entity.setLevelCallback(new TransientEntitySectionManager.Callback(entity, i, entitysection));
        this.callbacks.onCreated(entity);
        this.callbacks.onTrackingStart(entity);
        if (entity.isAlwaysTicking() || entitysection.getStatus().isTicking()) {
            this.callbacks.onTickingStart(entity);
        }
    }

    @VisibleForDebug
    public int count() {
        return this.entityStorage.count();
    }

    void removeSectionIfEmpty(long section, EntitySection<T> entitySection) {
        if (entitySection.isEmpty()) {
            this.sectionStorage.remove(section);
        }
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.entityStorage.count() + "," + this.sectionStorage.count() + "," + this.tickingChunks.size();
    }

    class Callback implements EntityInLevelCallback {
        private final T entity;
        private final Entity realEntity;
        private long currentSectionKey;
        private EntitySection<T> currentSection;

        Callback(T entity, long section, EntitySection<T> currentSection) {
            this.entity = entity;
            this.realEntity = entity instanceof Entity ? (Entity) entity : null;
            this.currentSectionKey = section;
            this.currentSection = currentSection;
        }

        @Override
        public void onMove() {
            BlockPos blockpos = this.entity.blockPosition();
            long i = SectionPos.asLong(blockpos);
            if (i != this.currentSectionKey) {
                Visibility visibility = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    TransientEntitySectionManager.LOGGER
                        .warn("Entity {} wasn't found in section {} (moving to {})", this.entity, SectionPos.of(this.currentSectionKey), i);
                }

                TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = TransientEntitySectionManager.this.sectionStorage.getOrCreateSection(i);
                entitysection.add(this.entity);
                long oldSectionKey = currentSectionKey;
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                TransientEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                if (!this.entity.isAlwaysTicking()) {
                    boolean flag = visibility.isTicking();
                    boolean flag1 = entitysection.getStatus().isTicking();
                    if (flag && !flag1) {
                        TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
                    } else if (!flag && flag1) {
                        TransientEntitySectionManager.this.callbacks.onTickingStart(this.entity);
                    }
                }
                if (this.realEntity != null) net.neoforged.neoforge.common.CommonHooks.onEntityEnterSection(this.realEntity, oldSectionKey, i);
            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                TransientEntitySectionManager.LOGGER
                    .warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, SectionPos.of(this.currentSectionKey), reason);
            }

            Visibility visibility = this.currentSection.getStatus();
            if (visibility.isTicking() || this.entity.isAlwaysTicking()) {
                TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
            }

            TransientEntitySectionManager.this.callbacks.onTrackingEnd(this.entity);
            TransientEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            TransientEntitySectionManager.this.entityStorage.remove(this.entity);
            this.entity.setLevelCallback(NULL);
            TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }
}
