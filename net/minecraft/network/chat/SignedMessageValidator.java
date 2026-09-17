package net.minecraft.network.chat;

import com.mojang.logging.LogUtils;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.util.SignatureValidator;
import org.slf4j.Logger;

@FunctionalInterface
public interface SignedMessageValidator {
    Logger LOGGER = LogUtils.getLogger();
    SignedMessageValidator ACCEPT_UNSIGNED = PlayerChatMessage::removeSignature;
    SignedMessageValidator REJECT_ALL = p_314910_ -> {
        LOGGER.error("Received chat message from {}, but they have no chat session initialized and secure chat is enforced", p_314910_.sender());
        return null;
    };

    @Nullable
    PlayerChatMessage updateAndValidate(PlayerChatMessage message);

    public static class KeyBased implements SignedMessageValidator {
        private final SignatureValidator validator;
        private final BooleanSupplier expired;
        @Nullable
        private PlayerChatMessage lastMessage;
        private boolean isChainValid = true;

        public KeyBased(SignatureValidator validator, BooleanSupplier expired) {
            this.validator = validator;
            this.expired = expired;
        }

        private boolean validateChain(PlayerChatMessage message) {
            if (message.equals(this.lastMessage)) {
                return true;
            } else if (this.lastMessage != null && !message.link().isDescendantOf(this.lastMessage.link())) {
                LOGGER.error(
                    "Received out-of-order chat message from {}: expected index > {} for session {}, but was {} for session {}",
                    message.sender(),
                    this.lastMessage.link().index(),
                    this.lastMessage.link().sessionId(),
                    message.link().index(),
                    message.link().sessionId()
                );
                return false;
            } else {
                return true;
            }
        }

        private boolean validate(PlayerChatMessage message) {
            if (this.expired.getAsBoolean()) {
                LOGGER.error("Received message from player with expired profile public key: {}", message);
                return false;
            } else if (!message.verify(this.validator)) {
                LOGGER.error("Received message with invalid signature from {}", message.sender());
                return false;
            } else {
                return this.validateChain(message);
            }
        }

        @Nullable
        @Override
        public PlayerChatMessage updateAndValidate(PlayerChatMessage message) {
            this.isChainValid = this.isChainValid && this.validate(message);
            if (!this.isChainValid) {
                return null;
            } else {
                this.lastMessage = message;
                return message;
            }
        }
    }
}
