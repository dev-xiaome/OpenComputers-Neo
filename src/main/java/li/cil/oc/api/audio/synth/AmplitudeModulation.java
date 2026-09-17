package li.cil.oc.api.audio.synth;

import li.cil.oc.api.audio.AudioProcess;
import li.cil.oc.api.audio.AudioState;

public class AmplitudeModulation extends Modulation {

    public final int modulatorIndex;

    public AmplitudeModulation(int modulatorIndex) {
        this.modulatorIndex = modulatorIndex;
    }

    @Override
    public double getModifiedValue(AudioProcess process, AudioState state, double value) {
        AudioState mstate = process.states.get(modulatorIndex);
        return value * (1 + mstate.gate.getValue(process, mstate, true));
    }
}