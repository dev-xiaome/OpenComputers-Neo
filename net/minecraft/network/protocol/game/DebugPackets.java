package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.GameTestAddMarkerDebugPayload;
import net.minecraft.network.protocol.common.custom.GameTestClearMarkersDebugPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class DebugPackets {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void sendGameTestAddMarker(ServerLevel level, BlockPos pos, String text, int color, int lifetimeMillis) {
        sendPacketToAllPlayers(level, new GameTestAddMarkerDebugPayload(pos, color, text, lifetimeMillis));
    }

    public static void sendGameTestClearPacket(ServerLevel level) {
        sendPacketToAllPlayers(level, new GameTestClearMarkersDebugPayload());
    }

    public static void sendPoiPacketsForChunk(ServerLevel level, ChunkPos chunkPos) {
    }

    public static void sendPoiAddedPacket(ServerLevel level, BlockPos pos) {
        sendVillageSectionsPacket(level, pos);
    }

    public static void sendPoiRemovedPacket(ServerLevel level, BlockPos pos) {
        sendVillageSectionsPacket(level, pos);
    }

    public static void sendPoiTicketCountPacket(ServerLevel level, BlockPos pos) {
        sendVillageSectionsPacket(level, pos);
    }

    private static void sendVillageSectionsPacket(ServerLevel level, BlockPos pos) {
    }

    public static void sendPathFindingPacket(Level level, Mob mob, @Nullable Path path, float maxDistanceToWaypoint) {
    }

    public static void sendNeighborsUpdatePacket(Level level, BlockPos pos) {
    }

    public static void sendStructurePacket(WorldGenLevel level, StructureStart structureStart) {
    }

    public static void sendGoalSelector(Level level, Mob mob, GoalSelector goalSelector) {
    }

    public static void sendRaids(ServerLevel level, Collection<Raid> raids) {
    }

    public static void sendEntityBrain(LivingEntity livingEntity) {
    }

    public static void sendBeeInfo(Bee bee) {
    }

    public static void sendBreezeInfo(Breeze breeze) {
    }

    public static void sendGameEventInfo(Level level, Holder<GameEvent> gameEvent, Vec3 pos) {
    }

    public static void sendGameEventListenerInfo(Level level, GameEventListener gameEventListener) {
    }

    public static void sendHiveInfo(Level level, BlockPos pos, BlockState blockState, BeehiveBlockEntity hiveBlockEntity) {
    }

    private static List<String> getMemoryDescriptions(LivingEntity entity, long gameTime) {
        Map<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> map = entity.getBrain().getMemories();
        List<String> list = Lists.newArrayList();

        for (Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> entry : map.entrySet()) {
            MemoryModuleType<?> memorymoduletype = entry.getKey();
            Optional<? extends ExpirableValue<?>> optional = entry.getValue();
            String s;
            if (optional.isPresent()) {
                ExpirableValue<?> expirablevalue = (ExpirableValue<?>)optional.get();
                Object object = expirablevalue.getValue();
                if (memorymoduletype == MemoryModuleType.HEARD_BELL_TIME) {
                    long i = gameTime - (Long)object;
                    s = i + " ticks ago";
                } else if (expirablevalue.canExpire()) {
                    s = getShortDescription((ServerLevel)entity.level(), object) + " (ttl: " + expirablevalue.getTimeToLive() + ")";
                } else {
                    s = getShortDescription((ServerLevel)entity.level(), object);
                }
            } else {
                s = "-";
            }

            list.add(BuiltInRegistries.MEMORY_MODULE_TYPE.getKey(memorymoduletype).getPath() + ": " + s);
        }

        list.sort(String::compareTo);
        return list;
    }

    private static String getShortDescription(ServerLevel level, @Nullable Object p_object) {
        if (p_object == null) {
            return "-";
        } else if (p_object instanceof UUID) {
            return getShortDescription(level, level.getEntity((UUID)p_object));
        } else if (p_object instanceof LivingEntity) {
            Entity entity1 = (Entity)p_object;
            return DebugEntityNameGenerator.getEntityName(entity1);
        } else if (p_object instanceof Nameable) {
            return ((Nameable)p_object).getName().getString();
        } else if (p_object instanceof WalkTarget) {
            return getShortDescription(level, ((WalkTarget)p_object).getTarget());
        } else if (p_object instanceof EntityTracker) {
            return getShortDescription(level, ((EntityTracker)p_object).getEntity());
        } else if (p_object instanceof GlobalPos) {
            return getShortDescription(level, ((GlobalPos)p_object).pos());
        } else if (p_object instanceof BlockPosTracker) {
            return getShortDescription(level, ((BlockPosTracker)p_object).currentBlockPosition());
        } else if (p_object instanceof DamageSource) {
            Entity entity = ((DamageSource)p_object).getEntity();
            return entity == null ? p_object.toString() : getShortDescription(level, entity);
        } else if (!(p_object instanceof Collection)) {
            return p_object.toString();
        } else {
            List<String> list = Lists.newArrayList();

            for (Object object : (Iterable)p_object) {
                list.add(getShortDescription(level, object));
            }

            return list.toString();
        }
    }

    private static void sendPacketToAllPlayers(ServerLevel level, CustomPacketPayload payload) {
        Packet<?> packet = new ClientboundCustomPayloadPacket(payload);

        for (ServerPlayer serverplayer : level.players()) {
            serverplayer.connection.send(packet);
        }
    }
}
