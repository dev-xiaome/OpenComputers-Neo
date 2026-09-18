package li.cil.oc.common.capabilities;

import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.SidedEnvironment;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public final class Capabilities {
    // These are defined in the Scala Capabilities object, but we keep Java stubs
    // for backwards compatibility with Java callers.
    // The actual declarations live in li.cil.oc.common.Capabilities (Scala object).

    public static BlockCapability<Colored, Direction> ColoredCapability() {
        return li.cil.oc.common.Capabilities$.MODULE$.ColoredCapability();
    }

    public static BlockCapability<Environment, Direction> EnvironmentCapability() {
        return li.cil.oc.common.Capabilities$.MODULE$.EnvironmentCapability();
    }

    public static BlockCapability<SidedEnvironment, Direction> SidedEnvironmentCapability() {
        return li.cil.oc.common.Capabilities$.MODULE$.SidedEnvironmentCapability();
    }

    private Capabilities() {
    }
}
