package net.minecraft.client.server;

import com.mojang.authlib.GameProfile;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IntegratedPlayerList extends PlayerList {
    @Nullable
    private CompoundTag playerData;

    public IntegratedPlayerList(IntegratedServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo) {
        super(server, registries, playerIo, 8);
        this.setViewDistance(10);
    }

    /**
     * Also stores the NBTTags if this is an IntegratedPlayerList.
     */
    @Override
    protected void save(ServerPlayer player) {
        if (this.getServer().isSingleplayerOwner(player.getGameProfile())) {
            this.playerData = player.saveWithoutId(new CompoundTag());
        }

        super.save(player);
    }

    @Override
    public Component canPlayerLogin(SocketAddress socketAddress, GameProfile gameProfile) {
        return (Component)(this.getServer().isSingleplayerOwner(gameProfile) && this.getPlayerByName(gameProfile.getName()) != null
            ? Component.translatable("multiplayer.disconnect.name_taken")
            : super.canPlayerLogin(socketAddress, gameProfile));
    }

    public IntegratedServer getServer() {
        return (IntegratedServer)super.getServer();
    }

    @Nullable
    @Override
    public CompoundTag getSingleplayerData() {
        return this.playerData;
    }
}
