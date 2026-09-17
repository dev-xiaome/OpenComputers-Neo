package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class MessageSignatureCache {
    public static final int NOT_FOUND = -1;
    private static final int DEFAULT_CAPACITY = 128;
    private final MessageSignature[] entries;

    public MessageSignatureCache(int size) {
        this.entries = new MessageSignature[size];
    }

    public static MessageSignatureCache createDefault() {
        return new MessageSignatureCache(128);
    }

    public int pack(MessageSignature signature) {
        for (int i = 0; i < this.entries.length; i++) {
            if (signature.equals(this.entries[i])) {
                return i;
            }
        }

        return -1;
    }

    @Nullable
    public MessageSignature unpack(int index) {
        return this.entries[index];
    }

    public void push(SignedMessageBody signedMessageBody, @Nullable MessageSignature signature) {
        List<MessageSignature> list = signedMessageBody.lastSeen().entries();
        ArrayDeque<MessageSignature> arraydeque = new ArrayDeque<>(list.size() + 1);
        arraydeque.addAll(list);
        if (signature != null) {
            arraydeque.add(signature);
        }

        this.push(arraydeque);
    }

    @VisibleForTesting
    void push(List<MessageSignature> chatMessages) {
        this.push(new ArrayDeque<>(chatMessages));
    }

    private void push(ArrayDeque<MessageSignature> deque) {
        Set<MessageSignature> set = new ObjectOpenHashSet<>(deque);

        for (int i = 0; !deque.isEmpty() && i < this.entries.length; i++) {
            MessageSignature messagesignature = this.entries[i];
            this.entries[i] = deque.removeLast();
            if (messagesignature != null && !set.contains(messagesignature)) {
                deque.addFirst(messagesignature);
            }
        }
    }
}
