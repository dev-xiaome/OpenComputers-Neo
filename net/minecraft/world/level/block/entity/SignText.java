package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;

public class SignText {
    private static final Codec<Component[]> LINES_CODEC = ComponentSerialization.FLAT_CODEC
        .listOf()
        .comapFlatMap(
            p_337999_ -> Util.fixedSize((List<Component>)p_337999_, 4)
                    .map(p_277881_ -> new Component[]{p_277881_.get(0), p_277881_.get(1), p_277881_.get(2), p_277881_.get(3)}),
            p_277460_ -> List.of(p_277460_[0], p_277460_[1], p_277460_[2], p_277460_[3])
        );
    public static final Codec<SignText> DIRECT_CODEC = RecordCodecBuilder.create(
        p_338000_ -> p_338000_.group(
                    LINES_CODEC.fieldOf("messages").forGetter(p_277822_ -> p_277822_.messages),
                    LINES_CODEC.lenientOptionalFieldOf("filtered_messages").forGetter(SignText::filteredMessages),
                    DyeColor.CODEC.fieldOf("color").orElse(DyeColor.BLACK).forGetter(p_277343_ -> p_277343_.color),
                    Codec.BOOL.fieldOf("has_glowing_text").orElse(false).forGetter(p_277555_ -> p_277555_.hasGlowingText)
                )
                .apply(p_338000_, SignText::load)
    );
    public static final int LINES = 4;
    private final Component[] messages;
    private final Component[] filteredMessages;
    private final DyeColor color;
    private final boolean hasGlowingText;
    @Nullable
    private FormattedCharSequence[] renderMessages;
    private boolean renderMessagedFiltered;

    public SignText() {
        this(emptyMessages(), emptyMessages(), DyeColor.BLACK, false);
    }

    public SignText(Component[] messages, Component[] filteredMessages, DyeColor color, boolean hasGlowingText) {
        this.messages = messages;
        this.filteredMessages = filteredMessages;
        this.color = color;
        this.hasGlowingText = hasGlowingText;
    }

    private static Component[] emptyMessages() {
        return new Component[]{CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY};
    }

    private static SignText load(Component[] messages, Optional<Component[]> filteredMessages, DyeColor color, boolean hasGlowingText) {
        return new SignText(messages, filteredMessages.orElse(Arrays.copyOf(messages, messages.length)), color, hasGlowingText);
    }

    public boolean hasGlowingText() {
        return this.hasGlowingText;
    }

    public SignText setHasGlowingText(boolean hasGlowingText) {
        return hasGlowingText == this.hasGlowingText ? this : new SignText(this.messages, this.filteredMessages, this.color, hasGlowingText);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public SignText setColor(DyeColor color) {
        return color == this.getColor() ? this : new SignText(this.messages, this.filteredMessages, color, this.hasGlowingText);
    }

    public Component getMessage(int index, boolean isFiltered) {
        return this.getMessages(isFiltered)[index];
    }

    public SignText setMessage(int index, Component text) {
        return this.setMessage(index, text, text);
    }

    public SignText setMessage(int index, Component text, Component filteredText) {
        Component[] acomponent = Arrays.copyOf(this.messages, this.messages.length);
        Component[] acomponent1 = Arrays.copyOf(this.filteredMessages, this.filteredMessages.length);
        acomponent[index] = text;
        acomponent1[index] = filteredText;
        return new SignText(acomponent, acomponent1, this.color, this.hasGlowingText);
    }

    public boolean hasMessage(Player player) {
        return Arrays.stream(this.getMessages(player.isTextFilteringEnabled())).anyMatch(p_277499_ -> !p_277499_.getString().isEmpty());
    }

    public Component[] getMessages(boolean isFiltered) {
        return isFiltered ? this.filteredMessages : this.messages;
    }

    public FormattedCharSequence[] getRenderMessages(boolean renderMessagesFiltered, Function<Component, FormattedCharSequence> formatter) {
        if (this.renderMessages == null || this.renderMessagedFiltered != renderMessagesFiltered) {
            this.renderMessagedFiltered = renderMessagesFiltered;
            this.renderMessages = new FormattedCharSequence[4];

            for (int i = 0; i < 4; i++) {
                this.renderMessages[i] = formatter.apply(this.getMessage(i, renderMessagesFiltered));
            }
        }

        return this.renderMessages;
    }

    private Optional<Component[]> filteredMessages() {
        for (int i = 0; i < 4; i++) {
            if (!this.filteredMessages[i].equals(this.messages[i])) {
                return Optional.of(this.filteredMessages);
            }
        }

        return Optional.empty();
    }

    public boolean hasAnyClickCommands(Player player) {
        for (Component component : this.getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickevent = style.getClickEvent();
            if (clickevent != null && clickevent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                return true;
            }
        }

        return false;
    }
}
