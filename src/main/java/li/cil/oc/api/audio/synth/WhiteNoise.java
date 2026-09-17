package li.cil.oc.api.audio.synth;

import li.cil.oc.api.audio.AudioState;
import net.minecraft.nbt.CompoundTag;

public class WhiteNoise extends Noise {

    @Override
    protected double generate(AudioState state) {
        return Math.random() * 2 - 1;
    }

    @Override
    public void save(CompoundTag nbt) {
        nbt.putByte("t", (byte) 0);
        nbt.putDouble("o", noiseOutput);
    }
}