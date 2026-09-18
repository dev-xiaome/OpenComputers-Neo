package li.cil.oc.integration.create;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.TrainEditPacket;
import com.simibubi.create.foundation.utility.StringHelper;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.createmod.catnip.data.Glob;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public final class CreateStationEnvironment extends CreateEnvironment<StationBlockEntity> {
    CreateStationEnvironment(final StationBlockEntity blockEntity) {
        super(blockEntity, "Create_Station");
    }

    @Callback
    public Object[] assemble(final Context context, final Arguments args) {
        if (!blockEntity.isAssembling())
            throw new IllegalStateException("station must be in assembly mode");
        blockEntity.assemble(null);
        if (blockEntity.getStation() == null || blockEntity.getStation().getPresentTrain() == null)
            throw new IllegalStateException("failed to assemble train");
        if (!blockEntity.exitAssemblyMode())
            throw new IllegalStateException("failed to exit assembly mode");
        return result();
    }

    @Callback
    public Object[] disassemble(final Context context, final Arguments args) {
        if (blockEntity.isAssembling())
            throw new IllegalStateException("station must not be in assembly mode");
        getTrainOrThrow();
        if (!blockEntity.enterAssemblyMode(null))
            throw new IllegalStateException("could not disassemble train");
        return result();
    }

    @Callback
    public Object[] setAssemblyMode(final Context context, final Arguments args) {
        final boolean assemblyMode = args.checkBoolean(0);
        if (assemblyMode && !blockEntity.enterAssemblyMode(null))
            throw new IllegalStateException("failed to enter assembly mode");
        if (!assemblyMode && !blockEntity.exitAssemblyMode())
            throw new IllegalStateException("failed to exit assembly mode");
        return result();
    }

    @Callback(direct = true)
    public Object[] isInAssemblyMode(final Context context, final Arguments args) {
        return result(blockEntity.isAssembling());
    }

    @Callback(direct = true)
    public Object[] getStationName(final Context context, final Arguments args) {
        return result(getStationOrThrow().name);
    }

    @Callback
    public Object[] setStationName(final Context context, final Arguments args) {
        if (!blockEntity.updateName(args.checkString(0)))
            throw new IllegalStateException("could not set station name");
        return result();
    }

    @Callback(direct = true)
    public Object[] isTrainPresent(final Context context, final Arguments args) {
        return result(getStationOrThrow().getPresentTrain() != null);
    }

    @Callback(direct = true)
    public Object[] isTrainImminent(final Context context, final Arguments args) {
        return result(getStationOrThrow().getImminentTrain() != null);
    }

    @Callback(direct = true)
    public Object[] isTrainEnroute(final Context context, final Arguments args) {
        return result(getStationOrThrow().getNearestTrain() != null);
    }

    @Callback(direct = true)
    public Object[] getTrainName(final Context context, final Arguments args) {
        return result(getTrainOrThrow().name.getString());
    }

    @Callback
    public Object[] setTrainName(final Context context, final Arguments args) {
        final Train train = getTrainOrThrow();
        final String name = args.checkString(0);
        train.name = Component.literal(name);
        CatnipServices.NETWORK.sendToAllClients(new TrainEditPacket.TrainEditReturnPacket(
                train.id, name, train.icon.getId(), train.mapColorIndex));
        return result();
    }

    @Callback(direct = true)
    public Object[] hasSchedule(final Context context, final Arguments args) {
        return result(getTrainOrThrow().runtime.getSchedule() != null);
    }

    @Callback(direct = true)
    public Object[] getSchedule(final Context context, final Arguments args) {
        final Schedule schedule = getTrainOrThrow().runtime.getSchedule();
        if (schedule == null)
            throw new IllegalStateException("train doesn't have a schedule");
        return result(fromCompoundTag(schedule.write(blockEntity.getLevel().registryAccess())));
    }

    @Callback
    public Object[] setSchedule(final Context context, final Arguments args) {
        final Train train = getTrainOrThrow();
        final Schedule schedule = Schedule.fromTag(blockEntity.getLevel().registryAccess(), toCompoundTag(args.checkTable(0)));
        if (schedule.entries.isEmpty())
            throw new IllegalArgumentException("Schedule must have at least one entry");
        final boolean automatic = train.runtime.getSchedule() == null || train.runtime.isAutoSchedule;
        train.runtime.setSchedule(schedule, automatic);
        return result();
    }

    @Callback(direct = true)
    public Object[] canTrainReach(final Context context, final Arguments args) {
        final PathResult path = findPath(args.checkString(0));
        return path.path != null ? result(true, null)
                : result(false, path.destinationExists ? "cannot-reach" : "no-target");
    }

    @Callback(direct = true)
    public Object[] distanceTo(final Context context, final Arguments args) {
        final PathResult path = findPath(args.checkString(0));
        return path.path != null ? result(path.path.distance, null)
                : result(null, path.destinationExists ? "cannot-reach" : "no-target");
    }

    private PathResult findPath(final String destinationFilter) {
        final Train train = getTrainOrThrow();
        final String regex = Glob.toRegexPattern(destinationFilter, "");
        boolean anyMatch = false;
        final ArrayList<GlobalStation> stations = new ArrayList<>();
        try {
            for (final GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
                if (!station.name.matches(regex))
                    continue;
                anyMatch = true;
                stations.add(station);
            }
        } catch (final PatternSyntaxException ignored) {
        }
        return new PathResult(train.navigation.findPathTo(stations, Double.MAX_VALUE), anyMatch);
    }

    private GlobalStation getStationOrThrow() {
        final GlobalStation station = blockEntity.getStation();
        if (station == null)
            throw new IllegalStateException("station is not connected to a track");
        return station;
    }

    private Train getTrainOrThrow() {
        final Train train = getStationOrThrow().getPresentTrain();
        if (train == null)
            throw new IllegalStateException("there is no train present");
        return train;
    }

    private static Map<Object, Object> fromCompoundTag(final CompoundTag tag) {
        return castMap(fromNbtTag(null, tag));
    }

    private static Object fromNbtTag(final String key, final Tag tag) {
        final byte type = tag.getId();
        if (type == Tag.TAG_BYTE && "Count".equals(key))
            return ((NumericTag) tag).getAsByte();
        if (type == Tag.TAG_BYTE)
            return ((NumericTag) tag).getAsByte() != 0;
        if (type == Tag.TAG_SHORT || type == Tag.TAG_INT || type == Tag.TAG_LONG)
            return ((NumericTag) tag).getAsLong();
        if (type == Tag.TAG_FLOAT || type == Tag.TAG_DOUBLE)
            return ((NumericTag) tag).getAsDouble();
        if (type == Tag.TAG_STRING)
            return tag.getAsString();
        if (type == Tag.TAG_LIST || type == Tag.TAG_BYTE_ARRAY || type == Tag.TAG_INT_ARRAY || type == Tag.TAG_LONG_ARRAY) {
            final Map<Integer, Object> list = new LinkedHashMap<>();
            final CollectionTag<?> collection = (CollectionTag<?>) tag;
            for (int index = 0; index < collection.size(); index++)
                list.put(index + 1, fromNbtTag(null, collection.get(index)));
            return list;
        }
        if (type == Tag.TAG_COMPOUND) {
            final Map<Object, Object> table = new LinkedHashMap<>();
            final CompoundTag compound = (CompoundTag) tag;
            for (final String compoundKey : compound.getAllKeys())
                table.put(StringHelper.camelCaseToSnakeCase(compoundKey),
                        fromNbtTag(compoundKey, compound.get(compoundKey)));
            return table;
        }
        throw new IllegalArgumentException("unknown tag type " + tag.getType().getName());
    }

    private static CompoundTag toCompoundTag(final Map<?, ?> table) {
        return (CompoundTag) toNbtTag(null, table);
    }

    private static Tag toNbtTag(final String key, final Object value) {
        if (value instanceof Boolean bool)
            return ByteTag.valueOf(bool);
        if (value instanceof Byte || "count".equals(key))
            return ByteTag.valueOf(((Number) value).byteValue());
        if (value instanceof Number number)
            return number.intValue() == number.doubleValue()
                    ? IntTag.valueOf(number.intValue()) : DoubleTag.valueOf(number.doubleValue());
        if (value instanceof String string)
            return StringTag.valueOf(string);
        if (value instanceof Map<?, ?> map && isArrayMap(map)) {
            final ListTag list = new ListTag();
            for (int index = 1; index <= map.size(); index++)
                list.add(toNbtTag(null, getNumericKey(map, index)));
            return list;
        }
        if (value instanceof Map<?, ?> map) {
            final CompoundTag compound = new CompoundTag();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String compoundKey))
                    throw new IllegalArgumentException("table key is not of type string");
                final String nbtKey = compoundKey.equals("id") && map.containsKey("count")
                        ? "id" : StringHelper.snakeCaseToCamelCase(compoundKey);
                compound.put(nbtKey, toNbtTag(compoundKey, entry.getValue()));
            }
            return compound;
        }
        throw new IllegalArgumentException("unknown object type " + (value == null ? "nil" : value.getClass().getName()));
    }

    private static boolean isArrayMap(final Map<?, ?> map) {
        for (int index = 1; index <= map.size(); index++)
            if (getNumericKey(map, index) == null)
                return false;
        return !map.isEmpty();
    }

    private static Object getNumericKey(final Map<?, ?> map, final int index) {
        if (map.containsKey(index)) return map.get(index);
        if (map.containsKey((double) index)) return map.get((double) index);
        if (map.containsKey((long) index)) return map.get((long) index);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> castMap(final Object value) {
        return (Map<Object, Object>) value;
    }

    private record PathResult(DiscoveredPath path, boolean destinationExists) {
    }
}
