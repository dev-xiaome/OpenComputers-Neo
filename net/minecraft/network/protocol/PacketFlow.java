package net.minecraft.network.protocol;

/**
 * The direction of packets.
 */
public enum PacketFlow implements net.neoforged.neoforge.common.extensions.IPacketFlowExtension {
    SERVERBOUND("serverbound"),
    CLIENTBOUND("clientbound");

    private final String id;

    private PacketFlow(String id) {
        this.id = id;
    }

    public PacketFlow getOpposite() {
        return this == CLIENTBOUND ? SERVERBOUND : CLIENTBOUND;
    }

    public String id() {
        return this.id;
    }
}
