package net.minecraft.client.multiplayer.chat;

import com.google.common.collect.Queues;
import com.mojang.authlib.GameProfile;
import java.time.Instant;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.util.StringDecomposer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class ChatListener {
    private static final Component CHAT_VALIDATION_ERROR = Component.translatable("chat.validation_error").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC);
    private final Minecraft minecraft;
    private final Deque<ChatListener.Message> delayedMessageQueue = Queues.newArrayDeque();
    private long messageDelay;
    private long previousMessageTime;

    public ChatListener(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void tick() {
        if (this.messageDelay != 0L) {
            if (Util.getMillis() >= this.previousMessageTime + this.messageDelay) {
                ChatListener.Message chatlistener$message = this.delayedMessageQueue.poll();

                while (chatlistener$message != null && !chatlistener$message.accept()) {
                    chatlistener$message = this.delayedMessageQueue.poll();
                }
            }
        }
    }

    public void setMessageDelay(double delaySeconds) {
        long i = (long)(delaySeconds * 1000.0);
        if (i == 0L && this.messageDelay > 0L) {
            this.delayedMessageQueue.forEach(ChatListener.Message::accept);
            this.delayedMessageQueue.clear();
        }

        this.messageDelay = i;
    }

    public void acceptNextDelayedMessage() {
        this.delayedMessageQueue.remove().accept();
    }

    public long queueSize() {
        return (long)this.delayedMessageQueue.size();
    }

    public void clearQueue() {
        this.delayedMessageQueue.forEach(ChatListener.Message::accept);
        this.delayedMessageQueue.clear();
    }

    public boolean removeFromDelayedMessageQueue(MessageSignature signature) {
        return this.delayedMessageQueue.removeIf(p_247887_ -> signature.equals(p_247887_.signature()));
    }

    private boolean willDelayMessages() {
        return this.messageDelay > 0L && Util.getMillis() < this.previousMessageTime + this.messageDelay;
    }

    private void handleMessage(@Nullable MessageSignature signature, BooleanSupplier handler) {
        if (this.willDelayMessages()) {
            this.delayedMessageQueue.add(new ChatListener.Message(signature, handler));
        } else {
            handler.getAsBoolean();
        }
    }

    public void handlePlayerChatMessage(PlayerChatMessage chatMessage, GameProfile gameProfile, ChatType.Bound boundChatType) {
        boolean flag = this.minecraft.options.onlyShowSecureChat().get();
        PlayerChatMessage playerchatmessage = flag ? chatMessage.removeUnsignedContent() : chatMessage;
        Component component = boundChatType.decorate(playerchatmessage.decoratedContent());
        Instant instant = Instant.now();
        this.handleMessage(chatMessage.signature(), () -> {
            boolean flag1 = this.showMessageToPlayer(boundChatType, chatMessage, component, gameProfile, flag, instant);
            ClientPacketListener clientpacketlistener = this.minecraft.getConnection();
            if (clientpacketlistener != null) {
                clientpacketlistener.markMessageAsProcessed(chatMessage, flag1);
            }

            return flag1;
        });
    }

    public void handleChatMessageError(UUID sender, ChatType.Bound boundChatType) {
        this.handleMessage(null, () -> {
            if (this.minecraft.isBlocked(sender)) {
                return false;
            } else {
                Component component = boundChatType.decorate(CHAT_VALIDATION_ERROR);
                this.minecraft.gui.getChat().addMessage(component, null, GuiMessageTag.chatError());
                this.previousMessageTime = Util.getMillis();
                return true;
            }
        });
    }

    public void handleDisguisedChatMessage(Component message, ChatType.Bound boundChatType) {
        Instant instant = Instant.now();
        this.handleMessage(null, () -> {
            Component component = boundChatType.decorate(message);
            Component forgeComponent = net.neoforged.neoforge.client.ClientHooks.onClientChat(boundChatType, component, Util.NIL_UUID);
            if (forgeComponent == null) return false;
            this.minecraft.gui.getChat().addMessage(forgeComponent);
            this.narrateChatMessage(boundChatType, message);
            this.logSystemMessage(component, instant);
            this.previousMessageTime = Util.getMillis();
            return true;
        });
    }

    private boolean showMessageToPlayer(
        ChatType.Bound boundChatType, PlayerChatMessage chatMessage, Component decoratedServerContent, GameProfile gameProfile, boolean onlyShowSecureChat, Instant timestamp
    ) {
        ChatTrustLevel chattrustlevel = this.evaluateTrustLevel(chatMessage, decoratedServerContent, timestamp);
        if (onlyShowSecureChat && chattrustlevel.isNotSecure()) {
            return false;
        } else if (!this.minecraft.isBlocked(chatMessage.sender()) && !chatMessage.isFullyFiltered()) {
            GuiMessageTag guimessagetag = chattrustlevel.createTag(chatMessage);
            MessageSignature messagesignature = chatMessage.signature();
            FilterMask filtermask = chatMessage.filterMask();
            if (filtermask.isEmpty()) {
                Component forgeComponent = net.neoforged.neoforge.client.ClientHooks.onClientPlayerChat(boundChatType, decoratedServerContent, chatMessage, chatMessage.sender());
                if (forgeComponent == null) return false;
                this.minecraft.gui.getChat().addMessage(forgeComponent, messagesignature, guimessagetag);
                this.narrateChatMessage(boundChatType, chatMessage.decoratedContent());
            } else {
                Component component = filtermask.applyWithFormatting(chatMessage.signedContent());
                if (component != null) {
                    Component forgeComponent = net.neoforged.neoforge.client.ClientHooks.onClientPlayerChat(boundChatType, boundChatType.decorate(component), chatMessage, chatMessage.sender());
                    if (forgeComponent == null) return false;
                    this.minecraft.gui.getChat().addMessage(forgeComponent, messagesignature, guimessagetag);
                    this.narrateChatMessage(boundChatType, component);
                }
            }

            this.logPlayerMessage(chatMessage, boundChatType, gameProfile, chattrustlevel);
            this.previousMessageTime = Util.getMillis();
            return true;
        } else {
            return false;
        }
    }

    private void narrateChatMessage(ChatType.Bound boundChatType, Component message) {
        this.minecraft.getNarrator().sayChat(boundChatType.decorateNarration(message));
    }

    private ChatTrustLevel evaluateTrustLevel(PlayerChatMessage chatMessage, Component decoratedServerContent, Instant timestamp) {
        return this.isSenderLocalPlayer(chatMessage.sender()) ? ChatTrustLevel.SECURE : ChatTrustLevel.evaluate(chatMessage, decoratedServerContent, timestamp);
    }

    private void logPlayerMessage(PlayerChatMessage message, ChatType.Bound boundChatType, GameProfile gameProfile, ChatTrustLevel trustLevel) {
        ChatLog chatlog = this.minecraft.getReportingContext().chatLog();
        chatlog.push(LoggedChatMessage.player(gameProfile, message, trustLevel));
    }

    private void logSystemMessage(Component message, Instant timestamp) {
        ChatLog chatlog = this.minecraft.getReportingContext().chatLog();
        chatlog.push(LoggedChatMessage.system(message, timestamp));
    }

    public void handleSystemMessage(Component message, boolean isOverlay) {
        if (!this.minecraft.options.hideMatchedNames().get() || !this.minecraft.isBlocked(this.guessChatUUID(message))) {
            message = net.neoforged.neoforge.client.ClientHooks.onClientSystemChat(message, isOverlay);
            if (message == null) return;
            if (isOverlay) {
                this.minecraft.gui.setOverlayMessage(message, false);
            } else {
                this.minecraft.gui.getChat().addMessage(message);
                this.logSystemMessage(message, Instant.now());
            }

            this.minecraft.getNarrator().say(message);
        }
    }

    private UUID guessChatUUID(Component message) {
        String s = StringDecomposer.getPlainText(message);
        String s1 = StringUtils.substringBetween(s, "<", ">");
        return s1 == null ? Util.NIL_UUID : this.minecraft.getPlayerSocialManager().getDiscoveredUUID(s1);
    }

    private boolean isSenderLocalPlayer(UUID sender) {
        if (this.minecraft.isLocalServer() && this.minecraft.player != null) {
            UUID uuid = this.minecraft.player.getGameProfile().getId();
            return uuid.equals(sender);
        } else {
            return false;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record Message(@Nullable MessageSignature signature, BooleanSupplier handler) {
        public boolean accept() {
            return this.handler.getAsBoolean();
        }
    }
}
