package net.minecraft.client.resources.sounds;

import javax.annotation.Nullable;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.Weighted;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.SampledFloat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Sound implements Weighted<Sound> {
    public static final FileToIdConverter SOUND_LISTER = new FileToIdConverter("sounds", ".ogg");
    private final ResourceLocation location;
    private final SampledFloat volume;
    private final SampledFloat pitch;
    private final int weight;
    private final Sound.Type type;
    private final boolean stream;
    private final boolean preload;
    private final int attenuationDistance;

    public Sound(
        ResourceLocation location,
        SampledFloat volume,
        SampledFloat pitch,
        int weight,
        Sound.Type type,
        boolean stream,
        boolean preload,
        int attenuationDistance
    ) {
        this.location = location;
        this.volume = volume;
        this.pitch = pitch;
        this.weight = weight;
        this.type = type;
        this.stream = stream;
        this.preload = preload;
        this.attenuationDistance = attenuationDistance;
    }

    public ResourceLocation getLocation() {
        return this.location;
    }

    public ResourceLocation getPath() {
        return SOUND_LISTER.idToFile(this.location);
    }

    public SampledFloat getVolume() {
        return this.volume;
    }

    public SampledFloat getPitch() {
        return this.pitch;
    }

    @Override
    public int getWeight() {
        return this.weight;
    }

    /**
     * Retrieves the sound associated with the element.
     * The sound is obtained using the provided random source.
     * <p>
     * @return The sound associated with the element
     *
     * @param randomSource the random source used for sound selection
     */
    public Sound getSound(RandomSource randomSource) {
        return this;
    }

    /**
     * Preloads the sound if required by the sound engine.
     * This method is called to preload the sound associated with the element into the sound engine, ensuring it is ready for playback.
     *
     * @param engine the sound engine used for sound preloading
     */
    @Override
    public void preloadIfRequired(SoundEngine engine) {
        if (this.preload) {
            engine.requestPreload(this);
        }
    }

    public Sound.Type getType() {
        return this.type;
    }

    public boolean shouldStream() {
        return this.stream;
    }

    public boolean shouldPreload() {
        return this.preload;
    }

    public int getAttenuationDistance() {
        return this.attenuationDistance;
    }

    @Override
    public String toString() {
        return "Sound[" + this.location + "]";
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Type {
        FILE("file"),
        SOUND_EVENT("event");

        private final String name;

        private Type(String name) {
            this.name = name;
        }

        @Nullable
        public static Sound.Type getByName(String name) {
            for (Sound.Type sound$type : values()) {
                if (sound$type.name.equals(name)) {
                    return sound$type;
                }
            }

            return null;
        }
    }
}
