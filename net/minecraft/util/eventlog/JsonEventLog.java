package net.minecraft.util.eventlog;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

public class JsonEventLog<T> implements Closeable {
    private static final Gson GSON = new Gson();
    private final Codec<T> codec;
    final FileChannel channel;
    private final AtomicInteger referenceCount = new AtomicInteger(1);

    public JsonEventLog(Codec<T> codec, FileChannel channel) {
        this.codec = codec;
        this.channel = channel;
    }

    public static <T> JsonEventLog<T> open(Codec<T> codec, Path path) throws IOException {
        FileChannel filechannel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE);
        return new JsonEventLog<>(codec, filechannel);
    }

    public void write(T data) throws IOException {
        JsonElement jsonelement = this.codec.encodeStart(JsonOps.INSTANCE, data).getOrThrow(IOException::new);
        this.channel.position(this.channel.size());
        Writer writer = Channels.newWriter(this.channel, StandardCharsets.UTF_8);
        GSON.toJson(jsonelement, GSON.newJsonWriter(writer));
        writer.write(10);
        writer.flush();
    }

    public JsonEventLogReader<T> openReader() throws IOException {
        if (this.referenceCount.get() <= 0) {
            throw new IOException("Event log has already been closed");
        } else {
            this.referenceCount.incrementAndGet();
            final JsonEventLogReader<T> jsoneventlogreader = JsonEventLogReader.create(this.codec, Channels.newReader(this.channel, StandardCharsets.UTF_8));
            return new JsonEventLogReader<T>() {
                private volatile long position;

                @Nullable
                @Override
                public T next() throws IOException {
                    Object object;
                    try {
                        JsonEventLog.this.channel.position(this.position);
                        object = jsoneventlogreader.next();
                    } finally {
                        this.position = JsonEventLog.this.channel.position();
                    }

                    return (T)object;
                }

                @Override
                public void close() throws IOException {
                    JsonEventLog.this.releaseReference();
                }
            };
        }
    }

    @Override
    public void close() throws IOException {
        this.releaseReference();
    }

    void releaseReference() throws IOException {
        if (this.referenceCount.decrementAndGet() <= 0) {
            this.channel.close();
        }
    }
}
