package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.phys.AABB;

public class LevelEntityGetterAdapter<T extends EntityAccess> implements LevelEntityGetter<T> {
    private final EntityLookup<T> visibleEntities;
    private final EntitySectionStorage<T> sectionStorage;

    public LevelEntityGetterAdapter(EntityLookup<T> visibleEntities, EntitySectionStorage<T> sectionStorage) {
        this.visibleEntities = visibleEntities;
        this.sectionStorage = sectionStorage;
    }

    @Nullable
    @Override
    public T get(int id) {
        return this.visibleEntities.getEntity(id);
    }

    @Nullable
    @Override
    public T get(UUID uuid) {
        return this.visibleEntities.getEntity(uuid);
    }

    @Override
    public Iterable<T> getAll() {
        return this.visibleEntities.getAllEntities();
    }

    @Override
    public <U extends T> void get(EntityTypeTest<T, U> test, AbortableIterationConsumer<U> consumer) {
        this.visibleEntities.getEntities(test, consumer);
    }

    @Override
    public void get(AABB boundingBox, Consumer<T> consumer) {
        this.sectionStorage.getEntities(boundingBox, AbortableIterationConsumer.forConsumer(consumer));
    }

    @Override
    public <U extends T> void get(EntityTypeTest<T, U> test, AABB bounds, AbortableIterationConsumer<U> consumer) {
        this.sectionStorage.getEntities(test, bounds, consumer);
    }
}
