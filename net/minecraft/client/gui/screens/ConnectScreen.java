package net.minecraft.client.gui.screens;

import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.client.quickplay.QuickPlay;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ConnectScreen extends Screen {
    private static final AtomicInteger UNIQUE_THREAD_ID = new AtomicInteger(0);
    static final Logger LOGGER = LogUtils.getLogger();
    private static final long NARRATION_DELAY_MS = 2000L;
    public static final Component ABORT_CONNECTION = Component.translatable("connect.aborted");
    public static final Component UNKNOWN_HOST_MESSAGE = Component.translatable("disconnect.genericReason", Component.translatable("disconnect.unknownHost"));
    @Nullable
    volatile Connection connection;
    @Nullable
    ChannelFuture channelFuture;
    volatile boolean aborted;
    final Screen parent;
    private Component status = Component.translatable("connect.connecting");
    private long lastNarration = -1L;
    final Component connectFailedTitle;

    private ConnectScreen(Screen parent, Component connectFailedTitle) {
        super(GameNarrator.NO_TITLE);
        this.parent = parent;
        this.connectFailedTitle = connectFailedTitle;
    }

    public static void startConnecting(
        Screen parent, Minecraft minecraft, ServerAddress serverAddress, ServerData serverData, boolean isQuickPlay, @Nullable TransferState transferState
    ) {
        if (minecraft.screen instanceof ConnectScreen) {
            LOGGER.error("Attempt to connect while already connecting");
        } else {
            Component component;
            if (transferState != null) {
                component = CommonComponents.TRANSFER_CONNECT_FAILED;
            } else if (isQuickPlay) {
                component = QuickPlay.ERROR_TITLE;
            } else {
                component = CommonComponents.CONNECT_FAILED;
            }

            ConnectScreen connectscreen = new ConnectScreen(parent, component);
            if (transferState != null) {
                connectscreen.updateStatus(Component.translatable("connect.transferring"));
            }

            minecraft.disconnect();
            minecraft.prepareForMultiplayer();
            minecraft.updateReportEnvironment(ReportEnvironment.thirdParty(serverData.ip));
            minecraft.quickPlayLog().setWorldData(QuickPlayLog.Type.MULTIPLAYER, serverData.ip, serverData.name);
            minecraft.setScreen(connectscreen);
            connectscreen.connect(minecraft, serverAddress, serverData, transferState);
        }
    }

    private void connect(final Minecraft minecraft, final ServerAddress serverAddress, final ServerData serverData, @Nullable final TransferState transferState) {
        LOGGER.info("Connecting to {}, {}", serverAddress.getHost(), serverAddress.getPort());
        Thread thread = new Thread("Server Connector #" + UNIQUE_THREAD_ID.incrementAndGet()) {
            @Override
            public void run() {
                InetSocketAddress inetsocketaddress = null;

                try {
                    if (ConnectScreen.this.aborted) {
                        return;
                    }

                    Optional<InetSocketAddress> optional = ServerNameResolver.DEFAULT.resolveAddress(serverAddress).map(ResolvedServerAddress::asInetSocketAddress);
                    if (ConnectScreen.this.aborted) {
                        return;
                    }

                    if (optional.isEmpty()) {
                        ConnectScreen.LOGGER.error("Couldn't connect to server: Unknown host \"{}\"", serverAddress.getHost());
                        net.neoforged.neoforge.network.DualStackUtils.logInitialPreferences();
                        minecraft.execute(
                            () -> minecraft.setScreen(
                                    new DisconnectedScreen(ConnectScreen.this.parent, ConnectScreen.this.connectFailedTitle, ConnectScreen.UNKNOWN_HOST_MESSAGE)
                                )
                        );
                        return;
                    }

                    inetsocketaddress = optional.get();
                    Connection connection;
                    synchronized (ConnectScreen.this) {
                        if (ConnectScreen.this.aborted) {
                            return;
                        }

                        connection = new Connection(PacketFlow.CLIENTBOUND);
                        connection.setBandwidthLogger(minecraft.getDebugOverlay().getBandwidthLogger());
                        ConnectScreen.this.channelFuture = Connection.connect(inetsocketaddress, minecraft.options.useNativeTransport(), connection);
                    }

                    ConnectScreen.this.channelFuture.syncUninterruptibly();
                    synchronized (ConnectScreen.this) {
                        if (ConnectScreen.this.aborted) {
                            connection.disconnect(ConnectScreen.ABORT_CONNECTION);
                            return;
                        }

                        ConnectScreen.this.connection = connection;
                        minecraft.getDownloadedPackSource().configureForServerControl(connection, convertPackStatus(serverData.getResourcePackStatus()));
                    }

                    ConnectScreen.this.connection
                        .initiateServerboundPlayConnection(
                            inetsocketaddress.getHostName(),
                            inetsocketaddress.getPort(),
                            LoginProtocols.SERVERBOUND,
                            LoginProtocols.CLIENTBOUND,
                            new ClientHandshakePacketListenerImpl(
                                ConnectScreen.this.connection,
                                minecraft,
                                serverData,
                                ConnectScreen.this.parent,
                                false,
                                null,
                                ConnectScreen.this::updateStatus,
                                transferState
                            ),
                            transferState != null
                        );
                    ConnectScreen.this.connection.send(new ServerboundHelloPacket(minecraft.getUser().getName(), minecraft.getUser().getProfileId()));
                } catch (Exception exception2) {
                    if (ConnectScreen.this.aborted) {
                        return;
                    }

                    Exception exception;
                    if (exception2.getCause() instanceof Exception exception1) {
                        exception = exception1;
                    } else {
                        exception = exception2;
                    }

                    ConnectScreen.LOGGER.error("Couldn't connect to server", (Throwable)exception2);
                    String s = inetsocketaddress == null
                        ? exception.getMessage()
                        : exception.getMessage()
                            .replaceAll(inetsocketaddress.getHostName() + ":" + inetsocketaddress.getPort(), "")
                            .replaceAll(inetsocketaddress.toString(), "");
                    minecraft.execute(
                        () -> minecraft.setScreen(
                                new DisconnectedScreen(
                                    ConnectScreen.this.parent, ConnectScreen.this.connectFailedTitle, Component.translatable("disconnect.genericReason", s)
                                )
                            )
                    );
                }
            }

            private static ServerPackManager.PackPromptStatus convertPackStatus(ServerData.ServerPackStatus packStatus) {
                return switch (packStatus) {
                    case ENABLED -> ServerPackManager.PackPromptStatus.ALLOWED;
                    case DISABLED -> ServerPackManager.PackPromptStatus.DECLINED;
                    case PROMPT -> ServerPackManager.PackPromptStatus.PENDING;
                };
            }
        };
        thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(LOGGER));
        thread.start();
    }

    private void updateStatus(Component status) {
        this.status = status;
    }

    @Override
    public void tick() {
        if (this.connection != null) {
            if (this.connection.isConnected()) {
                this.connection.tick();
            } else {
                this.connection.handleDisconnection();
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, p_289624_ -> {
            synchronized (this) {
                this.aborted = true;
                if (this.channelFuture != null) {
                    this.channelFuture.cancel(true);
                    this.channelFuture = null;
                }

                if (this.connection != null) {
                    this.connection.disconnect(ABORT_CONNECTION);
                }
            }

            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 100, this.height / 4 + 120 + 12, 200, 20).build());
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        long i = Util.getMillis();
        if (i - this.lastNarration > 2000L) {
            this.lastNarration = i;
            this.minecraft.getNarrator().sayNow(Component.translatable("narrator.joining"));
        }

        guiGraphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 - 50, 16777215);
    }
}
