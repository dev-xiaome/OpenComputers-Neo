package li.cil.oc.api.audio.synth;

import li.cil.oc.api.audio.AudioProcess;
import li.cil.oc.api.audio.AudioState;

public abstract class Modulation {
    public abstract double getModifiedValue(AudioProcess process, AudioState state, double value);
}