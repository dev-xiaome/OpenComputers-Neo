package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class Raids extends SavedData {
    private static final String RAID_FILE_ID = "raids";
    private final Map<Integer, Raid> raidMap = Maps.newHashMap();
    private final ServerLevel level;
    private int nextAvailableID;
    private int tick;

    public static SavedData.Factory<Raids> factory(ServerLevel level) {
        return new SavedData.Factory<>(() -> new Raids(level), (p_294039_, p_324123_) -> load(level, p_294039_), DataFixTypes.SAVED_DATA_RAIDS);
    }

    public Raids(ServerLevel level) {
        this.level = level;
        this.nextAvailableID = 1;
        this.setDirty();
    }

    public Raid get(int id) {
        return this.raidMap.get(id);
    }

    public void tick() {
        this.tick++;
        Iterator<Raid> iterator = this.raidMap.values().iterator();

        while (iterator.hasNext()) {
            Raid raid = iterator.next();
            if (this.level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                raid.stop();
            }

            if (raid.isStopped()) {
                iterator.remove();
                this.setDirty();
            } else {
                raid.tick();
            }
        }

        if (this.tick % 200 == 0) {
            this.setDirty();
        }

        DebugPackets.sendRaids(this.level, this.raidMap.values());
    }

    public static boolean canJoinRaid(Raider raider, Raid raid) {
        return raider != null && raid != null && raid.getLevel() != null
            ? raider.isAlive()
                && raider.canJoinRaid()
                && raider.getNoActionTime() <= 2400
                && raider.level().dimensionType() == raid.getLevel().dimensionType()
            : false;
    }

    @Nullable
    public Raid createOrExtendRaid(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator()) {
            return null;
        } else if (this.level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
            return null;
        } else {
            DimensionType dimensiontype = player.level().dimensionType();
            if (!dimensiontype.hasRaids()) {
                return null;
            } else {
                List<PoiRecord> list = this.level
                    .getPoiManager()
                    .getInRange(p_219845_ -> p_219845_.is(PoiTypeTags.VILLAGE), pos, 64, PoiManager.Occupancy.IS_OCCUPIED)
                    .toList();
                int i = 0;
                Vec3 vec3 = Vec3.ZERO;

                for (PoiRecord poirecord : list) {
                    BlockPos blockpos = poirecord.getPos();
                    vec3 = vec3.add((double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ());
                    i++;
                }

                BlockPos blockpos1;
                if (i > 0) {
                    vec3 = vec3.scale(1.0 / (double)i);
                    blockpos1 = BlockPos.containing(vec3);
                } else {
                    blockpos1 = pos;
                }

                Raid raid = this.getOrCreateRaid(player.serverLevel(), blockpos1);
                if (!raid.isStarted() && !this.raidMap.containsKey(raid.getId())) {
                    this.raidMap.put(raid.getId(), raid);
                }

                if (!raid.isStarted() || raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel()) {
                    raid.absorbRaidOmen(player);
                }

                this.setDirty();
                return raid;
            }
        }
    }

    private Raid getOrCreateRaid(ServerLevel serverLevel, BlockPos pos) {
        Raid raid = serverLevel.getRaidAt(pos);
        return raid != null ? raid : new Raid(this.getUniqueId(), serverLevel, pos);
    }

    public static Raids load(ServerLevel level, CompoundTag tag) {
        Raids raids = new Raids(level);
        raids.nextAvailableID = tag.getInt("NextAvailableID");
        raids.tick = tag.getInt("Tick");
        ListTag listtag = tag.getList("Raids", 10);

        for (int i = 0; i < listtag.size(); i++) {
            CompoundTag compoundtag = listtag.getCompound(i);
            Raid raid = new Raid(level, compoundtag);
            raids.raidMap.put(raid.getId(), raid);
        }

        return raids;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextAvailableID", this.nextAvailableID);
        tag.putInt("Tick", this.tick);
        ListTag listtag = new ListTag();

        for (Raid raid : this.raidMap.values()) {
            CompoundTag compoundtag = new CompoundTag();
            raid.save(compoundtag);
            listtag.add(compoundtag);
        }

        tag.put("Raids", listtag);
        return tag;
    }

    public static String getFileId(Holder<DimensionType> dimensionTypeHolder) {
        return dimensionTypeHolder.is(BuiltinDimensionTypes.END) ? "raids_end" : "raids";
    }

    private int getUniqueId() {
        return ++this.nextAvailableID;
    }

    @Nullable
    public Raid getNearbyRaid(BlockPos pos, int distance) {
        Raid raid = null;
        double d0 = (double)distance;

        for (Raid raid1 : this.raidMap.values()) {
            double d1 = raid1.getCenter().distSqr(pos);
            if (raid1.isActive() && d1 < d0) {
                raid = raid1;
                d0 = d1;
            }
        }

        return raid;
    }
}
