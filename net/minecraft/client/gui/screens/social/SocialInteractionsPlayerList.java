package net.minecraft.client.gui.screens.social;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.client.multiplayer.chat.LoggedChatEvent;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SocialInteractionsPlayerList extends ContainerObjectSelectionList<PlayerEntry> {
    private final SocialInteractionsScreen socialInteractionsScreen;
    private final List<PlayerEntry> players = Lists.newArrayList();
    @Nullable
    private String filter;

    public SocialInteractionsPlayerList(SocialInteractionsScreen socialInteractionsScreen, Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.socialInteractionsScreen = socialInteractionsScreen;
    }

    @Override
    protected void renderListBackground(GuiGraphics guiGraphics) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics guiGraphics) {
    }

    @Override
    protected void enableScissor(GuiGraphics guiGraphics) {
        guiGraphics.enableScissor(this.getX(), this.getY() + 4, this.getRight(), this.getBottom());
    }

    public void updatePlayerList(Collection<UUID> ids, double scrollAmount, boolean addChatLogPlayers) {
        Map<UUID, PlayerEntry> map = new HashMap<>();
        this.addOnlinePlayers(ids, map);
        this.updatePlayersFromChatLog(map, addChatLogPlayers);
        this.updateFiltersAndScroll(map.values(), scrollAmount);
    }

    private void addOnlinePlayers(Collection<UUID> ids, Map<UUID, PlayerEntry> playerMap) {
        ClientPacketListener clientpacketlistener = this.minecraft.player.connection;

        for (UUID uuid : ids) {
            PlayerInfo playerinfo = clientpacketlistener.getPlayerInfo(uuid);
            if (playerinfo != null) {
                boolean flag = playerinfo.hasVerifiableChat();
                playerMap.put(
                    uuid, new PlayerEntry(this.minecraft, this.socialInteractionsScreen, uuid, playerinfo.getProfile().getName(), playerinfo::getSkin, flag)
                );
            }
        }
    }

    private void updatePlayersFromChatLog(Map<UUID, PlayerEntry> playerMap, boolean addPlayers) {
        for (GameProfile gameprofile : collectProfilesFromChatLog(this.minecraft.getReportingContext().chatLog())) {
            PlayerEntry playerentry;
            if (addPlayers) {
                playerentry = playerMap.computeIfAbsent(
                    gameprofile.getId(),
                    p_293608_ -> {
                        PlayerEntry playerentry1 = new PlayerEntry(
                            this.minecraft,
                            this.socialInteractionsScreen,
                            gameprofile.getId(),
                            gameprofile.getName(),
                            this.minecraft.getSkinManager().lookupInsecure(gameprofile),
                            true
                        );
                        playerentry1.setRemoved(true);
                        return playerentry1;
                    }
                );
            } else {
                playerentry = playerMap.get(gameprofile.getId());
                if (playerentry == null) {
                    continue;
                }
            }

            playerentry.setHasRecentMessages(true);
        }
    }

    private static Collection<GameProfile> collectProfilesFromChatLog(ChatLog chatLog) {
        Set<GameProfile> set = new ObjectLinkedOpenHashSet<>();

        for (int i = chatLog.end(); i >= chatLog.start(); i--) {
            LoggedChatEvent loggedchatevent = chatLog.lookup(i);
            if (loggedchatevent instanceof LoggedChatMessage.Player) {
                LoggedChatMessage.Player loggedchatmessage$player = (LoggedChatMessage.Player)loggedchatevent;
                if (loggedchatmessage$player.message().hasSignature()) {
                    set.add(loggedchatmessage$player.profile());
                }
            }
        }

        return set;
    }

    private void sortPlayerEntries() {
        this.players.sort(Comparator.<PlayerEntry, Integer>comparing(p_240744_ -> {
            if (this.minecraft.isLocalPlayer(p_240744_.getPlayerId())) {
                return 0;
            } else if (this.minecraft.getReportingContext().hasDraftReportFor(p_240744_.getPlayerId())) {
                return 1;
            } else if (p_240744_.getPlayerId().version() == 2) {
                return 4;
            } else {
                return p_240744_.hasRecentMessages() ? 2 : 3;
            }
        }).thenComparing(p_240745_ -> {
            if (!p_240745_.getPlayerName().isBlank()) {
                int i = p_240745_.getPlayerName().codePointAt(0);
                if (i == 95 || i >= 97 && i <= 122 || i >= 65 && i <= 90 || i >= 48 && i <= 57) {
                    return 0;
                }
            }

            return 1;
        }).thenComparing(PlayerEntry::getPlayerName, String::compareToIgnoreCase));
    }

    private void updateFiltersAndScroll(Collection<PlayerEntry> players, double scrollAmount) {
        this.players.clear();
        this.players.addAll(players);
        this.sortPlayerEntries();
        this.updateFilteredPlayers();
        this.replaceEntries(this.players);
        this.setScrollAmount(scrollAmount);
    }

    private void updateFilteredPlayers() {
        if (this.filter != null) {
            this.players.removeIf(p_100710_ -> !p_100710_.getPlayerName().toLowerCase(Locale.ROOT).contains(this.filter));
            this.replaceEntries(this.players);
        }
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public boolean isEmpty() {
        return this.players.isEmpty();
    }

    public void addPlayer(PlayerInfo playerInfo, SocialInteractionsScreen.Page page) {
        UUID uuid = playerInfo.getProfile().getId();

        for (PlayerEntry playerentry : this.players) {
            if (playerentry.getPlayerId().equals(uuid)) {
                playerentry.setRemoved(false);
                return;
            }
        }

        if ((page == SocialInteractionsScreen.Page.ALL || this.minecraft.getPlayerSocialManager().shouldHideMessageFrom(uuid))
            && (Strings.isNullOrEmpty(this.filter) || playerInfo.getProfile().getName().toLowerCase(Locale.ROOT).contains(this.filter))) {
            boolean flag = playerInfo.hasVerifiableChat();
            PlayerEntry playerentry1 = new PlayerEntry(
                this.minecraft, this.socialInteractionsScreen, playerInfo.getProfile().getId(), playerInfo.getProfile().getName(), playerInfo::getSkin, flag
            );
            this.addEntry(playerentry1);
            this.players.add(playerentry1);
        }
    }

    public void removePlayer(UUID id) {
        for (PlayerEntry playerentry : this.players) {
            if (playerentry.getPlayerId().equals(id)) {
                playerentry.setRemoved(true);
                return;
            }
        }
    }
}
