package net.minecraft.world.level.entity;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.util.AbortableIterationConsumer;
import org.slf4j.Logger;

public class EntityLookup<T extends EntityAccess> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Int2ObjectMap<T> byId = new Int2ObjectLinkedOpenHashMap<>();
    private final Map<UUID, T> byUuid = Maps.newHashMap();

    public <U extends T> void getEntities(EntityTypeTest<T, U> test, AbortableIterationConsumer<U> consumer) {
        for (T t : this.byId.values()) {
            U u = (U)test.tryCast(t);
            if (u != null && consumer.accept(u).shouldAbort()) {
                return;
            }
        }
    }

    public Iterable<T> getAllEntities() {
        return Iterables.unmodifiableIterable(this.byId.values());
    }

    public void add(T entity) {
        UUID uuid = entity.getUUID();
        if (this.byUuid.containsKey(uuid)) {
            LOGGER.warn("Duplicate entity UUID {}: {}", uuid, entity);
        } else {
            this.byUuid.put(uuid, entity);
            this.byId.put(entity.getId(), entity);
        }
    }

    public void remove(T entity) {
        this.byUuid.remove(entity.getUUID());
        this.byId.remove(entity.getId());
    }

    @Nullable
    public T getEntity(int id) {
        return this.byId.get(id);
    }

    @Nullable
    public T getEntity(UUID uuid) {
        return this.byUuid.get(uuid);
    }

    public int count() {
        return this.byUuid.size();
    }
}
