package net.minecraft.client.sounds;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * The {@code SoundEngine} class handles the management and playback of sounds in the game.
 */
@OnlyIn(Dist.CLIENT)
public class SoundEngine {
    /**
     * The marker used for logging
     */
    private static final Marker MARKER = MarkerFactory.getMarker("SOUNDS");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float PITCH_MIN = 0.5F;
    private static final float PITCH_MAX = 2.0F;
    private static final float VOLUME_MIN = 0.0F;
    private static final float VOLUME_MAX = 1.0F;
    private static final int MIN_SOURCE_LIFETIME = 20;
    /**
     * A set of resource locations for which a missing sound warning has been issued
     */
    private static final Set<ResourceLocation> ONLY_WARN_ONCE = Sets.newHashSet();
    /**
     * The default interval in milliseconds for checking the audio device state
     */
    private static final long DEFAULT_DEVICE_CHECK_INTERVAL_MS = 1000L;
    public static final String MISSING_SOUND = "FOR THE DEBUG!";
    public static final String OPEN_AL_SOFT_PREFIX = "OpenAL Soft on ";
    public static final int OPEN_AL_SOFT_PREFIX_LENGTH = "OpenAL Soft on ".length();
    /**
     * A reference to the sound handler.
     */
    public final SoundManager soundManager;
    /**
     * Reference to the GameSettings object.
     */
    private final Options options;
    /**
     * Set to true when the SoundManager has been initialised.
     */
    private boolean loaded;
    private final Library library = new Library();
    /**
     * The listener object responsible for managing the sound listener position and orientation
     */
    private final Listener listener = this.library.getListener();
    private final SoundBufferLibrary soundBuffers;
    private final SoundEngineExecutor executor = new SoundEngineExecutor();
    private final ChannelAccess channelAccess = new ChannelAccess(this.library, this.executor);
    /**
     * A counter for how long the sound manager has been running
     */
    private int tickCount;
    private long lastDeviceCheckTime;
    /**
     * The current state of the audio device check
     */
    private final AtomicReference<SoundEngine.DeviceCheckState> devicePoolState = new AtomicReference<>(SoundEngine.DeviceCheckState.NO_CHANGE);
    private final Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel = Maps.newHashMap();
    private final Multimap<SoundSource, SoundInstance> instanceBySource = HashMultimap.create();
    /**
     * A subset of playingSounds, this contains only {@linkplain TickableSoundInstance}
     */
    private final List<TickableSoundInstance> tickingSounds = Lists.newArrayList();
    /**
     * Contains sounds to play in n ticks. Type: HashMap<ISound, Integer>
     */
    private final Map<SoundInstance, Integer> queuedSounds = Maps.newHashMap();
    /**
     * The future time in which to stop this sound. Type: HashMap<String, Integer>
     */
    private final Map<SoundInstance, Integer> soundDeleteTime = Maps.newHashMap();
    private final List<SoundEventListener> listeners = Lists.newArrayList();
    private final List<TickableSoundInstance> queuedTickableSounds = Lists.newArrayList();
    private final List<Sound> preloadQueue = Lists.newArrayList();

