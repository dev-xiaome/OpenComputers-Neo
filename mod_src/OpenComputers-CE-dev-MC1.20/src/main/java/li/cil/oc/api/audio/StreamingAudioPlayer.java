package li.cil.oc.api.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StreamingAudioPlayer extends DFPWM {
    public static class SourceEntry {
        public final String id;
        public final IntBuffer src;
        public int receivedPackets;

        public SourceEntry(String id) {
            this.id = id;
            src = BufferUtils.createIntBuffer(1);
            AL10.alGenSources(src);
        }
    }

    private final Set<SourceEntry> sources = new HashSet<>();
    private final List<IntBuffer> buffersPlayed = new ArrayList<>();
    private final int BUFFER_PACKETS, AUDIO_FORMAT;

    private IntBuffer currentBuffer;

    private int sampleRate = 48000;
    private float volume = 1.0F;
    private float distance = 24.0F;

    public StreamingAudioPlayer(boolean sixteenBit, boolean stereo, int bufferPackets) {
        super();
        BUFFER_PACKETS = bufferPackets;
        if (sixteenBit) {
            AUDIO_FORMAT = stereo ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
        } else {
            AUDIO_FORMAT = stereo ? AL10.AL_FORMAT_STEREO8 : AL10.AL_FORMAT_MONO8;
        }

        reset();
    }

    public void setHearing(float dist, float vol) {
        this.distance = dist;
        this.volume = vol;
    }

    public void setSampleRate(int rate) {
        sampleRate = rate;
    }

    public void reset() {
        buffersPlayed.clear();
        stop();
    }

    public void updatePosition(String id, float x, float y, float z) {
        for (SourceEntry source : sources) {
            if ((id != null && id.equals(source.id))) {
                AL10.alSource3f(source.src.get(0), AL10.AL_POSITION, x, y, z);
                return;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private double getDistance(double x, double y, double z) {
        Vec3 pos = Minecraft.getInstance().player.position();
        return pos.distanceTo(new Vec3(x, y, z));
    }

    public void push(byte[] data) {
        // Prepare buffers
        if (currentBuffer == null) {
            currentBuffer = BufferUtils.createIntBuffer(1);
        } else {
            for (SourceEntry source : sources) {
                int processed = AL10.alGetSourcei(source.src.get(0), AL10.AL_BUFFERS_PROCESSED);
                if (processed > 0) {
                    AL10.alSourceUnqueueBuffers(source.src.get(0), currentBuffer);
                }
            }
        }

        AL10.alGenBuffers(currentBuffer);
        int genErr = AL10.alGetError();
        AL10.alBufferData(currentBuffer.get(0), AUDIO_FORMAT, BufferUtils.createByteBuffer(data.length).put(data).flip(), sampleRate);
        int dataErr = AL10.alGetError();

        synchronized (buffersPlayed) {
            buffersPlayed.add(currentBuffer);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void play(String id, float x, float y, float z) {
        play(id, x, y, z, 0.0f);
    }

    @OnlyIn(Dist.CLIENT)
    public void play(String id, float x, float y, float z, float rolloff) {
        SourceEntry source = null;
        for (SourceEntry entry : sources) {
            if (id.equals(entry.id)) {
                source = entry;
            }
        }
        if (source == null) {
            source = new SourceEntry(id);
            sources.add(source);
        }

        // Calculate distance
        float playerDistance = (float) getDistance(x, y, z);
        float distanceUsed = distance * (0.2F + (volume * 0.8F));
        float distanceReal = rolloff <= 0 ? 1 - (playerDistance / distanceUsed) : playerDistance / distanceUsed >= 1 ? 0 : 1;

        float gain = distanceReal * volume * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
        if (gain < 0.0F) {
            gain = 0.0F;
        } else if (gain > 1.0F) {
            gain = 1.0F;
        }

        // Set settings
        AL10.alSourcei(source.src.get(0), AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcef(source.src.get(0), AL10.AL_PITCH, 1.0f);
        AL10.alSourcef(source.src.get(0), AL10.AL_GAIN, gain);
        AL10.alSource3f(source.src.get(0), AL10.AL_POSITION, x, y, z);
        AL10.alSource3f(source.src.get(0), AL10.AL_VELOCITY, 0.0f, 0.0f, 0.0f);
        AL10.alSourcef(source.src.get(0), AL10.AL_ROLLOFF_FACTOR, (24F * 0.25F) / distanceUsed); // At a distance of 24, a rolloff factor of 0.25 sounds good enough.

        // Play audio
        AL10.alSourceQueueBuffers(source.src.get(0), currentBuffer);
        int queueErr = AL10.alGetError();

        int state = AL10.alGetSourcei(source.src.get(0), AL10.AL_SOURCE_STATE);

        if (source.receivedPackets > BUFFER_PACKETS && state != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source.src.get(0));
        } else if (source.receivedPackets <= BUFFER_PACKETS) {
            AL10.alSourcePause(source.src.get(0));
        }

        source.receivedPackets++;
    }

    public void stop() {
        int sourceCount = sources.size();
        for (SourceEntry source : sources) {
            AL10.alSourceStop(source.src.get(0));
            AL10.alDeleteSources(source.src.get(0));
        }
        sources.clear();

        int bufferCount = 0;
        synchronized (buffersPlayed) {
            if (currentBuffer != null) {
                buffersPlayed.add(currentBuffer);
            }

            for (IntBuffer b : buffersPlayed) {
                b.rewind();
                for (int i = 0; i < b.limit(); i++) {
                    int buffer = b.get(i);
                    if (AL10.alIsBuffer(buffer)) {
                        AL10.alDeleteBuffers(buffer);
                        bufferCount++;
                    }
                }
            }
            buffersPlayed.clear();
        }
    }
}