package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ServerStatusPinger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component CANT_CONNECT_MESSAGE = Component.translatable("multiplayer.status.cannot_connect").withColor(-65536);
    /**
     * A list of NetworkManagers that have pending pings
     */
    private final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());

    public void pingServer(final ServerData serverData, final Runnable serverListUpdater, final Runnable stateUpdater) throws UnknownHostException {
        final ServerAddress serveraddress = ServerAddress.parseString(serverData.ip);
        Optional<InetSocketAddress> optional = ServerNameResolver.DEFAULT.resolveAddress(serveraddress).map(ResolvedServerAddress::asInetSocketAddress);
        if (optional.isEmpty()) {
            this.onPingFailed(ConnectScreen.UNKNOWN_HOST_MESSAGE, serverData);
        } else {
            final InetSocketAddress inetsocketaddress = optional.get();
            final Connection connection = Connection.connectToServer(inetsocketaddress, false, null);
            this.connections.add(connection);
            serverData.motd = Component.translatable("multiplayer.status.pinging");
            serverData.playerList = Collections.emptyList();
            ClientStatusPacketListener clientstatuspacketlistener = new ClientStatusPacketListener() {
                private boolean success;
                private boolean receivedPing;
                private long pingStart;

                @Override
                public void handleStatusResponse(ClientboundStatusResponsePacket packet) {
                    if (this.receivedPing) {
                        connection.disconnect(Component.translatable("multiplayer.status.unrequested"));
                    } else {
                        this.receivedPing = true;
                        ServerStatus serverstatus = packet.status();
                        serverData.motd = serverstatus.description();
                        serverstatus.version().ifPresentOrElse(p_273307_ -> {
                            serverData.version = Component.literal(p_273307_.name());
                            serverData.protocol = p_273307_.protocol();
                        }, () -> {
                            serverData.version = Component.translatable("multiplayer.status.old");
                            serverData.protocol = 0;
                        });
                        serverstatus.players().ifPresentOrElse(p_273230_ -> {
                            serverData.status = ServerStatusPinger.formatPlayerCount(p_273230_.online(), p_273230_.max());
                            serverData.players = p_273230_;
                            if (!p_273230_.sample().isEmpty()) {
                                List<Component> list = new ArrayList<>(p_273230_.sample().size());

                                for (GameProfile gameprofile : p_273230_.sample()) {
                                    list.add(Component.literal(gameprofile.getName()));
                                }

                                if (p_273230_.sample().size() < p_273230_.online()) {
                                    list.add(Component.translatable("multiplayer.status.and_more", p_273230_.online() - p_273230_.sample().size()));
                                }

                                serverData.playerList = list;
                            } else {
                                serverData.playerList = List.of();
                            }
                        }, () -> serverData.status = Component.translatable("multiplayer.status.unknown").withStyle(ChatFormatting.DARK_GRAY));
                        serverstatus.favicon().ifPresent(p_302312_ -> {
                            if (!Arrays.equals(p_302312_.iconBytes(), serverData.getIconBytes())) {
                                serverData.setIconBytes(ServerData.validateIcon(p_302312_.iconBytes()));
                                serverListUpdater.run();
                            }
                        });
                        this.pingStart = Util.getMillis();
                        connection.send(new ServerboundPingRequestPacket(this.pingStart));
                        this.success = true;
                    }
                }

                @Override
                public void handlePongResponse(ClientboundPongResponsePacket packet) {
                    long i = this.pingStart;
                    long j = Util.getMillis();
                    serverData.ping = j - i;
                    connection.disconnect(Component.translatable("multiplayer.status.finished"));
                    stateUpdater.run();
                }

                @Override
                public void onDisconnect(DisconnectionDetails details) {
                    if (!this.success) {
                        ServerStatusPinger.this.onPingFailed(details.reason(), serverData);
                        ServerStatusPinger.this.pingLegacyServer(inetsocketaddress, serveraddress, serverData);
                    }
                }

                @Override
                public boolean isAcceptingMessages() {
                    return connection.isConnected();
                }
            };

            try {
                connection.initiateServerboundStatusConnection(serveraddress.getHost(), serveraddress.getPort(), clientstatuspacketlistener);
                connection.send(ServerboundStatusRequestPacket.INSTANCE);
            } catch (Throwable throwable) {
                LOGGER.error("Failed to ping server {}", serveraddress, throwable);
            }
        }
    }

    void onPingFailed(Component reason, ServerData serverData) {
        LOGGER.error("Can't ping {}: {}", serverData.ip, reason.getString());
        serverData.motd = CANT_CONNECT_MESSAGE;
        serverData.status = CommonComponents.EMPTY;
    }

    void pingLegacyServer(InetSocketAddress resolvedServerAddress, final ServerAddress serverAddress, final ServerData serverData) {
        new Bootstrap().group(Connection.NETWORK_WORKER_GROUP.get()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                }

                channel.pipeline().addLast(new LegacyServerPinger(serverAddress, (p_315832_, p_315833_, p_315834_, p_315835_, p_315836_) -> {
                    serverData.setState(ServerData.State.INCOMPATIBLE);
                    serverData.version = Component.literal(p_315833_);
                    serverData.motd = Component.literal(p_315834_);
                    serverData.status = ServerStatusPinger.formatPlayerCount(p_315835_, p_315836_);
                    serverData.players = new ServerStatus.Players(p_315836_, p_315835_, List.of());
                }));
            }
        }).channel(NioSocketChannel.class).connect(resolvedServerAddress.getAddress(), resolvedServerAddress.getPort());
    }

    public static Component formatPlayerCount(int players, int capacity) {
        Component component = Component.literal(Integer.toString(players)).withStyle(ChatFormatting.GRAY);
        Component component1 = Component.literal(Integer.toString(capacity)).withStyle(ChatFormatting.GRAY);
        return Component.translatable("multiplayer.status.player_count", component, component1).withStyle(ChatFormatting.DARK_GRAY);
    }

    public void tick() {
        synchronized (this.connections) {
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.isConnected()) {
                    connection.tick();
                } else {
                    iterator.remove();
                    connection.handleDisconnection();
                }
            }
        }
    }

    public void removeAll() {
        synchronized (this.connections) {
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.isConnected()) {
                    iterator.remove();
                    connection.disconnect(Component.translatable("multiplayer.status.cancelled"));
                }
            }
        }
    }
}
