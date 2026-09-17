package net.minecraft.util.parsing.packrat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public abstract class ParseState<S> {
    private final Map<ParseState.CacheKey<?>, ParseState.CacheEntry<?>> ruleCache = new HashMap<>();
    private final Dictionary<S> dictionary;
    private final ErrorCollector<S> errorCollector;

    protected ParseState(Dictionary<S> dictionary, ErrorCollector<S> errorCollector) {
        this.dictionary = dictionary;
        this.errorCollector = errorCollector;
    }

    public ErrorCollector<S> errorCollector() {
        return this.errorCollector;
    }

    public <T> Optional<T> parseTopRule(Atom<T> atom) {
        Optional<T> optional = this.parse(atom);
        if (optional.isPresent()) {
            this.errorCollector.finish(this.mark());
        }

        return optional;
    }

    public <T> Optional<T> parse(Atom<T> atom) {
        ParseState.CacheKey<T> cachekey = new ParseState.CacheKey<>(atom, this.mark());
        ParseState.CacheEntry<T> cacheentry = this.lookupInCache(cachekey);
        if (cacheentry != null) {
            this.restore(cacheentry.mark());
            return cacheentry.value;
        } else {
            Rule<S, T> rule = this.dictionary.get(atom);
            if (rule == null) {
                throw new IllegalStateException("No symbol " + atom);
            } else {
                Optional<T> optional = rule.parse(this);
                this.storeInCache(cachekey, optional);
                return optional;
            }
        }
    }

    @Nullable
    private <T> ParseState.CacheEntry<T> lookupInCache(ParseState.CacheKey<T> key) {
        return (ParseState.CacheEntry<T>)this.ruleCache.get(key);
    }

    private <T> void storeInCache(ParseState.CacheKey<T> key, Optional<T> value) {
        this.ruleCache.put(key, new ParseState.CacheEntry<>(value, this.mark()));
    }

    public abstract S input();

    public abstract int mark();

    public abstract void restore(int cursor);

    static record CacheEntry<T>(Optional<T> value, int mark) {
    }

    static record CacheKey<T>(Atom<T> name, int mark) {
    }
}
