package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class EntitySection<T extends EntityAccess> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ClassInstanceMultiMap<T> storage;
    private Visibility chunkStatus;

    public EntitySection(Class<T> entityClazz, Visibility chunkStatus) {
        this.chunkStatus = chunkStatus;
        this.storage = new ClassInstanceMultiMap<>(entityClazz);
    }

    public void add(T entity) {
        this.storage.add(entity);
    }

    public boolean remove(T entity) {
        return this.storage.remove(entity);
    }

    public AbortableIterationConsumer.Continuation getEntities(AABB bounds, AbortableIterationConsumer<T> consumer) {
        for (T t : this.storage) {
            if (t.getBoundingBox().intersects(bounds) && consumer.accept(t).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }

        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    public <U extends T> AbortableIterationConsumer.Continuation getEntities(
        EntityTypeTest<T, U> test, AABB bounds, AbortableIterationConsumer<? super U> consumer
    ) {
        Collection<? extends T> collection = this.storage.find(test.getBaseClass());
        if (collection.isEmpty()) {
            return AbortableIterationConsumer.Continuation.CONTINUE;
        } else {
            for (T t : collection) {
                U u = (U)test.tryCast(t);
                if (u != null && t.getBoundingBox().intersects(bounds) && consumer.accept(u).shouldAbort()) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }

            return AbortableIterationConsumer.Continuation.CONTINUE;
        }
    }

    public boolean isEmpty() {
        return this.storage.isEmpty();
    }

    public Stream<T> getEntities() {
        return this.storage.stream();
    }

    public Visibility getStatus() {
        return this.chunkStatus;
    }

    public Visibility updateChunkStatus(Visibility chunkStatus) {
        Visibility visibility = this.chunkStatus;
        this.chunkStatus = chunkStatus;
        return visibility;
    }

    @VisibleForDebug
    public int size() {
        return this.storage.size();
    }
}
