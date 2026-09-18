package li.cil.oc.util;

import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforgespi.Environment;

public final class SideTracker {
    public static boolean isServer() {
        return Environment.get().getDist().isDedicatedServer() || EffectiveSide.get().isServer();
    }

    public static boolean isClient() {
        return !isServer();
    }
}
