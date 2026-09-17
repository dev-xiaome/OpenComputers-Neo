package net.minecraft.server.network;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;

public interface ConfigurationTask {
    void start(Consumer<Packet<?>> task);

    ConfigurationTask.Type type();

    public static record Type(String id) {
        public Type(net.minecraft.resources.ResourceLocation location) {
            this(location.toString());
        }

        @Override
        public String toString() {
            return this.id;
        }
    }
}
