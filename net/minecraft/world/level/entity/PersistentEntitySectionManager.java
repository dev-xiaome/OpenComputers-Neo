package net.minecraft.world.level.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {
    static final Logger LOGGER = LogUtils.getLogger();
    final Set<UUID> knownUuids = Sets.newHashSet();
    final LevelCallback<T> callbacks;
    private final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage;
    final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    private final Long2ObjectMap<Visibility> chunkVisibility = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new Long2ObjectOpenHashMap<>();
    private final LongSet chunksToUnload = new LongOpenHashSet();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(Class<T> entityClass, LevelCallback<T> callbacks, EntityPersistentStorage<T> permanentStorage) {
        this.visibleEntityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(entityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = callbacks;
        this.permanentStorage = permanentStorage;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.visibleEntityStorage, this.sectionStorage);
    }

    void removeSectionIfEmpty(long sectionKey, EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionKey);
        }
    }

    private boolean addEntityUuid(T entity) {
        if (!this.knownUuids.add(entity.getUUID())) {
            LOGGER.warn("UUID of added entity already exists: {}", entity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(T entity) {
        return this.addEntity(entity, false);
    }

    public boolean addNewEntityWithoutEvent(T entity) {
        return this.addEntityWithoutEvent(entity, false);
    }

    private boolean addEntity(T p_entity, boolean worldGenSpawned) {
        if (p_entity instanceof Entity entity && net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(entity, entity.level(), worldGenSpawned)).isCanceled()) return false;
        return addEntityWithoutEvent(p_entity, worldGenSpawned);
    }

    private boolean addEntityWithoutEvent(T p_157539_, boolean p_157540_) {
        if (!this.addEntityUuid(p_157539_)) {
            return false;
        } else {
            long i = SectionPos.asLong(p_157539_.blockPosition());
            EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);
            entitysection.add(p_157539_);
            p_157539_.setLevelCallback(new PersistentEntitySectionManager.Callback(p_157539_, i, entitysection));
            if (!p_157540_) {
                this.callbacks.onCreated(p_157539_);
            }

            Visibility visibility = getEffectiveStatus(p_157539_, entitysection.getStatus());
            if (visibility.isAccessible()) {
                this.startTracking(p_157539_);
            }

            if (visibility.isTicking()) {
                this.startTicking(p_157539_);
            }

            return true;
        }
    }

    static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility visibility) {
        return entity.isAlwaysTicking() ? Visibility.TICKING : visibility;
    }

    public void addLegacyChunkEntities(Stream<T> entities) {
        entities.forEach(p_157607_ -> {
            this.addEntity(p_157607_, true);
            if (p_157607_ instanceof Entity entity) entity.onAddedToLevel();
        });
    }

    public void addWorldGenChunkEntities(Stream<T> entities) {
        entities.forEach(p_157605_ -> {
            this.addEntity(p_157605_, false);
            if (p_157605_ instanceof Entity entity) entity.onAddedToLevel();
        });
    }

    void startTicking(T entity) {
        this.callbacks.onTickingStart(entity);
    }

    void stopTicking(T entity) {
        this.callbacks.onTickingEnd(entity);
    }

    void startTracking(T entity) {
        this.visibleEntityStorage.add(entity);
        this.callbacks.onTrackingStart(entity);
    }

    void stopTracking(T entity) {
        this.callbacks.onTrackingEnd(entity);
        this.visibleEntityStorage.remove(entity);
    }

    public void updateChunkStatus(ChunkPos chunkPos, FullChunkStatus fullChunkStatus) {
        Visibility visibility = Visibility.fromFullChunkStatus(fullChunkStatus);
        this.updateChunkStatus(chunkPos, visibility);
    }

    public void updateChunkStatus(ChunkPos pos, Visibility p_visibility) {
        long i = pos.toLong();
        if (p_visibility == Visibility.HIDDEN) {
            this.chunkVisibility.remove(i);
            this.chunksToUnload.add(i);
        } else {
            this.chunkVisibility.put(i, p_visibility);
            this.chunksToUnload.remove(i);
            this.ensureChunkQueuedForLoad(i);
        }

        this.sectionStorage.getExistingSectionsInChunk(i).forEach(p_157545_ -> {
            Visibility visibility = p_157545_.updateChunkStatus(p_visibility);
            boolean flag = visibility.isAccessible();
            boolean flag1 = p_visibility.isAccessible();
            boolean flag2 = visibility.isTicking();
            boolean flag3 = p_visibility.isTicking();
            if (flag2 && !flag3) {
                p_157545_.getEntities().filter(p_157603_ -> !p_157603_.isAlwaysTicking()).forEach(this::stopTicking);
            }

            if (flag && !flag1) {
                p_157545_.getEntities().filter(p_157601_ -> !p_157601_.isAlwaysTicking()).forEach(this::stopTracking);
            } else if (!flag && flag1) {
                p_157545_.getEntities().filter(p_157599_ -> !p_157599_.isAlwaysTicking()).forEach(this::startTracking);
            }

            if (!flag2 && flag3) {
                p_157545_.getEntities().filter(p_157597_ -> !p_157597_.isAlwaysTicking()).forEach(this::startTicking);
            }
        });
    }

    private void ensureChunkQueuedForLoad(long chunkPosValue) {
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager$chunkloadstatus = this.chunkLoadStatuses.get(chunkPosValue);
        if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPosValue);
        }
    }

    private boolean storeChunkSections(long chunkPosValue, Consumer<T> entityAction) {
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager$chunkloadstatus = this.chunkLoadStatuses.get(chunkPosValue);
        if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        } else {
            List<T> list = this.sectionStorage
                .getExistingSectionsInChunk(chunkPosValue)
                .flatMap(p_157542_ -> p_157542_.getEntities().filter(EntityAccess::shouldBeSaved))
                .collect(Collectors.toList());
            if (list.isEmpty()) {
                if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                    this.permanentStorage.storeEntities(new ChunkEntities<>(new ChunkPos(chunkPosValue), ImmutableList.of()));
                }

                return true;
            } else if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
                this.requestChunkLoad(chunkPosValue);
                return false;
            } else {
                this.permanentStorage.storeEntities(new ChunkEntities<>(new ChunkPos(chunkPosValue), list));
                list.forEach(entityAction);
                return true;
            }
        }
    }

    private void requestChunkLoad(long chunkPosValue) {
        this.chunkLoadStatuses.put(chunkPosValue, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        ChunkPos chunkpos = new ChunkPos(chunkPosValue);
        this.permanentStorage.loadEntities(chunkpos).thenAccept(this.loadingInbox::add).exceptionally(p_157532_ -> {
            LOGGER.error("Failed to read chunk {}", chunkpos, p_157532_);
            return null;
        });
    }

    private boolean processChunkUnload(long chunkPosValue) {
        boolean flag = this.storeChunkSections(chunkPosValue, p_157595_ -> p_157595_.getPassengersAndSelf().forEach(this::unloadEntity));
        if (!flag) {
            return false;
        } else {
            this.chunkLoadStatuses.remove(chunkPosValue);
            return true;
        }
    }

    private void unloadEntity(EntityAccess entity) {
        entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        entity.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload.removeIf(p_157584_ -> this.chunkVisibility.get(p_157584_) != Visibility.HIDDEN ? true : this.processChunkUnload(p_157584_));
    }

    private void processPendingLoads() {
        ChunkEntities<T> chunkentities;
        while ((chunkentities = this.loadingInbox.poll()) != null) {
            chunkentities.getEntities().forEach(p_157593_ -> {
                this.addEntity(p_157593_, true);
                if (p_157593_ instanceof Entity entity) entity.onAddedToLevel();
            });
            this.chunkLoadStatuses.put(chunkentities.getPos().toLong(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
        }
    }

    public void tick() {
        this.processPendingLoads();
        this.processUnloads();
    }

    private LongSet getAllChunksToSave() {
        LongSet longset = this.sectionStorage.getAllChunksWithExistingSections();

        for (Entry<PersistentEntitySectionManager.ChunkLoadStatus> entry : Long2ObjectMaps.fastIterable(this.chunkLoadStatuses)) {
            if (entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                longset.add(entry.getLongKey());
            }
        }

        return longset;
    }

    public void autoSave() {
        this.getAllChunksToSave().forEach(p_157579_ -> {
            boolean flag = this.chunkVisibility.get(p_157579_) == Visibility.HIDDEN;
            if (flag) {
                this.processChunkUnload(p_157579_);
            } else {
                this.storeChunkSections(p_157579_, p_157591_ -> {
                });
            }
        });
    }

    public void saveAll() {
        LongSet longset = this.getAllChunksToSave();

        while (!longset.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            longset.removeIf(p_157574_ -> {
                boolean flag = this.chunkVisibility.get(p_157574_) == Visibility.HIDDEN;
                return flag ? this.processChunkUnload(p_157574_) : this.storeChunkSections(p_157574_, p_157589_ -> {
                });
            });
        }

        this.permanentStorage.flush(true);
    }

    @Override
    public void close() throws IOException {
        this.saveAll();
        this.permanentStorage.close();
    }

    public boolean isLoaded(UUID uuid) {
        return this.knownUuids.contains(uuid);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(BlockPos pos) {
        return this.chunkVisibility.get(ChunkPos.asLong(pos)).isTicking();
    }

    public boolean canPositionTick(ChunkPos chunkPos) {
        return this.chunkVisibility.get(chunkPos.toLong()).isTicking();
    }

    public boolean areEntitiesLoaded(long chunkPos) {
        return this.chunkLoadStatuses.get(chunkPos) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(Writer writer) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("visibility")
            .addColumn("load_status")
            .addColumn("entity_count")
            .build(writer);
        this.sectionStorage
            .getAllChunksWithExistingSections()
            .forEach(
                p_157517_ -> {
                    PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager$chunkloadstatus = this.chunkLoadStatuses.get(p_157517_);
                    this.sectionStorage
                        .getExistingSectionPositionsInChunk(p_157517_)
                        .forEach(
                            p_157521_ -> {
                                EntitySection<T> entitysection = this.sectionStorage.getSection(p_157521_);
                                if (entitysection != null) {
                                    try {
                                        csvoutput.writeRow(
                                            SectionPos.x(p_157521_),
                                            SectionPos.y(p_157521_),
                                            SectionPos.z(p_157521_),
                                            entitysection.getStatus(),
                                            persistententitysectionmanager$chunkloadstatus,
                                            entitysection.size()
                                        );
                                    } catch (IOException ioexception) {
                                        throw new UncheckedIOException(ioexception);
                                    }
                                }
                            }
                        );
                }
            );
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.knownUuids.size()
            + ","
            + this.visibleEntityStorage.count()
            + ","
            + this.sectionStorage.count()
            + ","
            + this.chunkLoadStatuses.size()
            + ","
            + this.chunkVisibility.size()
            + ","
            + this.loadingInbox.size()
            + ","
            + this.chunksToUnload.size();
    }

    @VisibleForDebug
    public int count() {
        return this.visibleEntityStorage.count();
    }

    class Callback implements EntityInLevelCallback {
        private final T entity;
        private final Entity realEntity;
        private long currentSectionKey;
        private EntitySection<T> currentSection;

        Callback(T entity, long currentSectionKey, EntitySection<T> currentSection) {
            this.entity = entity;
            this.realEntity = entity instanceof Entity ? (Entity) entity : null;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }

        @Override
        public void onMove() {
            BlockPos blockpos = this.entity.blockPosition();
            long i = SectionPos.asLong(blockpos);
            if (i != this.currentSectionKey) {
                Visibility visibility = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    PersistentEntitySectionManager.LOGGER
                        .warn("Entity {} wasn't found in section {} (moving to {})", this.entity, SectionPos.of(this.currentSectionKey), i);
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(i);
                entitysection.add(this.entity);
                long oldSectionKey = currentSectionKey;
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                this.updateStatus(visibility, entitysection.getStatus());
                if (this.realEntity != null) net.neoforged.neoforge.common.CommonHooks.onEntityEnterSection(this.realEntity, oldSectionKey, i);
            }
        }

        private void updateStatus(Visibility oldVisibility, Visibility newVisibility) {
            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(this.entity, oldVisibility);
            Visibility visibility1 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, newVisibility);
            if (visibility == visibility1) {
                if (visibility1.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            } else {
                boolean flag = visibility.isAccessible();
                boolean flag1 = visibility1.isAccessible();
                if (flag && !flag1) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!flag && flag1) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean flag2 = visibility.isTicking();
                boolean flag3 = visibility1.isTicking();
                if (flag2 && !flag3) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!flag2 && flag3) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (flag1) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            }
        }

        @Override
        public void onRemove(Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER
                    .warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, SectionPos.of(this.currentSectionKey), reason);
            }

            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());
            if (visibility.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (visibility.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (reason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }

    static enum ChunkLoadStatus {
        FRESH,
        PENDING,
        LOADED;
    }
}
