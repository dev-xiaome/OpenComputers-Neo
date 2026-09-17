package net.minecraft.client.gui.screens.social;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerSocialManager {
    private final Minecraft minecraft;
    private final Set<UUID> hiddenPlayers = Sets.newHashSet();
    private final UserApiService service;
    private final Map<String, UUID> discoveredNamesToUUID = Maps.newHashMap();
    private boolean onlineMode;
    private CompletableFuture<?> pendingBlockListRefresh = CompletableFuture.completedFuture(null);

    public PlayerSocialManager(Minecraft minecraft, UserApiService service) {
        this.minecraft = minecraft;
        this.service = service;
    }

    public void hidePlayer(UUID id) {
        this.hiddenPlayers.add(id);
    }

    public void showPlayer(UUID id) {
        this.hiddenPlayers.remove(id);
    }

    public boolean shouldHideMessageFrom(UUID id) {
        return this.isHidden(id) || this.isBlocked(id);
    }

    public boolean isHidden(UUID id) {
        return this.hiddenPlayers.contains(id);
    }

    public void startOnlineMode() {
        this.onlineMode = true;
        this.pendingBlockListRefresh = this.pendingBlockListRefresh.thenRunAsync(this.service::refreshBlockList, Util.ioPool());
    }

    public void stopOnlineMode() {
        this.onlineMode = false;
    }

    public boolean isBlocked(UUID id) {
        if (!this.onlineMode) {
            return false;
        } else {
            this.pendingBlockListRefresh.join();
            return this.service.isBlockedPlayer(id);
        }
    }

    public Set<UUID> getHiddenPlayers() {
        return this.hiddenPlayers;
    }

    public UUID getDiscoveredUUID(String uuid) {
        return this.discoveredNamesToUUID.getOrDefault(uuid, Util.NIL_UUID);
    }

    public void addPlayer(PlayerInfo playerInfo) {
        GameProfile gameprofile = playerInfo.getProfile();
        this.discoveredNamesToUUID.put(gameprofile.getName(), gameprofile.getId());
        if (this.minecraft.screen instanceof SocialInteractionsScreen socialinteractionsscreen) {
            socialinteractionsscreen.onAddPlayer(playerInfo);
        }
    }

    public void removePlayer(UUID id) {
        if (this.minecraft.screen instanceof SocialInteractionsScreen socialinteractionsscreen) {
            socialinteractionsscreen.onRemovePlayer(id);
        }
    }
}
