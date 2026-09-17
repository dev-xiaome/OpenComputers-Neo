package net.minecraft.server.bossevents;

import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class CustomBossEvents {
    private final Map<ResourceLocation, CustomBossEvent> events = Maps.newHashMap();

    @Nullable
    public CustomBossEvent get(ResourceLocation id) {
        return this.events.get(id);
    }

    public CustomBossEvent create(ResourceLocation id, Component name) {
        CustomBossEvent custombossevent = new CustomBossEvent(id, name);
        this.events.put(id, custombossevent);
        return custombossevent;
    }

    public void remove(CustomBossEvent bossbar) {
        this.events.remove(bossbar.getTextId());
    }

    public Collection<ResourceLocation> getIds() {
        return this.events.keySet();
    }

    public Collection<CustomBossEvent> getEvents() {
        return this.events.values();
    }

    public CompoundTag save(HolderLookup.Provider levelRegistry) {
        CompoundTag compoundtag = new CompoundTag();

        for (CustomBossEvent custombossevent : this.events.values()) {
            compoundtag.put(custombossevent.getTextId().toString(), custombossevent.save(levelRegistry));
        }

        return compoundtag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        for (String s : tag.getAllKeys()) {
            ResourceLocation resourcelocation = ResourceLocation.parse(s);
            this.events.put(resourcelocation, CustomBossEvent.load(tag.getCompound(s), resourcelocation, levelRegistry));
        }
    }

    public void onPlayerConnect(ServerPlayer player) {
        for (CustomBossEvent custombossevent : this.events.values()) {
            custombossevent.onPlayerConnect(player);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        for (CustomBossEvent custombossevent : this.events.values()) {
            custombossevent.onPlayerDisconnect(player);
        }
    }
}
