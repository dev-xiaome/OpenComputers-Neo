package net.minecraft.network.protocol.login;

import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.cookie.ClientCookiePacketListener;

/**
 * PacketListener for the client side of the LOGIN protocol.
 */
public interface ClientLoginPacketListener extends ClientCookiePacketListener, ClientboundPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.LOGIN;
    }

    void handleHello(ClientboundHelloPacket packet);

    void handleGameProfile(ClientboundGameProfilePacket packet);

    void handleDisconnect(ClientboundLoginDisconnectPacket packet);

    void handleCompression(ClientboundLoginCompressionPacket packet);

    void handleCustomQuery(ClientboundCustomQueryPacket packet);
}
