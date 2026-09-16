package li.cil.oc.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;

import java.util.Objects;

public final class ClientAccessHelper {
    public static RegistryAccess getClientRegistryAccess() {
        return Objects.requireNonNull(Minecraft.getInstance().level, "cannot get client registry provider as level is not yet initialized").registryAccess();
    }
    
    private ClientAccessHelper() {}
}
