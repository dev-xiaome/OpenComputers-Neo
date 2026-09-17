package net.minecraft;

import java.util.Objects;

@FunctionalInterface
public interface CharPredicate {
    boolean test(char value);

    default CharPredicate and(CharPredicate predicate) {
        Objects.requireNonNull(predicate);
        return p_178295_ -> this.test(p_178295_) && predicate.test(p_178295_);
    }

    default CharPredicate negate() {
        return p_178285_ -> !this.test(p_178285_);
    }

    default CharPredicate or(CharPredicate predicate) {
        Objects.requireNonNull(predicate);
        return p_178290_ -> this.test(p_178290_) || predicate.test(p_178290_);
    }
}
