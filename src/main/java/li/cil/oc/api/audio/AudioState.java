package li.cil.oc.api.audio;

import li.cil.oc.api.audio.synth.*;
import net.minecraft.nbt.CompoundTag;

public class AudioState {
    public Generator generator;
    public final int channelIndex;

    public float frequencyInHz;
    public float offset;
    public Gate gate = Gate.Closed;
    public FrequencyModulation freqMod;
    public AmplitudeModulation ampMod;
    public ADSR envelope;
    public float volume = 1;

    public boolean isFreqMod, isAmpMod;

    public AudioState(int channelIndex) {
        this.generator = new Wave();
        this.channelIndex = channelIndex;
    }

    public void load(CompoundTag nbt) {
        if (nbt.contains("wavehz")) {
            frequencyInHz = nbt.getFloat("wavehz");
        }
        if (nbt.contains("offset")) {
            offset = nbt.getFloat("offset");
        }
        if (nbt.contains("type")) {
            this.generator = new Wave(AudioType.fromIndex(nbt.getInt("type")));
        }
        if (nbt.contains("noise")) {
            this.generator = Noise.load(nbt.getCompound("noise"));
        }
        if (nbt.contains("gate")) {
            gate = Gate.fromIndex(nbt.getInt("gate"));
        }
        if (nbt.contains("fmodi") && nbt.contains("findex")) {
            freqMod = new FrequencyModulation(nbt.getInt("fmodi"), nbt.getFloat("findex"));
        }
        if (nbt.contains("amodi")) {
            ampMod = new AmplitudeModulation(nbt.getInt("amodi"));
        }
        if (nbt.contains("a")) {
            envelope = new ADSR(nbt.getInt("a"), nbt.getInt("d"), nbt.getFloat("s"), nbt.getInt("r"));
            envelope.phase = ADSR.Phase.fromIndex(nbt.getInt("p"));
            envelope.progress = nbt.getDouble("o");
        }
        if (nbt.contains("vol")) {
            volume = nbt.getFloat("vol");
        }

        if (nbt.contains("isfmod")) {
            isFreqMod = nbt.getBoolean("isfmod");
        }
        if (nbt.contains("isamod")) {
            isAmpMod = nbt.getBoolean("isamod");
        }
    }

    public void save(CompoundTag nbt) {
        nbt.putFloat("wavehz", frequencyInHz);
        nbt.putFloat("offset", offset);
        if (generator instanceof Wave && ((Wave) generator).type != null) {
            nbt.putInt("type", ((Wave) generator).type.ordinal());
        }
        if (generator instanceof Noise) {
            CompoundTag data = new CompoundTag();
            ((Noise) generator).save(data);
            nbt.put("noise", data);
        }
        nbt.putInt("gate", gate.ordinal());
        if (freqMod != null) {
            nbt.putInt("fmodi", freqMod.modulatorIndex);
            nbt.putFloat("findex", freqMod.index);
        }
        if (ampMod != null) {
            nbt.putInt("amodi", ampMod.modulatorIndex);
        }
        if (envelope != null) {
            nbt.putInt("a", envelope.attackDuration);
            nbt.putInt("d", envelope.decayDuration);
            nbt.putFloat("s", envelope.attenuation);
            nbt.putInt("r", envelope.releaseDuration);
            nbt.putInt("p", envelope.phase.ordinal());
            nbt.putDouble("o", envelope.progress);
        }
        nbt.putFloat("vol", volume);
        nbt.putBoolean("isfmod", isFreqMod);
        nbt.putBoolean("isamod", isAmpMod);
    }
}