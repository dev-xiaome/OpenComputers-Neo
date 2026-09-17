package li.cil.oc.api.audio.synth;

import li.cil.oc.api.Audio;
import li.cil.oc.api.audio.AudioProcess;
import li.cil.oc.api.audio.AudioState;

public enum Gate {
    Open {
        @Override
        public double getValue(AudioProcess process, AudioState state, boolean isModulating) {
            if (!isModulating && (state.isAmpMod || state.isFreqMod)) {
                return 0;
            }
            double value = state.generator instanceof Noise ? ((Noise) state.generator).noiseOutput :
                    state.generator instanceof Wave ? ((Wave) state.generator).type.generate(state.offset) : 0;
            if (state.freqMod != null && !state.isFreqMod && !state.isAmpMod) {
                value = state.freqMod.getModifiedValue(process, state, value);
            } else {
                state.offset += state.frequencyInHz / Audio.getSampleRate();
            }
            if (state.offset > 1) {
                state.offset %= 1.0F;
                if (state.generator instanceof Noise) {
                    ((Noise) state.generator).updateModifier(state);
                }
            }
            if (state.ampMod != null && !state.isAmpMod && !state.isFreqMod) {
                value = state.ampMod.getModifiedValue(process, state, value);
            }
            if (state.envelope != null) {
                value = state.envelope.getModifiedValue(state, value);
            }
            return value * state.volume;
        }
    },
    Closed {
        @Override
        public double getValue(AudioProcess process, AudioState state, boolean isModulating) {
            if (state.envelope != null && state.envelope.phase != null) {
                return Open.getValue(process, state, isModulating);
            }
            return 0;
        }
    };

    public abstract double getValue(AudioProcess process, AudioState state, boolean isModulating);

    public double getValue(AudioProcess process, AudioState state) {
        return getValue(process, state, false);
    }

    public static final Gate[] VALUES = values();

    public static Gate fromIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : Closed;
    }
}