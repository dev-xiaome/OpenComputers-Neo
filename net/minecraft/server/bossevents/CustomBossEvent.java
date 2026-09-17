package net.minecraft.server.bossevents;

import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public class CustomBossEvent extends ServerBossEvent {
    private final ResourceLocation id;
    private final Set<UUID> players = Sets.newHashSet();
    private int value;
    private int max = 100;

    public CustomBossEvent(ResourceLocation id, Component name) {
        super(name, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        this.id = id;
        this.setProgress(0.0F);
    }

    public ResourceLocation getTextId() {
        return this.id;
    }

    /**
     * Makes the boss visible to the given player.
     */
    @Override
    public void addPlayer(ServerPlayer player) {
        super.addPlayer(player);
        this.players.add(player.getUUID());
    }

    public void addOfflinePlayer(UUID player) {
        this.players.add(player);
    }

    /**
     * Makes the boss non-visible to the given player.
     */
    @Override
    public void removePlayer(ServerPlayer player) {
        super.removePlayer(player);
        this.players.remove(player.getUUID());
    }

    @Override
    public void removeAllPlayers() {
        super.removeAllPlayers();
        this.players.clear();
    }

    public int getValue() {
        return this.value;
    }

    public int getMax() {
        return this.max;
    }

    public void setValue(int value) {
        this.value = value;
        this.setProgress(Mth.clamp((float)value / (float)this.max, 0.0F, 1.0F));
    }

    public void setMax(int max) {
        this.max = max;
        this.setProgress(Mth.clamp((float)this.value / (float)max, 0.0F, 1.0F));
    }

    public final Component getDisplayName() {
        return ComponentUtils.wrapInSquareBrackets(this.getName())
            .withStyle(
                p_329881_ -> p_329881_.withColor(this.getColor().getFormatting())
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(this.getTextId().toString())))
                        .withInsertion(this.getTextId().toString())
            );
    }

    public boolean setPlayers(Collection<ServerPlayer> serverPlayerList) {
        Set<UUID> set = Sets.newHashSet();
        Set<ServerPlayer> set1 = Sets.newHashSet();

        for (UUID uuid : this.players) {
            boolean flag = false;

            for (ServerPlayer serverplayer : serverPlayerList) {
                if (serverplayer.getUUID().equals(uuid)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                set.add(uuid);
            }
        }

        for (ServerPlayer serverplayer1 : serverPlayerList) {
            boolean flag1 = false;

            for (UUID uuid2 : this.players) {
                if (serverplayer1.getUUID().equals(uuid2)) {
                    flag1 = true;
                    break;
                }
            }

            if (!flag1) {
                set1.add(serverplayer1);
            }
        }

        for (UUID uuid1 : set) {
            for (ServerPlayer serverplayer3 : this.getPlayers()) {
                if (serverplayer3.getUUID().equals(uuid1)) {
                    this.removePlayer(serverplayer3);
                    break;
                }
            }

            this.players.remove(uuid1);
        }

        for (ServerPlayer serverplayer2 : set1) {
            this.addPlayer(serverplayer2);
        }

        return !set.isEmpty() || !set1.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider levelRegistry) {
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putString("Name", Component.Serializer.toJson(this.name, levelRegistry));
        compoundtag.putBoolean("Visible", this.isVisible());
        compoundtag.putInt("Value", this.value);
        compoundtag.putInt("Max", this.max);
        compoundtag.putString("Color", this.getColor().getName());
        compoundtag.putString("Overlay", this.getOverlay().getName());
        compoundtag.putBoolean("DarkenScreen", this.shouldDarkenScreen());
        compoundtag.putBoolean("PlayBossMusic", this.shouldPlayBossMusic());
        compoundtag.putBoolean("CreateWorldFog", this.shouldCreateWorldFog());
        ListTag listtag = new ListTag();

        for (UUID uuid : this.players) {
            listtag.add(NbtUtils.createUUID(uuid));
        }

        compoundtag.put("Players", listtag);
        return compoundtag;
    }

    public static CustomBossEvent load(CompoundTag p_tag, ResourceLocation id, HolderLookup.Provider levelRegistry) {
        CustomBossEvent custombossevent = new CustomBossEvent(id, Component.Serializer.fromJson(p_tag.getString("Name"), levelRegistry));
        custombossevent.setVisible(p_tag.getBoolean("Visible"));
        custombossevent.setValue(p_tag.getInt("Value"));
        custombossevent.setMax(p_tag.getInt("Max"));
        custombossevent.setColor(BossEvent.BossBarColor.byName(p_tag.getString("Color")));
        custombossevent.setOverlay(BossEvent.BossBarOverlay.byName(p_tag.getString("Overlay")));
        custombossevent.setDarkenScreen(p_tag.getBoolean("DarkenScreen"));
        custombossevent.setPlayBossMusic(p_tag.getBoolean("PlayBossMusic"));
        custombossevent.setCreateWorldFog(p_tag.getBoolean("CreateWorldFog"));

        for (Tag tag : p_tag.getList("Players", 11)) {
            custombossevent.addOfflinePlayer(NbtUtils.loadUUID(tag));
        }

        return custombossevent;
    }

    public void onPlayerConnect(ServerPlayer player) {
        if (this.players.contains(player.getUUID())) {
            this.addPlayer(player);
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        super.removePlayer(player);
    }
}
