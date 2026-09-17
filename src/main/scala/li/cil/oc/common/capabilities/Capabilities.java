package li.cil.oc.common.capabilities;

import li.cil.oc.api.audio.AudioReceiver;
import li.cil.oc.api.internal.Colored;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.SidedEnvironment;
import net.neoforged.neoforge.capabilities.Capability;
import net.neoforged.neoforge.capabilities.CapabilityManager;
import net.neoforged.neoforge.capabilities.CapabilityToken;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.SubscribeEvent;

public final class Capabilities {
    public static Capability<Colored> ColoredCapability = CapabilityManager.get(new CapabilityToken<>(){});

    public static Capability<Environment> EnvironmentCapability = CapabilityManager.get(new CapabilityToken<>(){});

    public static Capability<SidedEnvironment> SidedEnvironmentCapability = CapabilityManager.get(new CapabilityToken<>(){});

    public static Capability<AudioReceiver> AudioReceiverCapability = CapabilityManager.get(new CapabilityToken<>(){});
    
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(Colored.class);
        event.register(Environment.class);
        event.register(SidedEnvironment.class);
        event.register(AudioReceiver.class);
    }

    private Capabilities() {
    }
}
