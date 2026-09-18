package li.cil.oc.integration.create;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.TrainEditPacket;
import com.simibubi.create.foundation.utility.StringHelper;
import li.cil.oc.api.DataComponents;
import li.cil.oc.api.Network;
import li.cil.oc.api.UnrecoverablePersistanceException;
import li.cil.oc.api.datacomponents.MutableNbtComponentHolder;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
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
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.PatternSyntaxException;

/** The live Create train represented as an OC component on an onboard computer. */
public final class CreateTrainEnvironment extends AbstractManagedEnvironment implements NamedBlock {
    private static final Map<Environment, CreateTrainEnvironment> attached = new WeakHashMap<>();

    private final Train train;
    private final Level level;

    private CreateTrainEnvironment(final Train train, final Level level, final String address) {
        this.train = train;
        this.level = level;
        setNode(Network.newNode(this, Visibility.Network).withComponent("train").create());
        final MutableNbtComponentHolder addressHolder = new MutableNbtComponentHolder();
        addressHolder.set(DataComponents.ADDRESS.get(), address);
        try {
            node().loadData(addressHolder);
        } catch (final UnrecoverablePersistanceException e) {
            throw new IllegalStateException("failed to restore the train component address", e);
        }
    }

    /** Attach one train component to a computer that is currently inside a train carriage. */
    public static synchronized void attach(final Environment computer, final Train train, final Level level) {
        if (computer == null || computer.node() == null || train == null || level == null || attached.containsKey(computer))
            return;
        // The train component is recreated whenever Create restores a carriage.
        // Give it the same address for the same persisted computer/train pair,
        // otherwise the machine restores a stale random address and emits a
        // phantom component_removed after every world load.
        final String address = UUID.nameUUIDFromBytes((computer.node().address() + ":" + train.id)
                .getBytes(StandardCharsets.UTF_8)).toString();
        final CreateTrainEnvironment environment = new CreateTrainEnvironment(train, level, address);
        attached.put(computer, environment);
        computer.node().connect(environment.node());
    }

    /** Remove the transient component before Create tears down the moving computer. */
    public static synchronized void detach(final Environment computer) {
        final CreateTrainEnvironment environment = attached.remove(computer);
        if (environment != null && environment.node() != null)
            environment.node().remove();
    }

    @Override
    public String preferredName() {
        return "train";
    }

    @Override
    public int priority() {
        return 2;
    }

    private static Object[] result(final Object... values) {
        return values;
    }

    @Callback(direct = true, doc = "function():string -- Get the train name.")
    public Object[] getName(final Context context, final Arguments args) {
        return result(train.name.getString());
    }

    @Callback(doc = "function(name:string) -- Set the train name.")
    public Object[] setName(final Context context, final Arguments args) {
        final String name = args.checkString(0);
        train.name = Component.literal(name);
        CatnipServices.NETWORK.sendToAllClients(new TrainEditPacket.TrainEditReturnPacket(
                train.id, name, train.icon.getId(), train.mapColorIndex));
        return result();
    }

    @Callback(direct = true, doc = "function():string -- Get the train UUID.")
    public Object[] getId(final Context context, final Arguments args) {
        return result(train.id.toString());
    }

    @Callback(direct = true, doc = "function():number -- Get the current train speed in blocks per tick.")
    public Object[] getSpeed(final Context context, final Arguments args) {
        return result(train.speed);
    }

    @Callback(direct = true, doc = "function():number -- Get the current throttle from 0 to 1.")
    public Object[] getThrottle(final Context context, final Arguments args) {
        return result(train.throttle);
    }

