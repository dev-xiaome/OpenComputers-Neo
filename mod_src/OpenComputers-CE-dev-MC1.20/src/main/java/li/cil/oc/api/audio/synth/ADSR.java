package li.cil.oc.api.audio.synth;

import li.cil.oc.api.Audio;
import li.cil.oc.api.audio.AudioState;
import org.jetbrains.annotations.Nullable;

public class ADSR {

    public int attackDuration;
    public int decayDuration;
    public float attenuation;
    public int releaseDuration;
    public Phase phase = Phase.Attack;
    public double progress;
    private final Phase initialPhase;
    private final double initialProgress;

    // Precalculated speeds
    private final double attackSpeed;
    private final double decaySpeed;
    private final double releaseSpeed;

    public ADSR(int attackDuration, int decayDuration, float attenuation, int releaseDuration) {
        float sampleRate = Audio.getSampleRate();

        this.attackDuration = Math.max(attackDuration, 0);
        this.decayDuration = Math.max(decayDuration, 0);
        this.attenuation = Math.min(Math.max(attenuation, 0), 1);
        this.releaseDuration = Math.max(releaseDuration, 0);

        this.attackSpeed = 1000D / (this.attackDuration * sampleRate);
        if (this.attackDuration == 0) {
            if (this.decayDuration == 0) {
                this.initialPhase = this.phase = Phase.Sustain;
                this.initialProgress = this.progress = attenuation;
            } else {
                this.initialPhase = this.phase = Phase.Decay;
                this.initialProgress = this.progress = 1;
            }
        } else {
            this.initialPhase = Phase.Attack;
            this.initialProgress = this.progress = 0;
        }
        this.decaySpeed = this.decayDuration == 0 ? Double.POSITIVE_INFINITY : ((this.attenuation - 1D) * 1000D) / (this.decayDuration * sampleRate);
        this.releaseSpeed = this.releaseDuration == 0 ? Double.NEGATIVE_INFINITY : (-this.attenuation * 1000D) / (this.releaseDuration * sampleRate);
    }

    public double getModifiedValue(AudioState state, double value) {
        if (phase == null) {
            return 0;
        }
        if (state.gate == Gate.Closed && phase != Phase.Release) {
            phase = Phase.Release;
        }
        switch (phase) {
            case Attack: {
                // value = value * (progress / (double) attackSpeed);
                if ((progress += attackSpeed) >= 1) {
                    progress = 1;
                    nextPhase();
                }
                break;
            }
            case Decay: {
                // value = value * (1 + ((attenuation - 1) * (progress / (double) decaySpeed)));
                if ((progress += decaySpeed) <= attenuation) {
                    progress = attenuation;
                    nextPhase();
                }
                break;
            }
            case Release: {
                // value = value * (attenuation * (1 - (progress / (double) releaseSpeed)));
                if ((progress += releaseSpeed) <= 0) {
                    phase = null;
                    progress = 0;
                }
                break;
            }
        }
        value *= progress;
        return value;
    }

    private void nextPhase() {
        phase = phase.next();
    }

    public void reset() {
        this.phase = this.initialPhase;
        this.progress = this.initialProgress;
    }

    public enum Phase {
        Attack,
        Decay,
        Sustain,
        Release;

        @Nullable
        public Phase next() {
            int ordinal = ordinal();
            return ordinal < values().length ? values()[ordinal + 1] : null;
        }

        public static Phase fromIndex(int index) {
            return index >= 0 && index < values().length ? values()[index] : Attack;
        }
    }
}