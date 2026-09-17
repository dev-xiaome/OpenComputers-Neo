package com.mojang.realmsclient.dto;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ServerData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsServer extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_VALUE = -1;
    public long id;
    public String remoteSubscriptionId;
    public String name;
    public String motd;
    public RealmsServer.State state;
    public String owner;
    public UUID ownerUUID = Util.NIL_UUID;
    public List<PlayerInfo> players;
    public Map<Integer, RealmsWorldOptions> slots;
    public boolean expired;
    public boolean expiredTrial;
    public int daysLeft;
    public RealmsServer.WorldType worldType;
    public int activeSlot;
    @Nullable
    public String minigameName;
    public int minigameId;
    public String minigameImage;
    public long parentRealmId = -1L;
    @Nullable
    public String parentWorldName;
    public String activeVersion = "";
    public RealmsServer.Compatibility compatibility = RealmsServer.Compatibility.UNVERIFIABLE;

    public String getDescription() {
        return this.motd;
    }

    public String getName() {
        return this.name;
    }

    @Nullable
    public String getMinigameName() {
        return this.minigameName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String motd) {
        this.motd = motd;
    }

    public static RealmsServer parse(JsonObject json) {
        RealmsServer realmsserver = new RealmsServer();

        try {
            realmsserver.id = JsonUtils.getLongOr("id", json, -1L);
            realmsserver.remoteSubscriptionId = JsonUtils.getStringOr("remoteSubscriptionId", json, null);
            realmsserver.name = JsonUtils.getStringOr("name", json, null);
            realmsserver.motd = JsonUtils.getStringOr("motd", json, null);
            realmsserver.state = getState(JsonUtils.getStringOr("state", json, RealmsServer.State.CLOSED.name()));
            realmsserver.owner = JsonUtils.getStringOr("owner", json, null);
            if (json.get("players") != null && json.get("players").isJsonArray()) {
                realmsserver.players = parseInvited(json.get("players").getAsJsonArray());
                sortInvited(realmsserver);
            } else {
                realmsserver.players = Lists.newArrayList();
            }

            realmsserver.daysLeft = JsonUtils.getIntOr("daysLeft", json, 0);
            realmsserver.expired = JsonUtils.getBooleanOr("expired", json, false);
            realmsserver.expiredTrial = JsonUtils.getBooleanOr("expiredTrial", json, false);
            realmsserver.worldType = getWorldType(JsonUtils.getStringOr("worldType", json, RealmsServer.WorldType.NORMAL.name()));
            realmsserver.ownerUUID = JsonUtils.getUuidOr("ownerUUID", json, Util.NIL_UUID);
            if (json.get("slots") != null && json.get("slots").isJsonArray()) {
                realmsserver.slots = parseSlots(json.get("slots").getAsJsonArray());
            } else {
                realmsserver.slots = createEmptySlots();
            }

            realmsserver.minigameName = JsonUtils.getStringOr("minigameName", json, null);
            realmsserver.activeSlot = JsonUtils.getIntOr("activeSlot", json, -1);
            realmsserver.minigameId = JsonUtils.getIntOr("minigameId", json, -1);
            realmsserver.minigameImage = JsonUtils.getStringOr("minigameImage", json, null);
            realmsserver.parentRealmId = JsonUtils.getLongOr("parentWorldId", json, -1L);
            realmsserver.parentWorldName = JsonUtils.getStringOr("parentWorldName", json, null);
            realmsserver.activeVersion = JsonUtils.getStringOr("activeVersion", json, "");
            realmsserver.compatibility = getCompatibility(JsonUtils.getStringOr("compatibility", json, RealmsServer.Compatibility.UNVERIFIABLE.name()));
        } catch (Exception exception) {
            LOGGER.error("Could not parse McoServer: {}", exception.getMessage());
        }

        return realmsserver;
    }

    private static void sortInvited(RealmsServer realmsServer) {
        realmsServer.players
            .sort(
                (p_87502_, p_87503_) -> ComparisonChain.start()
                        .compareFalseFirst(p_87503_.getAccepted(), p_87502_.getAccepted())
                        .compare(p_87502_.getName().toLowerCase(Locale.ROOT), p_87503_.getName().toLowerCase(Locale.ROOT))
                        .result()
            );
    }

    private static List<PlayerInfo> parseInvited(JsonArray jsonArray) {
        List<PlayerInfo> list = Lists.newArrayList();

        for (JsonElement jsonelement : jsonArray) {
            try {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                PlayerInfo playerinfo = new PlayerInfo();
                playerinfo.setName(JsonUtils.getStringOr("name", jsonobject, null));
                playerinfo.setUuid(JsonUtils.getUuidOr("uuid", jsonobject, Util.NIL_UUID));
                playerinfo.setOperator(JsonUtils.getBooleanOr("operator", jsonobject, false));
                playerinfo.setAccepted(JsonUtils.getBooleanOr("accepted", jsonobject, false));
                playerinfo.setOnline(JsonUtils.getBooleanOr("online", jsonobject, false));
                list.add(playerinfo);
            } catch (Exception exception) {
            }
        }

        return list;
    }

    private static Map<Integer, RealmsWorldOptions> parseSlots(JsonArray jsonArray) {
        Map<Integer, RealmsWorldOptions> map = Maps.newHashMap();

        for (JsonElement jsonelement : jsonArray) {
            try {
                JsonObject jsonobject = jsonelement.getAsJsonObject();
                JsonParser jsonparser = new JsonParser();
                JsonElement jsonelement1 = jsonparser.parse(jsonobject.get("options").getAsString());
                RealmsWorldOptions realmsworldoptions;
                if (jsonelement1 == null) {
                    realmsworldoptions = RealmsWorldOptions.createDefaults();
                } else {
                    realmsworldoptions = RealmsWorldOptions.parse(jsonelement1.getAsJsonObject());
                }

                int i = JsonUtils.getIntOr("slotId", jsonobject, -1);
                map.put(i, realmsworldoptions);
            } catch (Exception exception) {
            }
        }

        for (int j = 1; j <= 3; j++) {
            if (!map.containsKey(j)) {
                map.put(j, RealmsWorldOptions.createEmptyDefaults());
            }
        }

        return map;
    }

    private static Map<Integer, RealmsWorldOptions> createEmptySlots() {
        Map<Integer, RealmsWorldOptions> map = Maps.newHashMap();
        map.put(1, RealmsWorldOptions.createEmptyDefaults());
        map.put(2, RealmsWorldOptions.createEmptyDefaults());
        map.put(3, RealmsWorldOptions.createEmptyDefaults());
        return map;
    }

    public static RealmsServer parse(String json) {
        try {
            return parse(new JsonParser().parse(json).getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.error("Could not parse McoServer: {}", exception.getMessage());
            return new RealmsServer();
        }
    }

    private static RealmsServer.State getState(String name) {
        try {
            return RealmsServer.State.valueOf(name);
        } catch (Exception exception) {
            return RealmsServer.State.CLOSED;
        }
    }

    private static RealmsServer.WorldType getWorldType(String name) {
        try {
            return RealmsServer.WorldType.valueOf(name);
        } catch (Exception exception) {
            return RealmsServer.WorldType.NORMAL;
        }
    }

    public static RealmsServer.Compatibility getCompatibility(@Nullable String id) {
        try {
            return RealmsServer.Compatibility.valueOf(id);
        } catch (Exception exception) {
            return RealmsServer.Compatibility.UNVERIFIABLE;
        }
    }

    public boolean isCompatible() {
        return this.compatibility.isCompatible();
    }

    public boolean needsUpgrade() {
        return this.compatibility.needsUpgrade();
    }

    public boolean needsDowngrade() {
        return this.compatibility.needsDowngrade();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.motd, this.state, this.owner, this.expired);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        } else if (other == this) {
            return true;
        } else if (other.getClass() != this.getClass()) {
            return false;
        } else {
            RealmsServer realmsserver = (RealmsServer)other;
            return new EqualsBuilder()
                .append(this.id, realmsserver.id)
                .append(this.name, realmsserver.name)
                .append(this.motd, realmsserver.motd)
                .append(this.state, realmsserver.state)
                .append(this.owner, realmsserver.owner)
                .append(this.expired, realmsserver.expired)
                .append(this.worldType, this.worldType)
                .isEquals();
        }
    }

    public RealmsServer clone() {
        RealmsServer realmsserver = new RealmsServer();
        realmsserver.id = this.id;
        realmsserver.remoteSubscriptionId = this.remoteSubscriptionId;
        realmsserver.name = this.name;
        realmsserver.motd = this.motd;
        realmsserver.state = this.state;
        realmsserver.owner = this.owner;
        realmsserver.players = this.players;
        realmsserver.slots = this.cloneSlots(this.slots);
        realmsserver.expired = this.expired;
        realmsserver.expiredTrial = this.expiredTrial;
        realmsserver.daysLeft = this.daysLeft;
        realmsserver.worldType = this.worldType;
        realmsserver.ownerUUID = this.ownerUUID;
        realmsserver.minigameName = this.minigameName;
        realmsserver.activeSlot = this.activeSlot;
        realmsserver.minigameId = this.minigameId;
        realmsserver.minigameImage = this.minigameImage;
        realmsserver.parentWorldName = this.parentWorldName;
        realmsserver.parentRealmId = this.parentRealmId;
        realmsserver.activeVersion = this.activeVersion;
        realmsserver.compatibility = this.compatibility;
        return realmsserver;
    }

    public Map<Integer, RealmsWorldOptions> cloneSlots(Map<Integer, RealmsWorldOptions> slots) {
        Map<Integer, RealmsWorldOptions> map = Maps.newHashMap();

        for (Entry<Integer, RealmsWorldOptions> entry : slots.entrySet()) {
            map.put(entry.getKey(), entry.getValue().clone());
        }

        return map;
    }

    public boolean isSnapshotRealm() {
        return this.parentRealmId != -1L;
    }

    public boolean isMinigameActive() {
        return this.worldType == RealmsServer.WorldType.MINIGAME;
    }

    public String getWorldName(int slot) {
        return this.name + " (" + this.slots.get(slot).getSlotName(slot) + ")";
    }

    public ServerData toServerData(String ip) {
        return new ServerData(this.name, ip, ServerData.Type.REALM);
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Compatibility {
        UNVERIFIABLE,
        INCOMPATIBLE,
        RELEASE_TYPE_INCOMPATIBLE,
        NEEDS_DOWNGRADE,
        NEEDS_UPGRADE,
        COMPATIBLE;

        public boolean isCompatible() {
            return this == COMPATIBLE;
        }

        public boolean needsUpgrade() {
            return this == NEEDS_UPGRADE;
        }

        public boolean needsDowngrade() {
            return this == NEEDS_DOWNGRADE;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class McoServerComparator implements Comparator<RealmsServer> {
        private final String refOwner;

        public McoServerComparator(String refOwner) {
            this.refOwner = refOwner;
        }

        public int compare(RealmsServer first, RealmsServer second) {
            return ComparisonChain.start()
                .compareTrueFirst(first.isSnapshotRealm(), second.isSnapshotRealm())
                .compareTrueFirst(first.state == RealmsServer.State.UNINITIALIZED, second.state == RealmsServer.State.UNINITIALIZED)
                .compareTrueFirst(first.expiredTrial, second.expiredTrial)
                .compareTrueFirst(first.owner.equals(this.refOwner), second.owner.equals(this.refOwner))
                .compareFalseFirst(first.expired, second.expired)
                .compareTrueFirst(first.state == RealmsServer.State.OPEN, second.state == RealmsServer.State.OPEN)
                .compare(first.id, second.id)
                .result();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static enum State {
        CLOSED,
        OPEN,
        UNINITIALIZED;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum WorldType {
        NORMAL,
        MINIGAME,
        ADVENTUREMAP,
        EXPERIENCE,
        INSPIRATION;
    }
}
