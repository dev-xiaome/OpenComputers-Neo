package li.cil.oc.api.audio.synth;

import li.cil.oc.api.audio.AudioState;
import net.minecraft.nbt.CompoundTag;

public class LFSR extends Noise {

    private int value;
    private final int mask;

    public LFSR(int value, int mask) {
        this.value = value;
        this.mask = mask;
    }

    @Override
    protected double generate(AudioState state) {
        if ((value & 1) != 0) {
            value = (value >>> 1) ^ mask;
            return 1;
        } else {
            value >>>= 1;
            return -1;
        }
    }

    @Override
    public void save(CompoundTag nbt) {
        nbt.putByte("t", (byte) 1);
        nbt.putDouble("o", noiseOutput);
        nbt.putInt("v", value);
        nbt.putInt("m", mask);
    }
}