package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;

public class MessageArgument implements SignedArgument<MessageArgument.Message> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Hello world!", "foo", "@e", "Hello @p :)");
    static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType(
        (p_341561_, p_341562_) -> Component.translatableEscape("argument.message.too_long", p_341561_, p_341562_)
    );

    public static MessageArgument message() {
        return new MessageArgument();
    }

    public static Component getMessage(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        MessageArgument.Message messageargument$message = context.getArgument(name, MessageArgument.Message.class);
        return messageargument$message.resolveComponent(context.getSource());
    }

    public static void resolveChatMessage(CommandContext<CommandSourceStack> context, String key, Consumer<PlayerChatMessage> callback) throws CommandSyntaxException {
        MessageArgument.Message messageargument$message = context.getArgument(key, MessageArgument.Message.class);
        CommandSourceStack commandsourcestack = context.getSource();
        Component component = messageargument$message.resolveComponent(commandsourcestack);
        CommandSigningContext commandsigningcontext = commandsourcestack.getSigningContext();
        PlayerChatMessage playerchatmessage = commandsigningcontext.getArgument(key);
        if (playerchatmessage != null) {
            resolveSignedMessage(callback, commandsourcestack, playerchatmessage.withUnsignedContent(component));
        } else {
            resolveDisguisedMessage(callback, commandsourcestack, PlayerChatMessage.system(messageargument$message.text).withUnsignedContent(component));
        }
    }

    private static void resolveSignedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        MinecraftServer minecraftserver = source.getServer();
        CompletableFuture<FilteredText> completablefuture = filterPlainText(source, message);
        Component component = minecraftserver.getChatDecorator().decorate(source.getPlayer(), message.decoratedContent());
        source.getChatMessageChainer().append(completablefuture, p_300688_ -> {
            PlayerChatMessage playerchatmessage = message.withUnsignedContent(component).filter(p_300688_.mask());
            callback.accept(playerchatmessage);
        });
    }

    private static void resolveDisguisedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        ChatDecorator chatdecorator = source.getServer().getChatDecorator();
        Component component = chatdecorator.decorate(source.getPlayer(), message.decoratedContent());
        callback.accept(message.withUnsignedContent(component));
    }

    private static CompletableFuture<FilteredText> filterPlainText(CommandSourceStack source, PlayerChatMessage message) {
        ServerPlayer serverplayer = source.getPlayer();
        return serverplayer != null && message.hasSignatureFrom(serverplayer.getUUID())
            ? serverplayer.getTextFilter().processStreamMessage(message.signedContent())
            : CompletableFuture.completedFuture(FilteredText.passThrough(message.signedContent()));
    }

    public MessageArgument.Message parse(StringReader reader) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(reader, true);
    }

    public <S> MessageArgument.Message parse(StringReader reader, @Nullable S p_353119_) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(reader, EntitySelectorParser.allowSelectors(p_353119_));
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static record Message(String text, MessageArgument.Part[] parts) {
        Component resolveComponent(CommandSourceStack source) throws CommandSyntaxException {
            return this.toComponent(source, net.neoforged.neoforge.common.CommonHooks.canUseEntitySelectors(source));
        }

        /**
         * Converts this message into a text component, replacing any selectors in the text with the actual evaluated selector.
         */
        public Component toComponent(CommandSourceStack source, boolean allowSelectors) throws CommandSyntaxException {
            if (this.parts.length != 0 && allowSelectors) {
                MutableComponent mutablecomponent = Component.literal(this.text.substring(0, this.parts[0].start()));
                int i = this.parts[0].start();

                for (MessageArgument.Part messageargument$part : this.parts) {
                    Component component = messageargument$part.toComponent(source);
                    if (i < messageargument$part.start()) {
                        mutablecomponent.append(this.text.substring(i, messageargument$part.start()));
                    }

                    mutablecomponent.append(component);
                    i = messageargument$part.end();
                }

                if (i < this.text.length()) {
                    mutablecomponent.append(this.text.substring(i));
                }

                return mutablecomponent;
            } else {
                return Component.literal(this.text);
            }
        }

        /**
         * Parses a message. The algorithm for this is simply to run through and look for selectors, ignoring any invalid selectors in the text (since players may type e.g. "[@]").
         */
        public static MessageArgument.Message parseText(StringReader reader, boolean allowSelectors) throws CommandSyntaxException {
            if (reader.getRemainingLength() > 256) {
                throw MessageArgument.TOO_LONG.create(reader.getRemainingLength(), 256);
            } else {
                String s = reader.getRemaining();
                if (!allowSelectors) {
                    reader.setCursor(reader.getTotalLength());
                    return new MessageArgument.Message(s, new MessageArgument.Part[0]);
                } else {
                    List<MessageArgument.Part> list = Lists.newArrayList();
                    int i = reader.getCursor();

                    while (true) {
                        int j;
                        EntitySelector entityselector;
                        while (true) {
                            if (!reader.canRead()) {
                                return new MessageArgument.Message(s, list.toArray(new MessageArgument.Part[0]));
                            }

                            if (reader.peek() == '@') {
                                j = reader.getCursor();

                                try {
                                    EntitySelectorParser entityselectorparser = new EntitySelectorParser(reader, true);
                                    entityselector = entityselectorparser.parse();
                                    break;
                                } catch (CommandSyntaxException commandsyntaxexception) {
                                    if (commandsyntaxexception.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE
                                        && commandsyntaxexception.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) {
                                        throw commandsyntaxexception;
                                    }

                                    reader.setCursor(j + 1);
                                }
                            } else {
                                reader.skip();
                            }
                        }

                        list.add(new MessageArgument.Part(j - i, reader.getCursor() - i, entityselector));
                    }
                }
            }
        }
    }

    public static record Part(int start, int end, EntitySelector selector) {
        /**
         * Runs the selector and returns the component produced by it. This method does not actually appear to ever return null.
         */
        public Component toComponent(CommandSourceStack source) throws CommandSyntaxException {
            return EntitySelector.joinNames(this.selector.findEntities(source));
        }
    }
}
