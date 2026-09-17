package net.minecraft.network;

/**
 * Describes the set of packets a connection understands at a given point.
 * A connection always starts out in state {@link #HANDSHAKING}. In this state the client sends its desired protocol using
 * {@link ClientIntentionPacket}. The server then either accepts the connection and switches to the desired protocol or it disconnects the client (for example in case of an outdated client).
 *
 * Each protocol has a {@link PacketListener} implementation tied to it for server and client respectively.
 *
 * Every packet must correspond to exactly one protocol.
 */
public enum ConnectionProtocol {
    /**
     * The handshake protocol. This is the initial protocol, in which the client tells the server its intention (i.e. which protocol it wants to use).
     */
    HANDSHAKING("handshake"),
    /**
     * The play protocol. This is the main protocol that is used while "in game" and most normal packets reside in here.
     */
    PLAY("play"),
    /**
     * The status protocol. This protocol is used when a client pings a server while on the multiplayer screen.
     */
    STATUS("status"),
    /**
     * The login protocol. This is the first protocol the client switches to to join a server. It handles authentication with the mojang servers. After it is complete, the connection is switched to the PLAY protocol.
     */
    LOGIN("login"),
    CONFIGURATION("configuration");

    private final String id;

    private ConnectionProtocol(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public boolean isPlay() {
        return this == PLAY;
    }
    public boolean isConfiguration() {
        return this == CONFIGURATION;
    }
}
