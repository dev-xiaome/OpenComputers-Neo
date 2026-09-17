package net.minecraft.client.telemetry;

import java.time.Duration;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.telemetry.events.PerformanceMetricsEvent;
import net.minecraft.client.telemetry.events.WorldLoadEvent;
import net.minecraft.client.telemetry.events.WorldLoadTimesEvent;
import net.minecraft.client.telemetry.events.WorldUnloadEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WorldSessionTelemetryManager {
    private final UUID worldSessionId = UUID.randomUUID();
    private final TelemetryEventSender eventSender;
    private final WorldLoadEvent worldLoadEvent;
    private final WorldUnloadEvent worldUnloadEvent = new WorldUnloadEvent();
    private final PerformanceMetricsEvent performanceMetricsEvent;
    private final WorldLoadTimesEvent worldLoadTimesEvent;

    public WorldSessionTelemetryManager(TelemetryEventSender sender, boolean newWorld, @Nullable Duration worldLoadDuration, @Nullable String minigameName) {
        this.worldLoadEvent = new WorldLoadEvent(minigameName);
        this.performanceMetricsEvent = new PerformanceMetricsEvent();
        this.worldLoadTimesEvent = new WorldLoadTimesEvent(newWorld, worldLoadDuration);
        this.eventSender = sender.decorate(p_261981_ -> {
            this.worldLoadEvent.addProperties(p_261981_);
            p_261981_.put(TelemetryProperty.WORLD_SESSION_ID, this.worldSessionId);
        });
    }

    public void tick() {
        this.performanceMetricsEvent.tick(this.eventSender);
    }

    public void onPlayerInfoReceived(GameType gameType, boolean isHardcore) {
        this.worldLoadEvent.setGameMode(gameType, isHardcore);
        this.worldUnloadEvent.onPlayerInfoReceived();
        this.worldSessionStart();
    }

    public void onServerBrandReceived(String serverBrand) {
        this.worldLoadEvent.setServerBrand(serverBrand);
        this.worldSessionStart();
    }

    public void setTime(long time) {
        this.worldUnloadEvent.setTime(time);
    }

    public void worldSessionStart() {
        if (this.worldLoadEvent.send(this.eventSender)) {
            this.worldLoadTimesEvent.send(this.eventSender);
            this.performanceMetricsEvent.start();
        }
    }

    public void onDisconnect() {
        this.worldLoadEvent.send(this.eventSender);
        this.performanceMetricsEvent.stop();
        this.worldUnloadEvent.send(this.eventSender);
    }

    public void onAdvancementDone(Level level, AdvancementHolder advancement) {
        ResourceLocation resourcelocation = advancement.id();
        if (advancement.value().sendsTelemetryEvent() && "minecraft".equals(resourcelocation.getNamespace())) {
            long i = level.getGameTime();
            this.eventSender.send(TelemetryEventType.ADVANCEMENT_MADE, p_286184_ -> {
                p_286184_.put(TelemetryProperty.ADVANCEMENT_ID, resourcelocation.toString());
                p_286184_.put(TelemetryProperty.ADVANCEMENT_GAME_TIME, i);
            });
        }
    }
}
