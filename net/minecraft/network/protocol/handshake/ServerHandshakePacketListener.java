package net.minecraft.network.protocol.handshake;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.game.ServerPacketListener;

/**
 * PacketListener for the server side of the HANDSHAKING protocol.
 */
public interface ServerHandshakePacketListener extends ServerPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.HANDSHAKING;
    }

    /**
     * There are two recognized intentions for initiating a handshake: logging in and acquiring server status. The NetworkManager's protocol will be reconfigured according to the specified intention, although a login-intention must pass a versioncheck or receive a disconnect otherwise
     */
    void handleIntention(ClientIntentionPacket packet);
}
