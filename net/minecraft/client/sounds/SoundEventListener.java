package net.minecraft.client.sounds;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The SoundEventListener interface defines a listener for sound events.
 * Classes implementing this interface can be registered as listeners to receive notifications when a sound is played.
 */
@OnlyIn(Dist.CLIENT)
public interface SoundEventListener {
    void onPlaySound(SoundInstance sound, WeighedSoundEvents accessor, float range);
}
