package net.minecraft.network.chat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.Unit;

public interface FormattedText {
    Optional<Unit> STOP_ITERATION = Optional.of(Unit.INSTANCE);
    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> p_130779_) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> p_130781_, Style p_130782_) {
            return Optional.empty();
        }
    };

    <T> Optional<T> visit(FormattedText.ContentConsumer<T> acceptor);

    <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> acceptor, Style style);

    static FormattedText of(final String text) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> p_130787_) {
                return p_130787_.accept(text);
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> p_130789_, Style p_130790_) {
                return p_130789_.accept(p_130790_, text);
            }
        };
    }

    static FormattedText of(final String text, final Style style) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> p_130797_) {
                return p_130797_.accept(text);
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> p_130799_, Style p_130800_) {
                return p_130799_.accept(style.applyTo(p_130800_), text);
            }
        };
    }

    static FormattedText composite(FormattedText... elements) {
        return composite(ImmutableList.copyOf(elements));
    }

    static FormattedText composite(final List<? extends FormattedText> elements) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(FormattedText.ContentConsumer<T> p_130805_) {
                for (FormattedText formattedtext : elements) {
                    Optional<T> optional = formattedtext.visit(p_130805_);
                    if (optional.isPresent()) {
                        return optional;
                    }
                }

                return Optional.empty();
            }

            @Override
            public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> p_130807_, Style p_130808_) {
                for (FormattedText formattedtext : elements) {
                    Optional<T> optional = formattedtext.visit(p_130807_, p_130808_);
                    if (optional.isPresent()) {
                        return optional;
                    }
                }

                return Optional.empty();
            }
        };
    }

    default String getString() {
        StringBuilder stringbuilder = new StringBuilder();
        this.visit(p_130767_ -> {
            stringbuilder.append(p_130767_);
            return Optional.empty();
        });
        return stringbuilder.toString();
    }

    public interface ContentConsumer<T> {
        Optional<T> accept(String content);
    }

    public interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String content);
    }
}
