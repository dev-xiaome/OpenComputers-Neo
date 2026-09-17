package net.minecraft.server.rcon.thread;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.ServerInterface;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.slf4j.Logger;

public class RconThread extends GenericThread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerSocket socket;
    private final String rconPassword;
    private final List<RconClient> clients = Lists.newArrayList();
    private final ServerInterface serverInterface;

    private RconThread(ServerInterface serverInterface, ServerSocket socket, String rconPassword) {
        super("RCON Listener");
        this.serverInterface = serverInterface;
        this.socket = socket;
        this.rconPassword = rconPassword;
    }

    private void clearClients() {
        this.clients.removeIf(p_11612_ -> !p_11612_.isRunning());
    }

    @Override
    public void run() {
        try {
            while (this.running) {
                try {
                    Socket socket = this.socket.accept();
                    RconClient rconclient = new RconClient(this.serverInterface, this.rconPassword, socket);
                    rconclient.start();
                    this.clients.add(rconclient);
                    this.clearClients();
                } catch (SocketTimeoutException sockettimeoutexception) {
                    this.clearClients();
                } catch (IOException ioexception) {
                    if (this.running) {
                        LOGGER.info("IO exception: ", (Throwable)ioexception);
                    }
                }
            }
        } finally {
            this.closeSocket(this.socket);
        }
    }

    @Nullable
    public static RconThread create(ServerInterface serverInterface) {
        DedicatedServerProperties dedicatedserverproperties = serverInterface.getProperties();
        String s = serverInterface.getServerIp();
        if (s.isEmpty()) {
            s = "0.0.0.0";
        }

        int i = dedicatedserverproperties.rconPort;
        if (0 < i && 65535 >= i) {
            String s1 = dedicatedserverproperties.rconPassword;
            if (s1.isEmpty()) {
                LOGGER.warn("No rcon password set in server.properties, rcon disabled!");
                return null;
            } else {
                try {
                    ServerSocket serversocket = new ServerSocket(i, 0, InetAddress.getByName(s));
                    serversocket.setSoTimeout(500);
                    RconThread rconthread = new RconThread(serverInterface, serversocket, s1);
                    if (!rconthread.start()) {
                        return null;
                    } else {
                        LOGGER.info("RCON running on {}:{}", s, i);
                        return rconthread;
                    }
                } catch (IOException ioexception) {
                    LOGGER.warn("Unable to initialise RCON on {}:{}", s, i, ioexception);
                    return null;
                }
            }
        } else {
            LOGGER.warn("Invalid rcon port {} found in server.properties, rcon disabled!", i);
            return null;
        }
    }

    @Override
    public void stop() {
        this.running = false;
        this.closeSocket(this.socket);
        super.stop();

        for (RconClient rconclient : this.clients) {
            if (rconclient.isRunning()) {
                rconclient.stop();
            }
        }

        this.clients.clear();
    }

    private void closeSocket(ServerSocket socket) {
        LOGGER.debug("closeSocket: {}", socket);

        try {
            socket.close();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to close socket", (Throwable)ioexception);
        }
    }
}
