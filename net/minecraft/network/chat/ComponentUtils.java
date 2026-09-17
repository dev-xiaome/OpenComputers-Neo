package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;

public class ComponentUtils {
    public static final String DEFAULT_SEPARATOR_TEXT = ", ";
    public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
    public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");

    /**
     * Merge the component's styles with the given Style.
     */
    public static MutableComponent mergeStyles(MutableComponent component, Style p_style) {
        if (p_style.isEmpty()) {
            return component;
        } else {
            Style style = component.getStyle();
            if (style.isEmpty()) {
                return component.setStyle(p_style);
            } else {
                return style.equals(p_style) ? component : component.setStyle(style.applyTo(p_style));
            }
        }
    }

    public static Optional<MutableComponent> updateForEntity(
        @Nullable CommandSourceStack commandSourceStack, Optional<Component> optionalComponent, @Nullable Entity entity, int recursionDepth
    ) throws CommandSyntaxException {
        return optionalComponent.isPresent() ? Optional.of(updateForEntity(commandSourceStack, optionalComponent.get(), entity, recursionDepth)) : Optional.empty();
    }

    public static MutableComponent updateForEntity(@Nullable CommandSourceStack commandSourceStack, Component p_component, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        if (recursionDepth > 100) {
            return p_component.copy();
        } else {
            MutableComponent mutablecomponent = p_component.getContents().resolve(commandSourceStack, entity, recursionDepth + 1);

            for (Component component : p_component.getSiblings()) {
                mutablecomponent.append(updateForEntity(commandSourceStack, component, entity, recursionDepth + 1));
            }

            return mutablecomponent.withStyle(resolveStyle(commandSourceStack, p_component.getStyle(), entity, recursionDepth));
        }
    }

    private static Style resolveStyle(@Nullable CommandSourceStack commandSourceStack, Style style, @Nullable Entity entity, int recursionDepth) throws CommandSyntaxException {
        HoverEvent hoverevent = style.getHoverEvent();
        if (hoverevent != null) {
            Component component = hoverevent.getValue(HoverEvent.Action.SHOW_TEXT);
            if (component != null) {
                HoverEvent hoverevent1 = new HoverEvent(HoverEvent.Action.SHOW_TEXT, updateForEntity(commandSourceStack, component, entity, recursionDepth + 1));
                return style.withHoverEvent(hoverevent1);
            }
        }

        return style;
    }

    public static Component formatList(Collection<String> elements) {
        return formatAndSortList(elements, p_130742_ -> Component.literal(p_130742_).withStyle(ChatFormatting.GREEN));
    }

    public static <T extends Comparable<T>> Component formatAndSortList(Collection<T> elements, Function<T, Component> componentExtractor) {
        if (elements.isEmpty()) {
            return CommonComponents.EMPTY;
        } else if (elements.size() == 1) {
            return componentExtractor.apply(elements.iterator().next());
        } else {
            List<T> list = Lists.newArrayList(elements);
            list.sort(Comparable::compareTo);
            return formatList(list, componentExtractor);
        }
    }

    public static <T> Component formatList(Collection<? extends T> elements, Function<T, Component> componentExtractor) {
        return formatList(elements, DEFAULT_SEPARATOR, componentExtractor);
    }

    public static <T> MutableComponent formatList(Collection<? extends T> elements, Optional<? extends Component> optionalSeparator, Function<T, Component> componentExtractor) {
        return formatList(elements, DataFixUtils.orElse(optionalSeparator, DEFAULT_SEPARATOR), componentExtractor);
    }

    public static Component formatList(Collection<? extends Component> elements, Component separator) {
        return formatList(elements, separator, Function.identity());
    }

    public static <T> MutableComponent formatList(Collection<? extends T> elements, Component separator, Function<T, Component> componentExtractor) {
        if (elements.isEmpty()) {
            return Component.empty();
        } else if (elements.size() == 1) {
            return componentExtractor.apply((T)elements.iterator().next()).copy();
        } else {
            MutableComponent mutablecomponent = Component.empty();
            boolean flag = true;

            for (T t : elements) {
                if (!flag) {
                    mutablecomponent.append(separator);
                }

                mutablecomponent.append(componentExtractor.apply(t));
                flag = false;
            }

            return mutablecomponent;
        }
    }

    /**
     * Wraps the text with square brackets.
     */
    public static MutableComponent wrapInSquareBrackets(Component toWrap) {
        return Component.translatable("chat.square_brackets", toWrap);
    }

    public static Component fromMessage(Message message) {
        return (Component)(message instanceof Component ? (Component)message : Component.literal(message.getString()));
    }

    public static boolean isTranslationResolvable(@Nullable Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents translatablecontents) {
            String s1 = translatablecontents.getKey();
            String s = translatablecontents.getFallback();
            return s != null || Language.getInstance().has(s1);
        } else {
            return true;
        }
    }

    public static MutableComponent copyOnClickText(String text) {
        return wrapInSquareBrackets(
            Component.literal(text)
                .withStyle(
                    p_258207_ -> p_258207_.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click")))
                            .withInsertion(text)
                )
        );
    }
}
