package net.minecraft.client.resources.sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SimpleSoundInstance extends AbstractSoundInstance {
    public SimpleSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch, RandomSource random, BlockPos entity) {
        this(
            soundEvent,
            source,
            volume,
            pitch,
            random,
            (double)entity.getX() + 0.5,
            (double)entity.getY() + 0.5,
            (double)entity.getZ() + 0.5
        );
    }

    public static SimpleSoundInstance forUI(SoundEvent sound, float pitch) {
        return forUI(sound, pitch, 0.25F);
    }

    public static SimpleSoundInstance forUI(Holder<SoundEvent> soundHolder, float pitch) {
        return forUI(soundHolder.value(), pitch);
    }

    public static SimpleSoundInstance forUI(SoundEvent sound, float pitch, float volume) {
        return new SimpleSoundInstance(
            sound.getLocation(),
            SoundSource.MASTER,
            volume,
            pitch,
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
        );
    }

    public static SimpleSoundInstance forMusic(SoundEvent sound) {
        return new SimpleSoundInstance(
            sound.getLocation(),
            SoundSource.MUSIC,
            1.0F,
            1.0F,
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
        );
    }

    public static SimpleSoundInstance forJukeboxSong(SoundEvent sound, Vec3 pos) {
        return new SimpleSoundInstance(
            sound,
            SoundSource.RECORDS,
            4.0F,
            1.0F,
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.LINEAR,
            pos.x,
            pos.y,
            pos.z
        );
    }

    public static SimpleSoundInstance forLocalAmbience(SoundEvent sound, float volume, float pitch) {
        return new SimpleSoundInstance(
            sound.getLocation(),
            SoundSource.AMBIENT,
            pitch,
            volume,
            SoundInstance.createUnseededRandom(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0,
            0.0,
            0.0,
            true
        );
    }

    public static SimpleSoundInstance forAmbientAddition(SoundEvent sound) {
        return forLocalAmbience(sound, 1.0F, 1.0F);
    }

    public static SimpleSoundInstance forAmbientMood(SoundEvent soundEvent, RandomSource random, double x, double y, double z) {
        return new SimpleSoundInstance(
            soundEvent, SoundSource.AMBIENT, 1.0F, 1.0F, random, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z
        );
    }

    public SimpleSoundInstance(
        SoundEvent soundEvent,
        SoundSource source,
        float volume,
        float pitch,
        RandomSource random,
        double x,
        double y,
        double z
    ) {
        this(soundEvent, source, volume, pitch, random, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z);
    }

    private SimpleSoundInstance(
        SoundEvent soundEvent,
        SoundSource source,
        float volume,
        float pitch,
        RandomSource random,
        boolean looping,
        int delay,
        SoundInstance.Attenuation attenuation,
        double x,
        double y,
        double z
    ) {
        this(soundEvent.getLocation(), source, volume, pitch, random, looping, delay, attenuation, x, y, z, false);
    }

    public SimpleSoundInstance(
        ResourceLocation location,
        SoundSource source,
        float volume,
        float pitch,
        RandomSource random,
        boolean looping,
        int delay,
        SoundInstance.Attenuation attenuation,
        double x,
        double y,
        double z,
        boolean relative
    ) {
        super(location, source, random);
        this.volume = volume;
        this.pitch = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.looping = looping;
        this.delay = delay;
        this.attenuation = attenuation;
        this.relative = relative;
    }
}
