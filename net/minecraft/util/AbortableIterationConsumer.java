package net.minecraft.util;

import java.util.function.Consumer;

@FunctionalInterface
public interface AbortableIterationConsumer<T> {
    AbortableIterationConsumer.Continuation accept(T value);

    static <T> AbortableIterationConsumer<T> forConsumer(Consumer<T> consumer) {
        return p_261916_ -> {
            consumer.accept(p_261916_);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        };
    }

    public static enum Continuation {
        CONTINUE,
        ABORT;

        public boolean shouldAbort() {
            return this == ABORT;
        }
    }
}
