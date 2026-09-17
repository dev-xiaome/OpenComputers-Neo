package net.minecraft.client.resources.metadata.animation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AnimationFrame {
    public static final int UNKNOWN_FRAME_TIME = -1;
    private final int index;
    private final int time;

    public AnimationFrame(int index) {
        this(index, -1);
    }

    public AnimationFrame(int index, int time) {
        this.index = index;
        this.time = time;
    }

    public int getTime(int defaultValue) {
        return this.time == -1 ? defaultValue : this.time;
    }

    public int getIndex() {
        return this.index;
    }
}
