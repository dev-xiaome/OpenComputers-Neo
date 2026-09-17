package net.minecraft.network;

import com.google.common.base.Suppliers;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.network.protocol.handshake.ServerHandshakePacketListener;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.StatusProtocols;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class Connection extends SimpleChannelInboundHandler<Packet<?>> {
    private static final float AVERAGE_PACKETS_SMOOTHING = 0.75F;
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Marker ROOT_MARKER = MarkerFactory.getMarker("NETWORK");
    public static final Marker PACKET_MARKER = Util.make(MarkerFactory.getMarker("NETWORK_PACKETS"), p_202569_ -> p_202569_.add(ROOT_MARKER));
    public static final Marker PACKET_RECEIVED_MARKER = Util.make(MarkerFactory.getMarker("PACKET_RECEIVED"), p_202562_ -> p_202562_.add(PACKET_MARKER));
    public static final Marker PACKET_SENT_MARKER = Util.make(MarkerFactory.getMarker("PACKET_SENT"), p_202557_ -> p_202557_.add(PACKET_MARKER));
    public static final Supplier<NioEventLoopGroup> NETWORK_WORKER_GROUP = Suppliers.memoize(
        () -> new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Client IO #%d").setDaemon(true).build())
    );
    public static final Supplier<EpollEventLoopGroup> NETWORK_EPOLL_WORKER_GROUP = Suppliers.memoize(
        () -> new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build())
    );
    public static final Supplier<DefaultEventLoopGroup> LOCAL_WORKER_GROUP = Suppliers.memoize(
        () -> new DefaultEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Local Client IO #%d").setDaemon(true).build())
    );
    private static final ProtocolInfo<ServerHandshakePacketListener> INITIAL_PROTOCOL = HandshakeProtocols.SERVERBOUND;
    private final PacketFlow receiving;
    private volatile boolean sendLoginDisconnect = true;
    private final Queue<Consumer<Connection>> pendingActions = Queues.newConcurrentLinkedQueue();
    /**
     * The active channel
     */
    private Channel channel;
    /**
     * The address of the remote party
     */
    private SocketAddress address;
    @Nullable
    private volatile PacketListener disconnectListener;
    /**
     * The PacketListener instance responsible for processing received packets
     */
    @Nullable
    private volatile PacketListener packetListener;
    @Nullable
    private DisconnectionDetails disconnectionDetails;
    private boolean encrypted;
    private boolean disconnectionHandled;
    private int receivedPackets;
    private int sentPackets;
    private float averageReceivedPackets;
    private float averageSentPackets;
    private int tickCount;
    private boolean handlingFault;
    @Nullable
    private volatile DisconnectionDetails delayedDisconnect;
    @Nullable
    BandwidthDebugMonitor bandwidthDebugMonitor;
    @Nullable
    private ProtocolInfo<?> inboundProtocol;

    public Connection(PacketFlow receiving) {
        this.receiving = receiving;
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        super.channelActive(context);
        this.channel = context.channel();
        this.address = this.channel.remoteAddress();
        if (this.delayedDisconnect != null) {
            this.disconnect(this.delayedDisconnect);
        }
        net.neoforged.neoforge.network.connection.ConnectionUtils.setConnection(context, this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) {
        this.disconnect(Component.translatable("disconnect.endOfStream"));
        net.neoforged.neoforge.network.connection.ConnectionUtils.removeConnection(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable exception) {
        if (exception instanceof SkipPacketException) {
            LOGGER.debug("Skipping packet due to errors", exception.getCause());
        } else {
            boolean flag = !this.handlingFault;
            this.handlingFault = true;
            if (this.channel.isOpen()) {
                if (exception instanceof TimeoutException) {
                    LOGGER.debug("Timeout", exception);
                    this.disconnect(Component.translatable("disconnect.timeout"));
                } else {
                    Component component = Component.translatable("disconnect.genericReason", "Internal Exception: " + exception);
                    PacketListener packetlistener = this.packetListener;
                    if (packetlistener != null) {
                        ConnectionProtocol protocol = packetlistener.protocol();
                        if (protocol == ConnectionProtocol.CONFIGURATION || protocol == ConnectionProtocol.PLAY) {
                            // Neo: Always log critical network exceptions for config and play packets
                            LOGGER.error("Exception caught in connection", exception);
                        }
                    }
                    DisconnectionDetails disconnectiondetails;
                    if (packetlistener != null) {
                        disconnectiondetails = packetlistener.createDisconnectionInfo(component, exception);
                    } else {
                        disconnectiondetails = new DisconnectionDetails(component);
                    }

                    if (flag) {
                        LOGGER.debug("Failed to sent packet", exception);
                        if (this.getSending() == PacketFlow.CLIENTBOUND) {
                            Packet<?> packet = (Packet<?>)(this.sendLoginDisconnect
                                ? new ClientboundLoginDisconnectPacket(component)
                                : new ClientboundDisconnectPacket(component));
                            this.send(packet, PacketSendListener.thenRun(() -> this.disconnect(disconnectiondetails)));
                        } else {
                            this.disconnect(disconnectiondetails);
                        }

                        this.setReadOnly();
                    } else {
                        LOGGER.debug("Double fault", exception);
                        this.disconnect(disconnectiondetails);
                    }
                }
            }
        }
    }

    protected void channelRead0(ChannelHandlerContext context, Packet<?> packet) {
        if (this.channel.isOpen()) {
            PacketListener packetlistener = this.packetListener;
            if (packetlistener == null) {
                throw new IllegalStateException("Received a packet before the packet listener was initialized");
            } else {
                if (packetlistener.shouldHandleMessage(packet)) {
                    try {
                        genericsFtw(packet, packetlistener);
                    } catch (RunningOnDifferentThreadException runningondifferentthreadexception) {
                    } catch (RejectedExecutionException rejectedexecutionexception) {
                        this.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
                    } catch (ClassCastException classcastexception) {
                        LOGGER.error("Received {} that couldn't be processed", packet.getClass(), classcastexception);
                        this.disconnect(Component.translatable("multiplayer.disconnect.invalid_packet"));
                    }

                    this.receivedPackets++;
                }
            }
        }
    }

    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
        packet.handle((T)listener);
    }

    private void validateListener(ProtocolInfo<?> protocolInfo, PacketListener packetListener) {
        Validate.notNull(packetListener, "packetListener");
        PacketFlow packetflow = packetListener.flow();
        if (packetflow != this.receiving) {
            throw new IllegalStateException("Trying to set listener for wrong side: connection is " + this.receiving + ", but listener is " + packetflow);
        } else {
            ConnectionProtocol connectionprotocol = packetListener.protocol();
            if (protocolInfo.id() != connectionprotocol) {
                throw new IllegalStateException("Listener protocol (" + connectionprotocol + ") does not match requested one " + protocolInfo);
            }
        }
    }

    private static void syncAfterConfigurationChange(ChannelFuture future) {
        try {
            future.syncUninterruptibly();
        } catch (Exception exception) {
            if (exception instanceof ClosedChannelException) {
                LOGGER.info("Connection closed during protocol change");
            } else {
                throw exception;
            }
        }
    }

    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetInfo) {
        this.validateListener(protocolInfo, packetInfo);
        if (protocolInfo.flow() != this.getReceiving()) {
            throw new IllegalStateException("Invalid inbound protocol: " + protocolInfo.id());
        } else {
            this.inboundProtocol = protocolInfo;
            this.packetListener = packetInfo;
            this.disconnectListener = null;
            UnconfiguredPipelineHandler.InboundConfigurationTask unconfiguredpipelinehandler$inboundconfigurationtask = UnconfiguredPipelineHandler.setupInboundProtocol(
                protocolInfo
            );
            BundlerInfo bundlerinfo = protocolInfo.bundlerInfo();
            if (bundlerinfo != null) {
                PacketBundlePacker packetbundlepacker = new PacketBundlePacker(bundlerinfo);
                unconfiguredpipelinehandler$inboundconfigurationtask = unconfiguredpipelinehandler$inboundconfigurationtask.andThen(
                    p_319518_ -> p_319518_.pipeline().addAfter("decoder", "bundler", packetbundlepacker)
                );
            }

            syncAfterConfigurationChange(this.channel.writeAndFlush(unconfiguredpipelinehandler$inboundconfigurationtask));
        }
    }

    public void setupOutboundProtocol(ProtocolInfo<?> protocolInfo) {
        if (protocolInfo.flow() != this.getSending()) {
            throw new IllegalStateException("Invalid outbound protocol: " + protocolInfo.id());
        } else {
            UnconfiguredPipelineHandler.OutboundConfigurationTask unconfiguredpipelinehandler$outboundconfigurationtask = UnconfiguredPipelineHandler.setupOutboundProtocol(
                protocolInfo
            );
            BundlerInfo bundlerinfo = protocolInfo.bundlerInfo();
            if (bundlerinfo != null) {
                PacketBundleUnpacker packetbundleunpacker = new PacketBundleUnpacker(bundlerinfo);
                unconfiguredpipelinehandler$outboundconfigurationtask = unconfiguredpipelinehandler$outboundconfigurationtask.andThen(
                    p_319516_ -> {
                        p_319516_.pipeline().addAfter("encoder", "unbundler", packetbundleunpacker);
                        // Neo: our handlers must be between the encoder and the unbundler, so re-inject them
                        // Note, this call must be inside the .andThen lambda, or it will actually run before the unbundler gets added.
                        net.neoforged.neoforge.network.filters.NetworkFilters.injectIfNecessary(this);
                    }
                );
            }

            boolean flag = protocolInfo.id() == ConnectionProtocol.LOGIN;
            syncAfterConfigurationChange(
                this.channel.writeAndFlush(unconfiguredpipelinehandler$outboundconfigurationtask.andThen(p_319527_ -> this.sendLoginDisconnect = flag))
            );
        }
    }

    public void setListenerForServerboundHandshake(PacketListener packetListener) {
        if (this.packetListener != null) {
            throw new IllegalStateException("Listener already set");
        } else if (this.receiving == PacketFlow.SERVERBOUND && packetListener.flow() == PacketFlow.SERVERBOUND && packetListener.protocol() == INITIAL_PROTOCOL.id()) {
            this.packetListener = packetListener;
        } else {
            throw new IllegalStateException("Invalid initial listener");
        }
    }

    public void initiateServerboundStatusConnection(String hostName, int port, ClientStatusPacketListener packetListener) {
        this.initiateServerboundConnection(hostName, port, StatusProtocols.SERVERBOUND, StatusProtocols.CLIENTBOUND, packetListener, ClientIntent.STATUS);
    }

    public void initiateServerboundPlayConnection(String hostName, int port, ClientLoginPacketListener packetListener) {
        this.initiateServerboundConnection(hostName, port, LoginProtocols.SERVERBOUND, LoginProtocols.CLIENTBOUND, packetListener, ClientIntent.LOGIN);
    }

    public <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundPlayConnection(
        String hostName, int port, ProtocolInfo<S> serverboundProtocol, ProtocolInfo<C> clientbountProtocol, C packetListener, boolean isTransfer
    ) {
        this.initiateServerboundConnection(hostName, port, serverboundProtocol, clientbountProtocol, packetListener, isTransfer ? ClientIntent.TRANSFER : ClientIntent.LOGIN);
    }

    private <S extends ServerboundPacketListener, C extends ClientboundPacketListener> void initiateServerboundConnection(
        String hostName, int port, ProtocolInfo<S> serverboundProtocol, ProtocolInfo<C> clientboundProtocol, C packetListener, ClientIntent intention
    ) {
        if (serverboundProtocol.id() != clientboundProtocol.id()) {
            throw new IllegalStateException("Mismatched initial protocols");
        } else {
            this.disconnectListener = packetListener;
            this.runOnceConnected(
                p_319525_ -> {
                    this.setupInboundProtocol(clientboundProtocol, packetListener);
                    p_319525_.sendPacket(
                        new ClientIntentionPacket(SharedConstants.getCurrentVersion().getProtocolVersion(), hostName, port, intention), null, true
                    );
                    this.setupOutboundProtocol(serverboundProtocol);
                }
            );
        }
    }

    public void send(Packet<?> packet) {
        this.send(packet, null);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener sendListener) {
        this.send(packet, sendListener, true);
    }

    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
        if (this.isConnected()) {
            this.flushQueue();
            this.sendPacket(packet, listener, flush);
        } else {
            this.pendingActions.add(p_293706_ -> p_293706_.sendPacket(packet, listener, flush));
        }
    }

    public void runOnceConnected(Consumer<Connection> action) {
        if (this.isConnected()) {
            this.flushQueue();
            action.accept(this);
        } else {
            this.pendingActions.add(action);
        }
    }

    private void sendPacket(Packet<?> packet, @Nullable PacketSendListener sendListener, boolean flush) {
        this.sentPackets++;
        if (this.channel.eventLoop().inEventLoop()) {
            this.doSendPacket(packet, sendListener, flush);
        } else {
            this.channel.eventLoop().execute(() -> this.doSendPacket(packet, sendListener, flush));
        }
    }

    private void doSendPacket(Packet<?> p_packet, @Nullable PacketSendListener sendListener, boolean flush) {
        ChannelFuture channelfuture = flush ? this.channel.writeAndFlush(p_packet) : this.channel.write(p_packet);
        if (sendListener != null) {
            channelfuture.addListener(p_243167_ -> {
                if (p_243167_.isSuccess()) {
                    sendListener.onSuccess();
                } else {
                    Packet<?> packet = sendListener.onFailure();
                    if (packet != null) {
                        ChannelFuture channelfuture1 = this.channel.writeAndFlush(packet);
                        channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                    }
                }
            });
        }

        channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public void flushChannel() {
        if (this.isConnected()) {
            this.flush();
        } else {
            this.pendingActions.add(Connection::flush);
        }
    }

    private void flush() {
        if (this.channel.eventLoop().inEventLoop()) {
            this.channel.flush();
        } else {
            this.channel.eventLoop().execute(() -> this.channel.flush());
        }
    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            synchronized (this.pendingActions) {
                Consumer<Connection> consumer;
                while ((consumer = this.pendingActions.poll()) != null) {
                    consumer.accept(this);
                }
            }
        }
    }

    public void tick() {
        this.flushQueue();
        if (this.packetListener instanceof TickablePacketListener tickablepacketlistener) {
            tickablepacketlistener.tick();
        }

        if (!this.isConnected() && !this.disconnectionHandled) {
            this.handleDisconnection();
        }

        if (this.channel != null) {
            this.channel.flush();
        }

        if (this.tickCount++ % 20 == 0) {
            this.tickSecond();
        }

        if (this.bandwidthDebugMonitor != null) {
            this.bandwidthDebugMonitor.tick();
        }
    }

    protected void tickSecond() {
        this.averageSentPackets = Mth.lerp(0.75F, (float)this.sentPackets, this.averageSentPackets);
        this.averageReceivedPackets = Mth.lerp(0.75F, (float)this.receivedPackets, this.averageReceivedPackets);
        this.sentPackets = 0;
        this.receivedPackets = 0;
    }

    public SocketAddress getRemoteAddress() {
        return this.address;
    }

    public String getLoggableAddress(boolean logIps) {
        if (this.address == null) {
            return "local";
        } else {
            return logIps ? net.neoforged.neoforge.network.DualStackUtils.getAddressString(this.address) : "IP hidden";
        }
    }

    /**
     * Closes the channel with a given reason. The reason is stored for later and will be used for informational purposes (info log on server,
     * disconnection screen on the client). This method is also called on the client when the server requests disconnection via
     * {@code ClientboundDisconnectPacket}.
     *
     * Closing the channel this way does not send any disconnection packets, it simply terminates the underlying netty channel.
     */
    public void disconnect(Component message) {
        this.disconnect(new DisconnectionDetails(message));
    }

    public void disconnect(DisconnectionDetails disconnectionDetails) {
        if (this.channel == null) {
            this.delayedDisconnect = disconnectionDetails;
        }

        if (this.isConnected()) {
            this.channel.close().awaitUninterruptibly();
            this.disconnectionDetails = disconnectionDetails;
        }
    }

    public boolean isMemoryConnection() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public PacketFlow getReceiving() {
        return this.receiving;
    }

    public PacketFlow getSending() {
        return this.receiving.getOpposite();
    }

    public static Connection connectToServer(InetSocketAddress address, boolean useEpollIfAvailable, @Nullable LocalSampleLogger sampleLogger) {
        Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        if (sampleLogger != null) {
            connection.setBandwidthLogger(sampleLogger);
        }

        ChannelFuture channelfuture = connect(address, useEpollIfAvailable, connection);
        channelfuture.syncUninterruptibly();
        return connection;
    }

    public static ChannelFuture connect(InetSocketAddress address, boolean useEpollIfAvailable, final Connection connection) {
        net.neoforged.neoforge.network.DualStackUtils.checkIPv6(address.getAddress());
        Class<? extends SocketChannel> oclass;
        EventLoopGroup eventloopgroup;
        if (Epoll.isAvailable() && useEpollIfAvailable) {
            oclass = EpollSocketChannel.class;
            eventloopgroup = NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            oclass = NioSocketChannel.class;
            eventloopgroup = NETWORK_WORKER_GROUP.get();
        }

        return new Bootstrap().group(eventloopgroup).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                }

                ChannelPipeline channelpipeline = channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
                Connection.configureSerialization(channelpipeline, PacketFlow.CLIENTBOUND, false, connection.bandwidthDebugMonitor);
                connection.configurePacketHandler(channelpipeline);
            }
        }).channel(oclass).connect(address.getAddress(), address.getPort());
    }

    private static String outboundHandlerName(boolean clientbound) {
        return clientbound ? "encoder" : "outbound_config";
    }

    private static String inboundHandlerName(boolean serverbound) {
        return serverbound ? "decoder" : "inbound_config";
    }

    public void configurePacketHandler(ChannelPipeline pipeline) {
        pipeline.addLast("hackfix", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext p_320587_, Object p_320392_, ChannelPromise p_320515_) throws Exception {
                super.write(p_320587_, p_320392_, p_320515_);
            }
        }).addLast("packet_handler", this);
    }

    public static void configureSerialization(ChannelPipeline pipeline, PacketFlow flow, boolean memoryOnly, @Nullable BandwidthDebugMonitor bandwithDebugMonitor) {
        PacketFlow packetflow = flow.getOpposite();
        boolean flag = flow == PacketFlow.SERVERBOUND;
        boolean flag1 = packetflow == PacketFlow.SERVERBOUND;
        pipeline.addLast("splitter", createFrameDecoder(bandwithDebugMonitor, memoryOnly))
            .addLast(new FlowControlHandler())
            .addLast(inboundHandlerName(flag), (ChannelHandler)(flag ? new PacketDecoder<>(INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Inbound()))
            .addLast("prepender", createFrameEncoder(memoryOnly))
            .addLast(outboundHandlerName(flag1), (ChannelHandler)(flag1 ? new PacketEncoder<>(INITIAL_PROTOCOL) : new UnconfiguredPipelineHandler.Outbound()));
    }

    private static ChannelOutboundHandler createFrameEncoder(boolean memoryOnly) {
        return (ChannelOutboundHandler)(memoryOnly ? new NoOpFrameEncoder() : new Varint21LengthFieldPrepender());
    }

    private static ChannelInboundHandler createFrameDecoder(@Nullable BandwidthDebugMonitor bandwithDebugMonitor, boolean memoryOnly) {
        if (!memoryOnly) {
            return new Varint21FrameDecoder(bandwithDebugMonitor);
        } else {
            return (ChannelInboundHandler)(bandwithDebugMonitor != null ? new MonitorFrameDecoder(bandwithDebugMonitor) : new NoOpFrameDecoder());
        }
    }

    public static void configureInMemoryPipeline(ChannelPipeline pipeline, PacketFlow flow) {
        configureSerialization(pipeline, flow, true, null);
    }

    /**
     * Prepares a clientside Connection for a local in-memory connection ("single player").
     * Establishes a connection to the socket supplied and configures the channel pipeline (only the packet handler is necessary,
     * since this is for an in-memory connection). Returns the newly created instance.
     */
    public static Connection connectToLocalServer(SocketAddress address) {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        new Bootstrap().group(LOCAL_WORKER_GROUP.get()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                ChannelPipeline channelpipeline = channel.pipeline();
                Connection.configureInMemoryPipeline(channelpipeline, PacketFlow.CLIENTBOUND);
                connection.configurePacketHandler(channelpipeline);
            }
        }).channel(LocalChannel.class).connect(address).syncUninterruptibly();
        return connection;
    }

    /**
     * Enables encryption for this connection using the given decrypting and encrypting ciphers.
     * This adds new handlers to this connection's pipeline which handle the decrypting and encrypting.
     * This happens as part of the normal network handshake.
     *
     * @see net.minecraft.network.protocol.login.ClientboundHelloPacket
     * @see net.minecraft.network.protocol.login.ServerboundKeyPacket
     */
    public void setEncryptionKey(Cipher decryptingCipher, Cipher encryptingCipher) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new CipherDecoder(decryptingCipher));
        this.channel.pipeline().addBefore("prepender", "encrypt", new CipherEncoder(encryptingCipher));
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    @Nullable
    public PacketListener getPacketListener() {
        return this.packetListener;
    }

    @Nullable
    public DisconnectionDetails getDisconnectionDetails() {
        return this.disconnectionDetails;
    }

    public void setReadOnly() {
        if (this.channel != null) {
            this.channel.config().setAutoRead(false);
        }
    }

    /**
     * Enables or disables compression for this connection. If {@code threshold} is >= 0 then a {@link CompressionDecoder} and {@link CompressionEncoder}
     * are installed in the pipeline or updated if they already exist. If {@code threshold} is < 0 then any such codec are removed.
     *
     * Compression is enabled as part of the connection handshake when the server sends {@link net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket}.
     */
    public void setupCompression(int threshold, boolean validateDecompressed) {
        if (threshold >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder compressiondecoder) {
                compressiondecoder.setThreshold(threshold, validateDecompressed);
            } else {
                this.channel.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(threshold, validateDecompressed));
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder compressionencoder) {
                compressionencoder.setThreshold(threshold);
            } else {
                this.channel.pipeline().addAfter("prepender", "compress", new CompressionEncoder(threshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }
    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (this.disconnectionHandled) {
                LOGGER.warn("handleDisconnection() called twice");
            } else {
                this.disconnectionHandled = true;
                PacketListener packetlistener = this.getPacketListener();
                PacketListener packetlistener1 = packetlistener != null ? packetlistener : this.disconnectListener;
                if (packetlistener1 != null) {
                    DisconnectionDetails disconnectiondetails = Objects.requireNonNullElseGet(
                        this.getDisconnectionDetails(), () -> new DisconnectionDetails(Component.translatable("multiplayer.disconnect.generic"))
                    );
                    packetlistener1.onDisconnect(disconnectiondetails);
                }
            }
        }
    }

    public float getAverageReceivedPackets() {
        return this.averageReceivedPackets;
    }

    public float getAverageSentPackets() {
        return this.averageSentPackets;
    }

    public void setBandwidthLogger(LocalSampleLogger bandwithLogger) {
        this.bandwidthDebugMonitor = new BandwidthDebugMonitor(bandwithLogger);
    }

    public Channel channel() {
        return this.channel;
    }

    public PacketFlow getDirection() {
        return this.receiving;
    }

    public ProtocolInfo<?> getInboundProtocol() {
        return Objects.requireNonNull(this.inboundProtocol, "Inbound protocol not set?");
    }
}
