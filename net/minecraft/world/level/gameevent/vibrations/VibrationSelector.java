package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class VibrationSelector {
    public static final Codec<VibrationSelector> CODEC = RecordCodecBuilder.create(
        p_338089_ -> p_338089_.group(
                    VibrationInfo.CODEC.lenientOptionalFieldOf("event").forGetter(p_251862_ -> p_251862_.currentVibrationData.map(Pair::getLeft)),
                    Codec.LONG.fieldOf("tick").forGetter(p_251458_ -> p_251458_.currentVibrationData.map(Pair::getRight).orElse(-1L))
                )
                .apply(p_338089_, VibrationSelector::new)
    );
    private Optional<Pair<VibrationInfo, Long>> currentVibrationData;

    public VibrationSelector(Optional<VibrationInfo> event, long tick) {
        this.currentVibrationData = event.map(p_251571_ -> Pair.of(p_251571_, tick));
    }

    public VibrationSelector() {
        this.currentVibrationData = Optional.empty();
    }

    public void addCandidate(VibrationInfo vibrationInfo, long tick) {
        if (this.shouldReplaceVibration(vibrationInfo, tick)) {
            this.currentVibrationData = Optional.of(Pair.of(vibrationInfo, tick));
        }
    }

    private boolean shouldReplaceVibration(VibrationInfo vibrationInfo, long tick) {
        if (this.currentVibrationData.isEmpty()) {
            return true;
        } else {
            Pair<VibrationInfo, Long> pair = this.currentVibrationData.get();
            long i = pair.getRight();
            if (tick != i) {
                return false;
            } else {
                VibrationInfo vibrationinfo = pair.getLeft();
                if (vibrationInfo.distance() < vibrationinfo.distance()) {
                    return true;
                } else {
                    return vibrationInfo.distance() > vibrationinfo.distance()
                        ? false
                        : VibrationSystem.getGameEventFrequency(vibrationInfo.gameEvent()) > VibrationSystem.getGameEventFrequency(vibrationinfo.gameEvent());
                }
            }
        }
    }

    public Optional<VibrationInfo> chosenCandidate(long tick) {
        if (this.currentVibrationData.isEmpty()) {
            return Optional.empty();
        } else {
            return this.currentVibrationData.get().getRight() < tick ? Optional.of(this.currentVibrationData.get().getLeft()) : Optional.empty();
        }
    }

    public void startOver() {
        this.currentVibrationData = Optional.empty();
    }
}
