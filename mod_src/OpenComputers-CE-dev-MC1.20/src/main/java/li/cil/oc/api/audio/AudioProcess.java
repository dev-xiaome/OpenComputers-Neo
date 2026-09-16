package li.cil.oc.api.audio;

import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class AudioProcess {
    public final ImmutableList<AudioState> states;
    public int delay = 0;
    public double error = 0;

    public AudioProcess(int channelCount) {
        List<AudioState> states = new ArrayList<>(channelCount);
        for (int i = 0; i < channelCount; i++) {
            states.add(new AudioState(i));
        }
        this.states = ImmutableList.copyOf(states);
    }

    public void load(CompoundTag nbt) {
        for (int i = 0; i < states.size(); i++) {
            if (nbt.contains("ch" + i)) {
                states.get(i).load(nbt.getCompound("ch" + i));
            }
        }
        if (nbt.contains("delay")) {
            delay = nbt.getInt("delay");
        }
    }

    public void save(CompoundTag nbt) {
        for (int i = 0; i < states.size(); i++) {
            CompoundTag stateNBT = new CompoundTag();
            nbt.put("ch" + i, stateNBT);
            states.get(i).save(stateNBT);
        }
        nbt.putInt("delay", delay);
    }
}