    public SoundEngine(SoundManager soundManager, Options options, ResourceProvider resourceManager) {
        this.soundManager = soundManager;
        this.options = options;
        this.soundBuffers = new SoundBufferLibrary(resourceManager);
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent(this));
    }

    public void reload() {
        ONLY_WARN_ONCE.clear();

        for (SoundEvent soundevent : BuiltInRegistries.SOUND_EVENT) {
            if (soundevent != SoundEvents.EMPTY) {
                ResourceLocation resourcelocation = soundevent.getLocation();
                if (this.soundManager.getSoundEvent(resourcelocation) == null) {
                    LOGGER.warn("Missing sound for event: {}", BuiltInRegistries.SOUND_EVENT.getKey(soundevent));
                    ONLY_WARN_ONCE.add(resourcelocation);
                }
            }
        }

        this.destroy();
        this.loadLibrary();
        net.neoforged.fml.ModLoader.postEvent(new net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent(this));
    }

    private synchronized void loadLibrary() {
        if (!this.loaded) {
            try {
                String s = this.options.soundDevice().get();
                this.library.init("".equals(s) ? null : s, this.options.directionalAudio().get());
                this.listener.reset();
                this.listener.setGain(this.options.getSoundSourceVolume(SoundSource.MASTER));
                this.soundBuffers.preload(this.preloadQueue).thenRun(this.preloadQueue::clear);
                this.loaded = true;
                LOGGER.info(MARKER, "Sound engine started");
            } catch (RuntimeException runtimeexception) {
                LOGGER.error(MARKER, "Error starting SoundSystem. Turning off sounds & music", (Throwable)runtimeexception);
            }
        }
    }

    /**
     * {@return the volume value pinned between 0.0f and 1.0f for a given {@linkplain SoundSource} category}
     */
    private float getVolume(@Nullable SoundSource category) {
        return category != null && category != SoundSource.MASTER ? this.options.getSoundSourceVolume(category) : 1.0F;
    }

    /**
     * Updates the volume for a specific sound category.
     * <p>
     * If the sound engine has not been loaded, the method returns without performing any action.
     * <p>
     * If the category is the "MASTER" category, the overall listener gain (volume) is set to the specified value.
     * <p>
     * For other categories, the volume is updated for each sound instance associated with the category.
     * <p>
     * If the calculated volume for an instance is less than or equal to 0.0, the instance is stopped.
     * Otherwise, the volume of the instance is set to the calculated value.
     */
    public void updateCategoryVolume(SoundSource category, float volume) {
        if (this.loaded) {
            if (category == SoundSource.MASTER) {
                this.listener.setGain(volume);
            } else {
                this.instanceToChannel.forEach((p_120280_, p_120281_) -> {
                    float f = this.calculateVolume(p_120280_);
                    p_120281_.execute(p_174990_ -> {
                        if (f <= 0.0F) {
                            p_174990_.stop();
                        } else {
                            p_174990_.setVolume(f);
                        }
                    });
                });
            }
        }
    }

    public void destroy() {
        if (this.loaded) {
            this.stopAll();
            this.soundBuffers.clear();
            this.library.cleanup();
            this.loaded = false;
        }
    }

    public void emergencyShutdown() {
        if (this.loaded) {
            this.library.cleanup();
        }
    }

    /**
     * Stops the provided {@linkplain SoundInstace} from continuing to play.
     */
    public void stop(SoundInstance sound) {
        if (this.loaded) {
            ChannelAccess.ChannelHandle channelaccess$channelhandle = this.instanceToChannel.get(sound);
            if (channelaccess$channelhandle != null) {
                channelaccess$channelhandle.execute(Channel::stop);
            }
        }
    }

    public void stopAll() {
        if (this.loaded) {
            this.executor.flush();
            this.instanceToChannel.values().forEach(p_120288_ -> p_120288_.execute(Channel::stop));
            this.instanceToChannel.clear();
            this.channelAccess.clear();
            this.queuedSounds.clear();
            this.tickingSounds.clear();
            this.instanceBySource.clear();
            this.soundDeleteTime.clear();
            this.queuedTickableSounds.clear();
        }
    }

    public void addEventListener(SoundEventListener listener) {
        this.listeners.add(listener);
    }

    public void removeEventListener(SoundEventListener listener) {
        this.listeners.remove(listener);
    }

    private boolean shouldChangeDevice() {
        if (this.library.isCurrentDeviceDisconnected()) {
            LOGGER.info("Audio device was lost!");
            return true;
        } else {
            long i = Util.getMillis();
            boolean flag = i - this.lastDeviceCheckTime >= 1000L;
            if (flag) {
                this.lastDeviceCheckTime = i;
                if (this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.NO_CHANGE, SoundEngine.DeviceCheckState.ONGOING)) {
                    String s = this.options.soundDevice().get();
                    Util.ioPool().execute(() -> {
                        if ("".equals(s)) {
                            if (this.library.hasDefaultDeviceChanged()) {
                                LOGGER.info("System default audio device has changed!");
                                this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.CHANGE_DETECTED);
                            }
                        } else if (!this.library.getCurrentDeviceName().equals(s) && this.library.getAvailableSoundDevices().contains(s)) {
                            LOGGER.info("Preferred audio device has become available!");
                            this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.CHANGE_DETECTED);
                        }

                        this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.ONGOING, SoundEngine.DeviceCheckState.NO_CHANGE);
                    });
                }
            }

            return this.devicePoolState.compareAndSet(SoundEngine.DeviceCheckState.CHANGE_DETECTED, SoundEngine.DeviceCheckState.NO_CHANGE);
        }
    }

    /**
     * Ticks all active instances of  {@code TickableSoundInstance}
     */
    public void tick(boolean isGamePaused) {
        if (this.shouldChangeDevice()) {
            this.reload();
        }

        if (!isGamePaused) {
            this.tickNonPaused();
        }

        this.channelAccess.scheduleTick();
    }

    private void tickNonPaused() {
        this.tickCount++;
        this.queuedTickableSounds.stream().filter(SoundInstance::canPlaySound).forEach(this::play);
        this.queuedTickableSounds.clear();

        for (TickableSoundInstance tickablesoundinstance : this.tickingSounds) {
            if (!tickablesoundinstance.canPlaySound()) {
                this.stop(tickablesoundinstance);
            }

            tickablesoundinstance.tick();
            if (tickablesoundinstance.isStopped()) {
                this.stop(tickablesoundinstance);
            } else {
                float f = this.calculateVolume(tickablesoundinstance);
                float f1 = this.calculatePitch(tickablesoundinstance);
                Vec3 vec3 = new Vec3(tickablesoundinstance.getX(), tickablesoundinstance.getY(), tickablesoundinstance.getZ());
                ChannelAccess.ChannelHandle channelaccess$channelhandle = this.instanceToChannel.get(tickablesoundinstance);
                if (channelaccess$channelhandle != null) {
                    channelaccess$channelhandle.execute(p_194478_ -> {
                        p_194478_.setVolume(f);
                        p_194478_.setPitch(f1);
                        p_194478_.setSelfPosition(vec3);
                    });
                }
            }
        }

        Iterator<Entry<SoundInstance, ChannelAccess.ChannelHandle>> iterator = this.instanceToChannel.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<SoundInstance, ChannelAccess.ChannelHandle> entry = iterator.next();
            ChannelAccess.ChannelHandle channelaccess$channelhandle1 = entry.getValue();
            SoundInstance soundinstance = entry.getKey();
            float f2 = this.options.getSoundSourceVolume(soundinstance.getSource());
            if (f2 <= 0.0F) {
                channelaccess$channelhandle1.execute(Channel::stop);
                iterator.remove();
            } else if (channelaccess$channelhandle1.isStopped()) {
                int i = this.soundDeleteTime.get(soundinstance);
                if (i <= this.tickCount) {
                    if (shouldLoopManually(soundinstance)) {
                        this.queuedSounds.put(soundinstance, this.tickCount + soundinstance.getDelay());
                    }

                    iterator.remove();
                    LOGGER.debug(MARKER, "Removed channel {} because it's not playing anymore", channelaccess$channelhandle1);
                    this.soundDeleteTime.remove(soundinstance);

                    try {
                        this.instanceBySource.remove(soundinstance.getSource(), soundinstance);
                    } catch (RuntimeException runtimeexception) {
                    }

                    if (soundinstance instanceof TickableSoundInstance) {
                        this.tickingSounds.remove(soundinstance);
                    }
                }
            }
        }

        Iterator<Entry<SoundInstance, Integer>> iterator1 = this.queuedSounds.entrySet().iterator();

        while (iterator1.hasNext()) {
            Entry<SoundInstance, Integer> entry1 = iterator1.next();
            if (this.tickCount >= entry1.getValue()) {
                SoundInstance soundinstance1 = entry1.getKey();
                if (soundinstance1 instanceof TickableSoundInstance) {
                    ((TickableSoundInstance)soundinstance1).tick();
                }

                this.play(soundinstance1);
                iterator1.remove();
            }
        }
    }

    /**
     * {@return Returns {@code true} if the SoundInstance requires manual looping, {@code false} otherwise
     *
     * @param sound the SoundInstance to check
     */
    private static boolean requiresManualLooping(SoundInstance sound) {
        return sound.getDelay() > 0;
    }

    /**
     * @return Returns {@code true} if the SoundInstance should loop manually, {@code false} otherwise
     *
     * @param sound The SoundInstance to check
     */
    private static boolean shouldLoopManually(SoundInstance sound) {
        return sound.isLooping() && requiresManualLooping(sound);
    }

    /**
     * @return Returns {@code true} if the SoundInstance should loop automatically, {@code false} otherwise
     *
     * @param sound The SoundInstance to check
     */
    private static boolean shouldLoopAutomatically(SoundInstance sound) {
        return sound.isLooping() && !requiresManualLooping(sound);
    }

    /**
     * {@return {@code true} if the {@linkplain SoundInstance} is active, {@code false} otherwise}
     *
     * @param sound the SoundInstance to check
     */
    public boolean isActive(SoundInstance sound) {
        if (!this.loaded) {
            return false;
        } else {
            return this.soundDeleteTime.containsKey(sound) && this.soundDeleteTime.get(sound) <= this.tickCount
                ? true
                : this.instanceToChannel.containsKey(sound);
        }
    }

    /**
     * Plays a given sound instance.
     * <p>
     * If the sound engine is not loaded or the sound instance cannot be played, the method returns early.
     * <p>
     * The method fulfills the following parts:
     * <ul>
     *   <li>Performs a series of checks to determine if it can play a sound</li>
     *   <li>Handles the playing of instances of {@code SoundInstance}</li>
     *   <li>Logs potential errors that may have occured</li>
     *   <li>Handles mapping instances of {@code SoundInstance} to specific audio channels</li>
     *   <li>Handles deletion times for active instances of {@code SoundInstance}</li>
     *   <li>Calculates and handles various sound properties such as volume, pitch, attenuation, looping, position and relative, </li>
     * </ul>
     * <p>
     * @implNote This method assumes proper synchronization or that thread confinement mechanisms are in place.
     *
     * @param sound the sound instance to be played.
     */
    public void play(SoundInstance p_sound) {
        if (this.loaded) {
            p_sound = net.neoforged.neoforge.client.ClientHooks.playSound(this, p_sound);
            if (p_sound != null && p_sound.canPlaySound()) {
                WeighedSoundEvents weighedsoundevents = p_sound.resolve(this.soundManager);
                ResourceLocation resourcelocation = p_sound.getLocation();
                if (weighedsoundevents == null) {
                    if (ONLY_WARN_ONCE.add(resourcelocation)) {
                        LOGGER.warn(MARKER, "Unable to play unknown soundEvent: {}", resourcelocation);
                    }
                } else {
                    Sound sound = p_sound.getSound();
                    if (sound != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
                        if (sound == SoundManager.EMPTY_SOUND) {
                            if (ONLY_WARN_ONCE.add(resourcelocation)) {
                                LOGGER.warn(MARKER, "Unable to play empty soundEvent: {}", resourcelocation);
                            }
                        } else {
                            float f = p_sound.getVolume();
                            float f1 = Math.max(f, 1.0F) * (float)sound.getAttenuationDistance();
                            SoundSource soundsource = p_sound.getSource();
                            float f2 = this.calculateVolume(f, soundsource);
                            float f3 = this.calculatePitch(p_sound);
                            SoundInstance.Attenuation soundinstance$attenuation = p_sound.getAttenuation();
                            boolean flag = p_sound.isRelative();
                            if (f2 == 0.0F && !p_sound.canStartSilent()) {
                                LOGGER.debug(MARKER, "Skipped playing sound {}, volume was zero.", sound.getLocation());
                            } else {
                                Vec3 vec3 = new Vec3(p_sound.getX(), p_sound.getY(), p_sound.getZ());
                                if (!this.listeners.isEmpty()) {
                                    float f4 = !flag && soundinstance$attenuation != SoundInstance.Attenuation.NONE ? f1 : Float.POSITIVE_INFINITY;

                                    for (SoundEventListener soundeventlistener : this.listeners) {
                                        soundeventlistener.onPlaySound(p_sound, weighedsoundevents, f4);
                                    }
                                }

                                if (this.listener.getGain() <= 0.0F) {
                                    LOGGER.debug(MARKER, "Skipped playing soundEvent: {}, master volume was zero", resourcelocation);
                                } else {
                                    boolean flag1 = shouldLoopAutomatically(p_sound);
                                    boolean flag2 = sound.shouldStream();
                                    CompletableFuture<ChannelAccess.ChannelHandle> completablefuture = this.channelAccess
                                        .createHandle(sound.shouldStream() ? Library.Pool.STREAMING : Library.Pool.STATIC);
                                    ChannelAccess.ChannelHandle channelaccess$channelhandle = completablefuture.join();
                                    if (channelaccess$channelhandle == null) {
                                        if (SharedConstants.IS_RUNNING_IN_IDE) {
                                            LOGGER.warn("Failed to create new sound handle");
                                        }
                                    } else {
                                        LOGGER.debug(MARKER, "Playing sound {} for event {}", sound.getLocation(), resourcelocation);
                                        this.soundDeleteTime.put(p_sound, this.tickCount + 20);
                                        this.instanceToChannel.put(p_sound, channelaccess$channelhandle);
                                        this.instanceBySource.put(soundsource, p_sound);
                                        channelaccess$channelhandle.execute(p_194488_ -> {
                                            p_194488_.setPitch(f3);
                                            p_194488_.setVolume(f2);
                                            if (soundinstance$attenuation == SoundInstance.Attenuation.LINEAR) {
                                                p_194488_.linearAttenuation(f1);
                                            } else {
                                                p_194488_.disableAttenuation();
                                            }

                                            p_194488_.setLooping(flag1 && !flag2);
                                            p_194488_.setSelfPosition(vec3);
                                            p_194488_.setRelative(flag);
                                        });
                                        final SoundInstance soundinstance = p_sound;
                                        if (!flag2) {
                                            this.soundBuffers
                                                .getCompleteBuffer(sound.getPath())
                                                .thenAccept(p_194501_ -> channelaccess$channelhandle.execute(p_194495_ -> {
                                                        p_194495_.attachStaticBuffer(p_194501_);
                                                        p_194495_.play();
                                                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent(this, soundinstance, p_194495_));
                                                    }));
                                        } else {
                                            soundinstance
                                                .getStream(this.soundBuffers, sound, flag1)
                                                .thenAccept(p_194504_ -> channelaccess$channelhandle.execute(p_194498_ -> {
                                                        p_194498_.attachBufferStream(p_194504_);
                                                        p_194498_.play();
                                                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent(this, soundinstance, p_194498_));
                                                    }));
                                        }

                                        if (p_sound instanceof TickableSoundInstance) {
                                            this.tickingSounds.add((TickableSoundInstance)p_sound);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Queues a new {@linkplain TickingCodeInstance}
     *
     * @param tickableSound the {@linkplain TickableSoundInstance} to queue
     */
    public void queueTickingSound(TickableSoundInstance tickableSound) {
        this.queuedTickableSounds.add(tickableSound);
    }

    /**
     * Requests a specific {@linkplain Sound} instance to be preloaded.
     */
    public void requestPreload(Sound sound) {
        this.preloadQueue.add(sound);
    }

    /**
     * Calculates the pitch of the sound being played.
     * <p>
     * Clamps the sound between 0.5f and 2.0f.
     *
     * @param sound the {@linkplain SoundInstance} being played
     */
    private float calculatePitch(SoundInstance sound) {
        return Mth.clamp(sound.getPitch(), 0.5F, 2.0F);
    }

    /**
     * Calculates the volume for the sound being played.
     * <p>
     * Delegates to {@code #calculateVolume(float, SoundSource)}
     */
    private float calculateVolume(SoundInstance sound) {
        return this.calculateVolume(sound.getVolume(), sound.getSource());
    }

    /**
     * Calculates the volume of the sound being played.
     * <p>
     * Clamps the sound between 0.0f and 1.0f.
     */
    private float calculateVolume(float volumeMultiplier, SoundSource source) {
        return Mth.clamp(volumeMultiplier * this.getVolume(source), 0.0F, 1.0F);
    }

    public void pause() {
        if (this.loaded) {
            this.channelAccess.executeOnChannels(p_194510_ -> p_194510_.forEach(Channel::pause));
        }
    }

    public void resume() {
        if (this.loaded) {
            this.channelAccess.executeOnChannels(p_194508_ -> p_194508_.forEach(Channel::unpause));
        }
    }

    /**
     * Adds a sound to play in n ticks
     */
    public void playDelayed(SoundInstance sound, int delay) {
        this.queuedSounds.put(sound, this.tickCount + delay);
    }

    public void updateSource(Camera renderInfo) {
        if (this.loaded && renderInfo.isInitialized()) {
            ListenerTransform listenertransform = new ListenerTransform(
                renderInfo.getPosition(), new Vec3(renderInfo.getLookVector()), new Vec3(renderInfo.getUpVector())
            );
            this.executor.execute(() -> this.listener.setTransform(listenertransform));
        }
    }

    public void stop(@Nullable ResourceLocation soundName, @Nullable SoundSource category) {
        if (category != null) {
            for (SoundInstance soundinstance : this.instanceBySource.get(category)) {
                if (soundName == null || soundinstance.getLocation().equals(soundName)) {
                    this.stop(soundinstance);
                }
            }
        } else if (soundName == null) {
            this.stopAll();
        } else {
            for (SoundInstance soundinstance1 : this.instanceToChannel.keySet()) {
                if (soundinstance1.getLocation().equals(soundName)) {
                    this.stop(soundinstance1);
                }
            }
        }
    }

    public String getDebugString() {
        return this.library.getDebugString();
    }

    public List<String> getAvailableSoundDevices() {
        return this.library.getAvailableSoundDevices();
    }

    public ListenerTransform getListenerTransform() {
        return this.listener.getTransform();
    }

    @OnlyIn(Dist.CLIENT)
    static enum DeviceCheckState {
        ONGOING,
        CHANGE_DETECTED,
        NO_CHANGE;
    }
}
