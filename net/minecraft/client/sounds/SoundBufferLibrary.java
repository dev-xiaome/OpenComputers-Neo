package net.minecraft.client.sounds;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.audio.SoundBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The {@linkplain SoundBufferLibrary} class provides a cache containing instances of {@linkplain SoundBuffer} and {@linkplain AudioStream} for use in Minecraft sound handling.
 */
@OnlyIn(Dist.CLIENT)
public class SoundBufferLibrary {
    /**
     * The {@linkplain ResourceProvider} used for loading sound resources.
     */
    private final ResourceProvider resourceManager;
    private final Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache = Maps.newHashMap();

    public SoundBufferLibrary(ResourceProvider resourceManager) {
        this.resourceManager = resourceManager;
    }

    /**
     * {@return Returns a {@linkplain CompletableFuture} containing the complete {@linkplain SoundBuffer}. The {@linkplain SoundBuffer} is loaded asynchronously and cached.}
     *
     * @param soundID the {@linkplain ResourceLocation} of the sound
     */
    public CompletableFuture<SoundBuffer> getCompleteBuffer(ResourceLocation soundID) {
        return this.cache.computeIfAbsent(soundID, p_340614_ -> CompletableFuture.supplyAsync(() -> {
                try {
                    SoundBuffer soundbuffer;
                    try (
                        InputStream inputstream = this.resourceManager.open(p_340614_);
                        FiniteAudioStream finiteaudiostream = new JOrbisAudioStream(inputstream);
                    ) {
                        ByteBuffer bytebuffer = finiteaudiostream.readAll();
                        soundbuffer = new SoundBuffer(bytebuffer, finiteaudiostream.getFormat());
                    }

                    return soundbuffer;
                } catch (IOException ioexception) {
                    throw new CompletionException(ioexception);
                }
            }, Util.nonCriticalIoPool()));
    }

    /**
     * {@return Returns a {@linkplain CompletableFuture} containing the {@linkplain AudioStream}. The {@linkplain AudioStream} is loaded asynchronously.}
     *
     * @param resourceLocation the {@linkplain ResourceLocation} of the sound
     * @param isWrapper        whether the {@linkplain AudioStream} should be a {@
     *                         linkplain LoopingAudioStream}
     */
    public CompletableFuture<AudioStream> getStream(ResourceLocation resourceLocation, boolean isWrapper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InputStream inputstream = this.resourceManager.open(resourceLocation);
                return (AudioStream)(isWrapper ? new LoopingAudioStream(JOrbisAudioStream::new, inputstream) : new JOrbisAudioStream(inputstream));
            } catch (IOException ioexception) {
                throw new CompletionException(ioexception);
            }
        }, Util.nonCriticalIoPool());
    }

    public void clear() {
        this.cache.values().forEach(p_120201_ -> p_120201_.thenAccept(SoundBuffer::discardAlBuffer));
        this.cache.clear();
    }

    /**
     * Preloads the {@linkplain SoundBuffer} objects for the specified collection of sounds.
     * <p>
     * @return a {@linkplain CompletableFuture} representing the completion of the preload operation
     *
     * @param sounds the collection of sounds to preload
     */
    public CompletableFuture<?> preload(Collection<Sound> sounds) {
        return CompletableFuture.allOf(sounds.stream().map(p_120197_ -> this.getCompleteBuffer(p_120197_.getPath())).toArray(CompletableFuture[]::new));
    }
}
