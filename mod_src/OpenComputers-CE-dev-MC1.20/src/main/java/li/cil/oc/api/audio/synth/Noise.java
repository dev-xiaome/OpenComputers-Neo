package li.cil.oc.api.audio.synth;

import li.cil.oc.api.audio.AudioState;
import net.minecraft.nbt.CompoundTag;

public abstract class Noise extends Generator {

    public double noiseOutput;

    public void updateModifier(AudioState state) {
        this.noiseOutput = generate(state);
    }

    protected abstract double generate(AudioState state);

    public static Noise load(CompoundTag nbt) {
        final Noise noise;
        if (nbt.getByte("t") == 1) {
            noise = new LFSR(nbt.getInt("v"), nbt.getInt("m"));
        } else {
            noise = new WhiteNoise();
        }
        noise.noiseOutput = nbt.getDouble("o");
        return noise;
    }

    public abstract void save(CompoundTag nbt);

}