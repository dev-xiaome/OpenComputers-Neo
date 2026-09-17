package net.minecraft.client.sounds;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The ChannelAccess class provides access to channels for playing audio data using a given library and executor.
 */
@OnlyIn(Dist.CLIENT)
public class ChannelAccess {
    private final Set<ChannelAccess.ChannelHandle> channels = Sets.newIdentityHashSet();
    final Library library;
    final Executor executor;

    public ChannelAccess(Library library, Executor executor) {
        this.library = library;
        this.executor = executor;
    }

    /**
     * Creates a new channel handle for the specified system mode and returns a CompletableFuture that completes with the handle when it is created.
     * <p>
     * @return a CompletableFuture that completes with the channel handle when it is created, or null if it cannot be created
     *
     * @param systemMode systemMode the system mode to create the channel handle for
     */
    public CompletableFuture<ChannelAccess.ChannelHandle> createHandle(Library.Pool systemMode) {
        CompletableFuture<ChannelAccess.ChannelHandle> completablefuture = new CompletableFuture<>();
        this.executor.execute(() -> {
            Channel channel = this.library.acquireChannel(systemMode);
            if (channel != null) {
                ChannelAccess.ChannelHandle channelaccess$channelhandle = new ChannelAccess.ChannelHandle(channel);
                this.channels.add(channelaccess$channelhandle);
                completablefuture.complete(channelaccess$channelhandle);
            } else {
                completablefuture.complete(null);
            }
        });
        return completablefuture;
    }

    /**
     * @param sourceStreamConsumer the consumer to execute on the stream of channels
     */
    public void executeOnChannels(Consumer<Stream<Channel>> sourceStreamConsumer) {
        this.executor.execute(() -> sourceStreamConsumer.accept(this.channels.stream().map(p_174978_ -> p_174978_.channel).filter(Objects::nonNull)));
    }

    public void scheduleTick() {
        this.executor.execute(() -> {
            Iterator<ChannelAccess.ChannelHandle> iterator = this.channels.iterator();

            while (iterator.hasNext()) {
                ChannelAccess.ChannelHandle channelaccess$channelhandle = iterator.next();
                channelaccess$channelhandle.channel.updateStream();
                if (channelaccess$channelhandle.channel.stopped()) {
                    channelaccess$channelhandle.release();
                    iterator.remove();
                }
            }
        });
    }

    public void clear() {
        this.channels.forEach(ChannelAccess.ChannelHandle::release);
        this.channels.clear();
    }

    /**
     * Represents a handle to a channel.
     */
    @OnlyIn(Dist.CLIENT)
    public class ChannelHandle {
        @Nullable
        Channel channel;
        private boolean stopped;

        public boolean isStopped() {
            return this.stopped;
        }

        public ChannelHandle(Channel channel) {
            this.channel = channel;
        }

        public void execute(Consumer<Channel> soundConsumer) {
            ChannelAccess.this.executor.execute(() -> {
                if (this.channel != null) {
                    soundConsumer.accept(this.channel);
                }
            });
        }

        public void release() {
            this.stopped = true;
            ChannelAccess.this.library.releaseChannel(this.channel);
            this.channel = null;
        }
    }
}
