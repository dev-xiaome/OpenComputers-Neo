package net.minecraft.client.telemetry.events;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.client.telemetry.TelemetryPropertyMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WorldUnloadEvent {
    private static final int NOT_TRACKING_TIME = -1;
    private Optional<Instant> worldLoadedTime = Optional.empty();
    private long totalTicks;
    private long lastGameTime;

    public void onPlayerInfoReceived() {
        this.lastGameTime = -1L;
        if (this.worldLoadedTime.isEmpty()) {
            this.worldLoadedTime = Optional.of(Instant.now());
        }
    }

    public void setTime(long time) {
        if (this.lastGameTime != -1L) {
            this.totalTicks = this.totalTicks + Math.max(0L, time - this.lastGameTime);
        }

        this.lastGameTime = time;
    }

    private int getTimeInSecondsSinceLoad(Instant wordLoadedTime) {
        Duration duration = Duration.between(wordLoadedTime, Instant.now());
        return (int)duration.toSeconds();
    }

    public void send(TelemetryEventSender sender) {
        this.worldLoadedTime.ifPresent(p_261953_ -> sender.send(TelemetryEventType.WORLD_UNLOADED, p_261597_ -> {
                p_261597_.put(TelemetryProperty.SECONDS_SINCE_LOAD, this.getTimeInSecondsSinceLoad(p_261953_));
                p_261597_.put(TelemetryProperty.TICKS_SINCE_LOAD, (int)this.totalTicks);
            }));
    }
}
