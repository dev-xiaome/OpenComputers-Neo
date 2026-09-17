package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.thread.ProcessorMailbox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ServerList {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ProcessorMailbox<Runnable> IO_MAILBOX = ProcessorMailbox.create(Util.backgroundExecutor(), "server-list-io");
    private static final int MAX_HIDDEN_SERVERS = 16;
    private final Minecraft minecraft;
    private final List<ServerData> serverList = Lists.newArrayList();
    private final List<ServerData> hiddenServerList = Lists.newArrayList();

    public ServerList(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void load() {
        try {
            this.serverList.clear();
            this.hiddenServerList.clear();
            CompoundTag compoundtag = NbtIo.read(this.minecraft.gameDirectory.toPath().resolve("servers.dat"));
            if (compoundtag == null) {
                return;
            }

            ListTag listtag = compoundtag.getList("servers", 10);

            for (int i = 0; i < listtag.size(); i++) {
                CompoundTag compoundtag1 = listtag.getCompound(i);
                ServerData serverdata = ServerData.read(compoundtag1);
                if (compoundtag1.getBoolean("hidden")) {
                    this.hiddenServerList.add(serverdata);
                } else {
                    this.serverList.add(serverdata);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Couldn't load server list", (Throwable)exception);
        }
    }

    public void save() {
        try {
            ListTag listtag = new ListTag();

            for (ServerData serverdata : this.serverList) {
                CompoundTag compoundtag = serverdata.write();
                compoundtag.putBoolean("hidden", false);
                listtag.add(compoundtag);
            }

            for (ServerData serverdata1 : this.hiddenServerList) {
                CompoundTag compoundtag2 = serverdata1.write();
                compoundtag2.putBoolean("hidden", true);
                listtag.add(compoundtag2);
            }

            CompoundTag compoundtag1 = new CompoundTag();
            compoundtag1.put("servers", listtag);
            Path path2 = this.minecraft.gameDirectory.toPath();
            Path path3 = Files.createTempFile(path2, "servers", ".dat");
            NbtIo.write(compoundtag1, path3);
            Path path = path2.resolve("servers.dat_old");
            Path path1 = path2.resolve("servers.dat");
            Util.safeReplaceFile(path1, path3, path);
        } catch (Exception exception) {
            LOGGER.error("Couldn't save server list", (Throwable)exception);
        }
    }

    /**
     * Gets the ServerData instance stored for the given index in the list.
     */
    public ServerData get(int index) {
        return this.serverList.get(index);
    }

    @Nullable
    public ServerData get(String ip) {
        for (ServerData serverdata : this.serverList) {
            if (serverdata.ip.equals(ip)) {
                return serverdata;
            }
        }

        for (ServerData serverdata1 : this.hiddenServerList) {
            if (serverdata1.ip.equals(ip)) {
                return serverdata1;
            }
        }

        return null;
    }

    @Nullable
    public ServerData unhide(String ip) {
        for (int i = 0; i < this.hiddenServerList.size(); i++) {
            ServerData serverdata = this.hiddenServerList.get(i);
            if (serverdata.ip.equals(ip)) {
                this.hiddenServerList.remove(i);
                this.serverList.add(serverdata);
                return serverdata;
            }
        }

        return null;
    }

    public void remove(ServerData serverData) {
        if (!this.serverList.remove(serverData)) {
            this.hiddenServerList.remove(serverData);
        }
    }

    public void add(ServerData server, boolean hidden) {
        if (hidden) {
            this.hiddenServerList.add(0, server);

            while (this.hiddenServerList.size() > 16) {
                this.hiddenServerList.remove(this.hiddenServerList.size() - 1);
            }
        } else {
            this.serverList.add(server);
        }
    }

    public int size() {
        return this.serverList.size();
    }

    /**
     * Takes two list indexes, and swaps their order around.
     */
    public void swap(int pos1, int pos2) {
        ServerData serverdata = this.get(pos1);
        this.serverList.set(pos1, this.get(pos2));
        this.serverList.set(pos2, serverdata);
        this.save();
    }

    public void replace(int index, ServerData server) {
        this.serverList.set(index, server);
    }

    private static boolean set(ServerData server, List<ServerData> serverList) {
        for (int i = 0; i < serverList.size(); i++) {
            ServerData serverdata = serverList.get(i);
            if (serverdata.name.equals(server.name) && serverdata.ip.equals(server.ip)) {
                serverList.set(i, server);
                return true;
            }
        }

        return false;
    }

    public static void saveSingleServer(ServerData server) {
        IO_MAILBOX.tell(() -> {
            ServerList serverlist = new ServerList(Minecraft.getInstance());
            serverlist.load();
            if (!set(server, serverlist.serverList)) {
                set(server, serverlist.hiddenServerList);
            }

            serverlist.save();
        });
    }
}
