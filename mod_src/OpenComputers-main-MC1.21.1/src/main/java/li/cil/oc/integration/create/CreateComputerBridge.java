package li.cil.oc.integration.create;

import com.simibubi.create.compat.computercraft.events.ComputerEvent;
import com.simibubi.create.compat.computercraft.events.KineticsChangeEvent;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.compat.computercraft.events.RepackageEvent;
import com.simibubi.create.compat.computercraft.events.SignalStateChangeEvent;
import com.simibubi.create.compat.computercraft.events.StationTrainPresenceEvent;
import com.simibubi.create.compat.computercraft.events.TrainPassEvent;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Shared state used by the native drivers and the Create computer-behaviour mixins. */
public final class CreateComputerBridge {
    public static final String ATTACHED_TAG = "OpenComputersAttached";

    private static final WeakHashMap<SmartBlockEntity, Set<CreateEnvironment<?>>> environments = new WeakHashMap<>();
    private static final Set<SmartBlockEntity> clientAttached = Collections.newSetFromMap(new WeakHashMap<>());

    private CreateComputerBridge() {
    }

    static synchronized void addEnvironment(final SmartBlockEntity blockEntity, final CreateEnvironment<?> environment) {
        environments.computeIfAbsent(blockEntity, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(environment);
    }

    static synchronized void removeEnvironment(final SmartBlockEntity blockEntity, final CreateEnvironment<?> environment) {
        final Set<CreateEnvironment<?>> values = environments.get(blockEntity);
        if (values == null)
            return;
        values.remove(environment);
        if (values.isEmpty())
            environments.remove(blockEntity);
        updateBlockEntity(blockEntity);
    }

    static synchronized void setAttached(final SmartBlockEntity blockEntity, final CreateEnvironment<?> environment, final boolean attached) {
        if (attached)
            addEnvironment(blockEntity, environment);
        updateBlockEntity(blockEntity);
    }

    public static synchronized boolean isAttached(final SmartBlockEntity blockEntity) {
        if (blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide)
            return clientAttached.contains(blockEntity);
        final Set<CreateEnvironment<?>> values = environments.get(blockEntity);
        return values != null && values.stream().anyMatch(CreateEnvironment::hasAttachedComputer);
    }

    public static synchronized void setClientAttached(final SmartBlockEntity blockEntity, final boolean attached) {
        if (attached)
            clientAttached.add(blockEntity);
        else
            clientAttached.remove(blockEntity);
    }

    public static synchronized void forwardEvent(final SmartBlockEntity blockEntity, final ComputerEvent event) {
        final Set<CreateEnvironment<?>> values = environments.get(blockEntity);
        if (values == null)
            return;
        for (final CreateEnvironment<?> environment : Set.copyOf(values))
            forwardEvent(environment, event);
    }

    private static void forwardEvent(final CreateEnvironment<?> environment, final ComputerEvent event) {
        if (event instanceof KineticsChangeEvent change) {
            if (environment instanceof CreateKineticEnvironments.SpeedGauge)
                environment.queueCreateEvent("speed_change", change.overStressed ? 0 : change.speed);
            else if (environment instanceof CreateKineticEnvironments.StressGauge) {
                if (change.overStressed)
                    environment.queueCreateEvent("overstressed");
                else
                    environment.queueCreateEvent("stress_change", change.stress, change.capacity);
            }
        } else if (event instanceof PackageEvent packageEvent) {
            environment.queueCreateEvent(packageEvent.status, packageValue(environment, packageEvent.box));
        } else if (event instanceof RepackageEvent repackageEvent) {
            environment.queueCreateEvent("package_repackaged", packageValue(environment, repackageEvent.box), repackageEvent.count);
        } else if (event instanceof SignalStateChangeEvent signalEvent) {
            environment.queueCreateEvent("train_signal_state_change", signalEvent.state.toString());
        } else if (event instanceof StationTrainPresenceEvent stationEvent) {
            environment.queueCreateEvent(stationEvent.type.name, stationEvent.train.name.getString());
        } else if (event instanceof TrainPassEvent trainEvent) {
            environment.queueCreateEvent(trainEvent.passing ? "train_passing" : "train_passed",
                    trainEvent.train.name.getString());
        }
    }

    private static CreatePackageValue packageValue(final CreateEnvironment<?> environment,
                                                   final net.minecraft.world.item.ItemStack box) {
        final PackagerBlockEntity owner = environment.createBlockEntity() instanceof PackagerBlockEntity packager
                ? packager : null;
        return new CreatePackageValue(owner, box);
    }

    private static void updateBlockEntity(final SmartBlockEntity blockEntity) {
        blockEntity.setChanged();
        blockEntity.notifyUpdate();
    }
}
