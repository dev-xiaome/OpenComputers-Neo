package net.minecraft.network.chat;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Optional;
import javax.annotation.Nullable;

public class LastSeenMessagesValidator {
    private final int lastSeenCount;
    private final ObjectList<LastSeenTrackedEntry> trackedMessages = new ObjectArrayList<>();
    @Nullable
    private MessageSignature lastPendingMessage;

    public LastSeenMessagesValidator(int lastSeenCount) {
        this.lastSeenCount = lastSeenCount;

        for (int i = 0; i < lastSeenCount; i++) {
            this.trackedMessages.add(null);
        }
    }

    public void addPending(MessageSignature signature) {
        if (!signature.equals(this.lastPendingMessage)) {
            this.trackedMessages.add(new LastSeenTrackedEntry(signature, true));
            this.lastPendingMessage = signature;
        }
    }

    public int trackedMessagesCount() {
        return this.trackedMessages.size();
    }

    public boolean applyOffset(int offset) {
        int i = this.trackedMessages.size() - this.lastSeenCount;
        if (offset >= 0 && offset <= i) {
            this.trackedMessages.removeElements(0, offset);
            return true;
        } else {
            return false;
        }
    }

    public Optional<LastSeenMessages> applyUpdate(LastSeenMessages.Update lastSeenUpdater) {
        if (!this.applyOffset(lastSeenUpdater.offset())) {
            return Optional.empty();
        } else {
            ObjectList<MessageSignature> objectlist = new ObjectArrayList<>(lastSeenUpdater.acknowledged().cardinality());
            if (lastSeenUpdater.acknowledged().length() > this.lastSeenCount) {
                return Optional.empty();
            } else {
                for (int i = 0; i < this.lastSeenCount; i++) {
                    boolean flag = lastSeenUpdater.acknowledged().get(i);
                    LastSeenTrackedEntry lastseentrackedentry = this.trackedMessages.get(i);
                    if (flag) {
                        if (lastseentrackedentry == null) {
                            return Optional.empty();
                        }

                        this.trackedMessages.set(i, lastseentrackedentry.acknowledge());
                        objectlist.add(lastseentrackedentry.signature());
                    } else {
                        if (lastseentrackedentry != null && !lastseentrackedentry.pending()) {
                            return Optional.empty();
                        }

                        this.trackedMessages.set(i, null);
                    }
                }

                return Optional.of(new LastSeenMessages(objectlist));
            }
        }
    }
}
