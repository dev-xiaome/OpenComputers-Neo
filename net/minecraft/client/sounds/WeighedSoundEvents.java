package net.minecraft.client.sounds;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The WeighedSoundEvents class represents a collection of weighted sound events.
 * It implements the Weighted interface to provide weighted selection of sounds.
 */
@OnlyIn(Dist.CLIENT)
public class WeighedSoundEvents implements Weighted<Sound> {
    private final List<Weighted<Sound>> list = Lists.newArrayList();
    @Nullable
    private final Component subtitle;

    /**
     * @param location    The resource location of the sound events
     * @param subtitleKey The key for the subtitle translation component, or null if
     *                    no subtitle is provided
     */
    public WeighedSoundEvents(ResourceLocation location, @Nullable String subtitleKey) {
        this.subtitle = subtitleKey == null ? null : Component.translatable(subtitleKey);
    }

    @Override
    public int getWeight() {
        int i = 0;

        for (Weighted<Sound> weighted : this.list) {
            i += weighted.getWeight();
        }

        return i;
    }

    /**
     * Retrieves a randomly selected sound from the sound events based on their weights.
     * The selection is performed using the provided random source.
     * <p>
     * @return A randomly selected sound from the sound events
     * The random source used for sound selection
     *
     * @param randomSource the random source used for sound selection
     */
    public Sound getSound(RandomSource randomSource) {
        int i = this.getWeight();
        if (!this.list.isEmpty() && i != 0) {
            int j = randomSource.nextInt(i);

            for (Weighted<Sound> weighted : this.list) {
                j -= weighted.getWeight();
                if (j < 0) {
                    return weighted.getSound(randomSource);
                }
            }

            return SoundManager.EMPTY_SOUND;
        } else {
            return SoundManager.EMPTY_SOUND;
        }
    }

    /**
     * Adds a sound event to the collection.
     *
     * @param accessor The weighted accessor for the sound event to be added
     */
    public void addSound(Weighted<Sound> accessor) {
        this.list.add(accessor);
    }

    @Nullable
    public Component getSubtitle() {
        return this.subtitle;
    }

    /**
     * Preloads the sound events into the sound engine if required.
     * This method is called to preload the sounds associated with the sound events into the sound engine, ensuring they are ready for playback.
     *
     * @param engine The sound engine used for sound preloading
     */
    @Override
    public void preloadIfRequired(SoundEngine engine) {
        for (Weighted<Sound> weighted : this.list) {
            weighted.preloadIfRequired(engine);
        }
    }
}
