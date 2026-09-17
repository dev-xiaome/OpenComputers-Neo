package net.minecraft.network;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.Packet;

public interface PacketSendListener {
    static PacketSendListener thenRun(final Runnable onSuccessOrFailure) {
        return new PacketSendListener() {
            @Override
            public void onSuccess() {
                onSuccessOrFailure.run();
            }

            @Nullable
            @Override
            public Packet<?> onFailure() {
                onSuccessOrFailure.run();
                return null;
            }
        };
    }

    static PacketSendListener exceptionallySend(final Supplier<Packet<?>> exceptionalPacketSupplier) {
        return new PacketSendListener() {
            @Nullable
            @Override
            public Packet<?> onFailure() {
                return exceptionalPacketSupplier.get();
            }
        };
    }

    default void onSuccess() {
    }

    @Nullable
    default Packet<?> onFailure() {
        return null;
    }
}
