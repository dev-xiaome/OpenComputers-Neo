package net.minecraft.server.dedicated;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.slf4j.Logger;

public class DedicatedPlayerList extends PlayerList {
    private static final Logger LOGGER = LogUtils.getLogger();

    public DedicatedPlayerList(DedicatedServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo) {
        super(server, registries, playerIo, server.getProperties().maxPlayers);
        DedicatedServerProperties dedicatedserverproperties = server.getProperties();
        this.setViewDistance(dedicatedserverproperties.viewDistance);
        this.setSimulationDistance(dedicatedserverproperties.simulationDistance);
        super.setUsingWhiteList(dedicatedserverproperties.whiteList.get());
        this.loadUserBanList();
        this.saveUserBanList();
        this.loadIpBanList();
        this.saveIpBanList();
        this.loadOps();
        this.loadWhiteList();
        this.saveOps();
        if (!this.getWhiteList().getFile().exists()) {
            this.saveWhiteList();
        }
    }

    @Override
    public void setUsingWhiteList(boolean whitelistEnabled) {
        super.setUsingWhiteList(whitelistEnabled);
        this.getServer().storeUsingWhiteList(whitelistEnabled);
    }

    @Override
    public void op(GameProfile profile) {
        super.op(profile);
        this.saveOps();
    }

    @Override
    public void deop(GameProfile profile) {
        super.deop(profile);
        this.saveOps();
    }

    @Override
    public void reloadWhiteList() {
        this.loadWhiteList();
    }

    private void saveIpBanList() {
        try {
            this.getIpBans().save();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to save ip banlist: ", (Throwable)ioexception);
        }
    }

    private void saveUserBanList() {
        try {
            this.getBans().save();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to save user banlist: ", (Throwable)ioexception);
        }
    }

    private void loadIpBanList() {
        try {
            this.getIpBans().load();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load ip banlist: ", (Throwable)ioexception);
        }
    }

    private void loadUserBanList() {
        try {
            this.getBans().load();
        } catch (IOException ioexception) {
            LOGGER.warn("Failed to load user banlist: ", (Throwable)ioexception);
        }
    }

    private void loadOps() {
        try {
            this.getOps().load();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load operators list: ", (Throwable)exception);
        }
    }

    private void saveOps() {
        try {
            this.getOps().save();
        } catch (Exception exception) {
            LOGGER.warn("Failed to save operators list: ", (Throwable)exception);
        }
    }

    private void loadWhiteList() {
        try {
            this.getWhiteList().load();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load white-list: ", (Throwable)exception);
        }
    }

    private void saveWhiteList() {
        try {
            this.getWhiteList().save();
        } catch (Exception exception) {
            LOGGER.warn("Failed to save white-list: ", (Throwable)exception);
        }
    }

    @Override
    public boolean isWhiteListed(GameProfile profile) {
        return !this.isUsingWhitelist() || this.isOp(profile) || this.getWhiteList().isWhiteListed(profile);
    }

    public DedicatedServer getServer() {
        return (DedicatedServer)super.getServer();
    }

    @Override
    public boolean canBypassPlayerLimit(GameProfile profile) {
        return this.getOps().canBypassPlayerLimit(profile);
    }
}
