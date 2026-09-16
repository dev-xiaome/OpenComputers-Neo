package li.cil.oc.api.audio.synth;

import li.cil.oc.api.Audio;
import li.cil.oc.api.audio.AudioProcess;
import li.cil.oc.api.audio.AudioState;

public class FrequencyModulation extends Modulation {

    public final int modulatorIndex;
    public final float index;

    public FrequencyModulation(int modulatorIndex, float index) {
        this.modulatorIndex = modulatorIndex;
        this.index = index;
    }

    @Override
    public double getModifiedValue(AudioProcess process, AudioState state, double value) {
        AudioState mstate = process.states.get(modulatorIndex);
        double deviation = mstate.gate.getValue(process, mstate, true) * index;
        state.offset += (float) ((state.frequencyInHz + deviation) / Audio.getSampleRate());
        return value;
    }
}