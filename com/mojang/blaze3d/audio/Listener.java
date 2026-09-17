package com.mojang.blaze3d.audio;

import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL10;

/**
 * The Listener class represents the listener in a 3D audio environment.
 *
 * The listener's position and orientation determine how sounds are perceived by the listener.
 */
@OnlyIn(Dist.CLIENT)
public class Listener {
    private float gain = 1.0F;
    private ListenerTransform transform = ListenerTransform.INITIAL;

    public void setTransform(ListenerTransform transform) {
        this.transform = transform;
        Vec3 vec3 = transform.position();
        Vec3 vec31 = transform.forward();
        Vec3 vec32 = transform.up();
        AL10.alListener3f(4100, (float)vec3.x, (float)vec3.y, (float)vec3.z);
        AL10.alListenerfv(4111, new float[]{(float)vec31.x, (float)vec31.y, (float)vec31.z, (float)vec32.x(), (float)vec32.y(), (float)vec32.z()});
    }

    /**
     * Sets the listener's gain.
     *
     * @param gain The gain to set for the listener.
     */
    public void setGain(float gain) {
        AL10.alListenerf(4106, gain);
        this.gain = gain;
    }

    public float getGain() {
        return this.gain;
    }

    public void reset() {
        this.setTransform(ListenerTransform.INITIAL);
    }

    public ListenerTransform getTransform() {
        return this.transform;
    }
}
