package net.minecraft.client.main;

import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.DisplayData;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.client.User;
import net.minecraft.client.resources.IndexedAssetSource;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GameConfig {
    public final GameConfig.UserData user;
    public final DisplayData display;
    public final GameConfig.FolderData location;
    public final GameConfig.GameData game;
    public final GameConfig.QuickPlayData quickPlay;

    public GameConfig(
        GameConfig.UserData user,
        DisplayData display,
        GameConfig.FolderData location,
        GameConfig.GameData game,
        GameConfig.QuickPlayData quickPlay
    ) {
        this.user = user;
        this.display = display;
        this.location = location;
        this.game = game;
        this.quickPlay = quickPlay;
    }

    @OnlyIn(Dist.CLIENT)
    public static class FolderData {
        public final File gameDirectory;
        public final File resourcePackDirectory;
        public final File assetDirectory;
        @Nullable
        public final String assetIndex;

        public FolderData(File gameDirectory, File resourcePackDirectory, File assetDirectory, @Nullable String assetIndex) {
            this.gameDirectory = gameDirectory;
            this.resourcePackDirectory = resourcePackDirectory;
            this.assetDirectory = assetDirectory;
            this.assetIndex = assetIndex;
        }

        public Path getExternalAssetSource() {
            return this.assetIndex == null ? this.assetDirectory.toPath() : IndexedAssetSource.createIndexFs(this.assetDirectory.toPath(), this.assetIndex);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class GameData {
        public final boolean demo;
        public final String launchVersion;
        public final String versionType;
        public final boolean disableMultiplayer;
        public final boolean disableChat;

        public GameData(boolean demo, String launchVersion, String versionType, boolean disableMultiplayer, boolean disableChat) {
            this.demo = demo;
            this.launchVersion = launchVersion;
            this.versionType = versionType;
            this.disableMultiplayer = disableMultiplayer;
            this.disableChat = disableChat;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record QuickPlayData(@Nullable String path, @Nullable String singleplayer, @Nullable String multiplayer, @Nullable String realms) {
        public boolean isEnabled() {
            return !StringUtil.isBlank(this.singleplayer) || !StringUtil.isBlank(this.multiplayer) || !StringUtil.isBlank(this.realms);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class UserData {
        public final User user;
        public final PropertyMap userProperties;
        public final PropertyMap profileProperties;
        public final Proxy proxy;

        public UserData(User user, PropertyMap userProperties, PropertyMap profileProperties, Proxy proxy) {
            this.user = user;
            this.userProperties = userProperties;
            this.profileProperties = profileProperties;
            this.proxy = proxy;
        }
    }
}
