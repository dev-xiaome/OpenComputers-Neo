package net.minecraft.client.multiplayer.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChatLog {
    private final LoggedChatEvent[] buffer;
    private int nextId;

    public static Codec<ChatLog> codec(int size) {
        return Codec.list(LoggedChatEvent.CODEC)
            .comapFlatMap(
                p_274704_ -> {
                    int i = p_274704_.size();
                    return i > size
                        ? DataResult.error(
                            () -> "Expected: a buffer of size less than or equal to " + size + " but: " + i + " is greater than " + size
                        )
                        : DataResult.success(new ChatLog(size, (List<LoggedChatEvent>)p_274704_));
                },
                ChatLog::loggedChatEvents
            );
    }

    public ChatLog(int size) {
        this.buffer = new LoggedChatEvent[size];
    }

    private ChatLog(int size, List<LoggedChatEvent> events) {
        this.buffer = events.toArray(LoggedChatEvent[]::new);
        this.nextId = events.size();
    }

    private List<LoggedChatEvent> loggedChatEvents() {
        List<LoggedChatEvent> list = new ArrayList<>(this.size());

        for (int i = this.start(); i <= this.end(); i++) {
            list.add(this.lookup(i));
        }

        return list;
    }

    public void push(LoggedChatEvent event) {
        this.buffer[this.index(this.nextId++)] = event;
    }

    @Nullable
    public LoggedChatEvent lookup(int id) {
        return id >= this.start() && id <= this.end() ? this.buffer[this.index(id)] : null;
    }

    private int index(int index) {
        return index % this.buffer.length;
    }

    public int start() {
        return Math.max(this.nextId - this.buffer.length, 0);
    }

    public int end() {
        return this.nextId - 1;
    }

    private int size() {
        return this.end() - this.start() + 1;
    }
}
