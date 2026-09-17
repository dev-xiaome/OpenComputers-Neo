package net.minecraft.network.protocol.login;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.cookie.ServerCookiePacketListener;
import net.minecraft.network.protocol.game.ServerPacketListener;

/**
 * PacketListener for the server side of the LOGIN protocol.
 */
public interface ServerLoginPacketListener extends ServerCookiePacketListener, ServerPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.LOGIN;
    }

    void handleHello(ServerboundHelloPacket packet);

    void handleKey(ServerboundKeyPacket packet);

    void handleCustomQueryPacket(ServerboundCustomQueryAnswerPacket packet);

    void handleLoginAcknowledgement(ServerboundLoginAcknowledgedPacket packet);
}
