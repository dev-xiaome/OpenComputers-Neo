package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.TickRateManager;

public record ClientboundTickingStepPacket(int tickSteps) implements Packet<ClientGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundTickingStepPacket> STREAM_CODEC = Packet.codec(
        ClientboundTickingStepPacket::write, ClientboundTickingStepPacket::new
    );

    private ClientboundTickingStepPacket(FriendlyByteBuf p_309129_) {
        this(p_309129_.readVarInt());
    }

    public static ClientboundTickingStepPacket from(TickRateManager tickRateManager) {
        return new ClientboundTickingStepPacket(tickRateManager.frozenTicksToRun());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.tickSteps);
    }

    @Override
    public PacketType<ClientboundTickingStepPacket> type() {
        return GamePacketTypes.CLIENTBOUND_TICKING_STEP;
    }

    /**
     * Passes this Packet on to the PacketListener for processing.
     */
    public void handle(ClientGamePacketListener handler) {
        handler.handleTickingStep(this);
    }
}
