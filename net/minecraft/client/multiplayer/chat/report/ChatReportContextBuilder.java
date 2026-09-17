package net.minecraft.client.multiplayer.chat.report;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.client.multiplayer.chat.LoggedChatEvent;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChatReportContextBuilder {
    final int leadingCount;
    private final List<ChatReportContextBuilder.Collector> activeCollectors = new ArrayList<>();

    public ChatReportContextBuilder(int leadingCount) {
        this.leadingCount = leadingCount;
    }

    public void collectAllContext(ChatLog chatLog, IntCollection reportedMessages, ChatReportContextBuilder.Handler handler) {
        IntSortedSet intsortedset = new IntRBTreeSet(reportedMessages);

        for (int i = intsortedset.lastInt(); i >= chatLog.start() && (this.isActive() || !intsortedset.isEmpty()); i--) {
            LoggedChatEvent $$6 = chatLog.lookup(i);
            if ($$6 instanceof LoggedChatMessage.Player) {
                LoggedChatMessage.Player loggedchatmessage$player = (LoggedChatMessage.Player)$$6;
                boolean flag = this.acceptContext(loggedchatmessage$player.message());
                if (intsortedset.remove(i)) {
                    this.trackContext(loggedchatmessage$player.message());
                    handler.accept(i, loggedchatmessage$player);
                } else if (flag) {
                    handler.accept(i, loggedchatmessage$player);
                }
            }
        }
    }

    public void trackContext(PlayerChatMessage lastChainMessage) {
        this.activeCollectors.add(new ChatReportContextBuilder.Collector(lastChainMessage));
    }

    public boolean acceptContext(PlayerChatMessage lastChainMessage) {
        boolean flag = false;
        Iterator<ChatReportContextBuilder.Collector> iterator = this.activeCollectors.iterator();

        while (iterator.hasNext()) {
            ChatReportContextBuilder.Collector chatreportcontextbuilder$collector = iterator.next();
            if (chatreportcontextbuilder$collector.accept(lastChainMessage)) {
                flag = true;
                if (chatreportcontextbuilder$collector.isComplete()) {
                    iterator.remove();
                }
            }
        }

        return flag;
    }

    public boolean isActive() {
        return !this.activeCollectors.isEmpty();
    }

    @OnlyIn(Dist.CLIENT)
    class Collector {
        private final Set<MessageSignature> lastSeenSignatures;
        private PlayerChatMessage lastChainMessage;
        private boolean collectingChain = true;
        private int count;

        Collector(PlayerChatMessage lastChainMessage) {
            this.lastSeenSignatures = new ObjectOpenHashSet<>(lastChainMessage.signedBody().lastSeen().entries());
            this.lastChainMessage = lastChainMessage;
        }

        boolean accept(PlayerChatMessage message) {
            if (message.equals(this.lastChainMessage)) {
                return false;
            } else {
                boolean flag = this.lastSeenSignatures.remove(message.signature());
                if (this.collectingChain && this.lastChainMessage.sender().equals(message.sender())) {
                    if (this.lastChainMessage.link().isDescendantOf(message.link())) {
                        flag = true;
                        this.lastChainMessage = message;
                    } else {
                        this.collectingChain = false;
                    }
                }

                if (flag) {
                    this.count++;
                }

                return flag;
            }
        }

        boolean isComplete() {
            return this.count >= ChatReportContextBuilder.this.leadingCount || !this.collectingChain && this.lastSeenSignatures.isEmpty();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public interface Handler {
        void accept(int index, LoggedChatMessage.Player player);
    }
}
