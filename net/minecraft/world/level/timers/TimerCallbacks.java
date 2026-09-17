package net.minecraft.world.level.timers;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public class TimerCallbacks<C> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final TimerCallbacks<MinecraftServer> SERVER_CALLBACKS = new TimerCallbacks<MinecraftServer>()
        .register(new FunctionCallback.Serializer())
        .register(new FunctionTagCallback.Serializer());
    private final Map<ResourceLocation, TimerCallback.Serializer<C, ?>> idToSerializer = Maps.newHashMap();
    private final Map<Class<?>, TimerCallback.Serializer<C, ?>> classToSerializer = Maps.newHashMap();

    public TimerCallbacks<C> register(TimerCallback.Serializer<C, ?> serializer) {
        this.idToSerializer.put(serializer.getId(), serializer);
        this.classToSerializer.put(serializer.getCls(), serializer);
        return this;
    }

    private <T extends TimerCallback<C>> TimerCallback.Serializer<C, T> getSerializer(Class<?> clazz) {
        return (TimerCallback.Serializer<C, T>)this.classToSerializer.get(clazz);
    }

    public <T extends TimerCallback<C>> CompoundTag serialize(T callback) {
        TimerCallback.Serializer<C, T> serializer = this.getSerializer(callback.getClass());
        CompoundTag compoundtag = new CompoundTag();
        serializer.serialize(compoundtag, callback);
        compoundtag.putString("Type", serializer.getId().toString());
        return compoundtag;
    }

    @Nullable
    public TimerCallback<C> deserialize(CompoundTag tag) {
        ResourceLocation resourcelocation = ResourceLocation.tryParse(tag.getString("Type"));
        TimerCallback.Serializer<C, ?> serializer = this.idToSerializer.get(resourcelocation);
        if (serializer == null) {
            LOGGER.error("Failed to deserialize timer callback: {}", tag);
            return null;
        } else {
            try {
                return serializer.deserialize(tag);
            } catch (Exception exception) {
                LOGGER.error("Failed to deserialize timer callback: {}", tag, exception);
                return null;
            }
        }
    }
}