    @Callback(doc = "function(value:number) -- Set the throttle from 1/18 to 1.")
    public Object[] setThrottle(final Context context, final Arguments args) {
        train.throttle = Math.max(1.0 / 18.0, Math.min(1.0, args.checkDouble(0)));
        return result(train.throttle);
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the train has a forward conductor.")
    public Object[] hasForwardConductor(final Context context, final Arguments args) {
        return result(train.hasForwardConductor());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the train has a backward conductor.")
    public Object[] hasBackwardConductor(final Context context, final Arguments args) {
        return result(train.hasBackwardConductor());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the train is derailed.")
    public Object[] isDerailed(final Context context, final Arguments args) {
        return result(train.derailed);
    }

    @Callback(direct = true, doc = "function():string or nil -- Get the current station name.")
    public Object[] getCurrentStation(final Context context, final Arguments args) {
        final GlobalStation station = train.getCurrentStation();
        return result(station == null ? null : station.name);
    }

    @Callback(direct = true, doc = "function():string -- Get the current schedule state.")
    public Object[] getState(final Context context, final Arguments args) {
        return result(train.runtime.state.toString());
    }

    @Callback(direct = true, doc = "function():boolean -- Whether schedule execution is paused.")
    public Object[] isPaused(final Context context, final Arguments args) {
        return result(train.runtime.paused);
    }

    @Callback(doc = "function(paused:boolean) -- Pause or resume schedule execution.")
    public Object[] setPaused(final Context context, final Arguments args) {
        train.runtime.paused = args.checkBoolean(0);
        return result(train.runtime.paused);
    }

    @Callback(direct = true, doc = "function():number -- Get the zero-based current schedule entry.")
    public Object[] getCurrentEntry(final Context context, final Arguments args) {
        return result(train.runtime.currentEntry);
    }

    @Callback(direct = true, doc = "function():string -- Get the current schedule title.")
    public Object[] getCurrentTitle(final Context context, final Arguments args) {
        return result(train.runtime.currentTitle);
    }

    @Callback(direct = true, doc = "function():boolean -- Whether the train has a schedule.")
    public Object[] hasSchedule(final Context context, final Arguments args) {
        return result(train.runtime.getSchedule() != null);
    }

    @Callback(direct = true, doc = "function():table -- Get the train schedule in Create's schedule format.")
    public Object[] getSchedule(final Context context, final Arguments args) {
        final Schedule schedule = train.runtime.getSchedule();
        if (schedule == null)
            throw new IllegalStateException("train doesn't have a schedule");
        return result(fromCompoundTag(schedule.write(level.registryAccess())));
    }

    @Callback(doc = "function(schedule:table) -- Replace the train schedule and start it.")
    public Object[] setSchedule(final Context context, final Arguments args) {
        final Schedule schedule = Schedule.fromTag(level.registryAccess(), toCompoundTag(args.checkTable(0)));
        if (schedule.entries.isEmpty())
            throw new IllegalArgumentException("Schedule must have at least one entry");
        final boolean automatic = train.runtime.getSchedule() == null || train.runtime.isAutoSchedule;
        train.runtime.setSchedule(schedule, automatic);
        return result();
    }

    @Callback(doc = "function() -- Remove the schedule and cancel navigation.")
    public Object[] clearSchedule(final Context context, final Arguments args) {
        train.runtime.discardSchedule();
        return result();
    }

    @Callback(direct = true, doc = "function(destination:string):boolean, string or nil -- Test whether a station is reachable.")
    public Object[] canReach(final Context context, final Arguments args) {
        final PathResult path = findPath(args.checkString(0));
        return path.path != null ? result(true, null)
                : result(false, path.destinationExists ? "cannot-reach" : "no-target");
    }

    @Callback(direct = true, doc = "function(destination:string):number or nil, string or nil -- Find the distance to a station.")
    public Object[] distanceTo(final Context context, final Arguments args) {
        final PathResult path = findPath(args.checkString(0));
        return path.path != null ? result(path.path.distance, null)
                : result(null, path.destinationExists ? "cannot-reach" : "no-target");
    }

    private PathResult findPath(final String destinationFilter) {
        final String regex = Glob.toRegexPattern(destinationFilter, "");
        boolean anyMatch = false;
        final ArrayList<GlobalStation> stations = new ArrayList<>();
        try {
            if (train.graph != null) {
                for (final GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
                    if (!station.name.matches(regex))
                        continue;
                    anyMatch = true;
                    stations.add(station);
                }
            }
        } catch (final PatternSyntaxException ignored) {
        }
        final DiscoveredPath path = train.navigation.findPathTo(stations, Double.MAX_VALUE);
        return new PathResult(path, anyMatch);
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
