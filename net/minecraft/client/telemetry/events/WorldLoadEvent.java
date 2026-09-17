package net.minecraft.client.telemetry.events;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.client.telemetry.TelemetryPropertyMap;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WorldLoadEvent {
    private boolean eventSent;
    @Nullable
    private TelemetryProperty.GameMode gameMode;
    @Nullable
    private String serverBrand;
    @Nullable
    private final String minigameName;

    public WorldLoadEvent(@Nullable String minigameName) {
        this.minigameName = minigameName;
    }

    public void addProperties(TelemetryPropertyMap.Builder builder) {
        if (this.serverBrand != null) {
            builder.put(TelemetryProperty.SERVER_MODDED, !this.serverBrand.equals("vanilla"));
        }

        builder.put(TelemetryProperty.SERVER_TYPE, this.getServerType());
    }

    private TelemetryProperty.ServerType getServerType() {
        ServerData serverdata = Minecraft.getInstance().getCurrentServer();
        if (serverdata != null && serverdata.isRealm()) {
            return TelemetryProperty.ServerType.REALM;
        } else {
            return Minecraft.getInstance().hasSingleplayerServer() ? TelemetryProperty.ServerType.LOCAL : TelemetryProperty.ServerType.OTHER;
        }
    }

    public boolean send(TelemetryEventSender sender) {
        if (!this.eventSent && this.gameMode != null && this.serverBrand != null) {
            this.eventSent = true;
            sender.send(TelemetryEventType.WORLD_LOADED, p_286185_ -> {
                p_286185_.put(TelemetryProperty.GAME_MODE, this.gameMode);
                if (this.minigameName != null) {
                    p_286185_.put(TelemetryProperty.REALMS_MAP_CONTENT, this.minigameName);
                }
            });
            return true;
        } else {
            return false;
        }
    }

    public void setGameMode(GameType gameMode, boolean isHardcore) {
        this.gameMode = switch (gameMode) {
            case SURVIVAL -> isHardcore ? TelemetryProperty.GameMode.HARDCORE : TelemetryProperty.GameMode.SURVIVAL;
            case CREATIVE -> TelemetryProperty.GameMode.CREATIVE;
            case ADVENTURE -> TelemetryProperty.GameMode.ADVENTURE;
            case SPECTATOR -> TelemetryProperty.GameMode.SPECTATOR;
        };
    }

    public void setServerBrand(String serverBrand) {
        this.serverBrand = serverBrand;
    }
}
