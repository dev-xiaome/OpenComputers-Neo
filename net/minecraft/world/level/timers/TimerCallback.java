package net.minecraft.world.level.timers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

@FunctionalInterface
public interface TimerCallback<T> {
    void handle(T obj, TimerQueue<T> manager, long gameTime);

    public abstract static class Serializer<T, C extends TimerCallback<T>> {
        private final ResourceLocation id;
        private final Class<?> cls;

        public Serializer(ResourceLocation id, Class<?> cls) {
            this.id = id;
            this.cls = cls;
        }

        public ResourceLocation getId() {
            return this.id;
        }

        public Class<?> getCls() {
            return this.cls;
        }

        public abstract void serialize(CompoundTag tag, C callback);

        public abstract C deserialize(CompoundTag tag);
    }
}
