package net.minecraft.util.profiling;

import java.util.function.Supplier;
import net.minecraft.util.profiling.metrics.MetricCategory;

public interface ProfilerFiller {
    String ROOT = "root";

    void startTick();

    void endTick();

    /**
     * Start section
     */
    void push(String name);

    void push(Supplier<String> nameSupplier);

    void pop();

    void popPush(String name);

    void popPush(Supplier<String> nameSupplier);

    void markForCharting(MetricCategory category);

    default void incrementCounter(String entryId) {
        this.incrementCounter(entryId, 1);
    }

    void incrementCounter(String counterName, int increment);

    default void incrementCounter(Supplier<String> entryIdSupplier) {
        this.incrementCounter(entryIdSupplier, 1);
    }

    void incrementCounter(Supplier<String> counterNameSupplier, int increment);

    static ProfilerFiller tee(final ProfilerFiller first, final ProfilerFiller second) {
        if (first == InactiveProfiler.INSTANCE) {
            return second;
        } else {
            return second == InactiveProfiler.INSTANCE ? first : new ProfilerFiller() {
                @Override
                public void startTick() {
                    first.startTick();
                    second.startTick();
                }

                @Override
                public void endTick() {
                    first.endTick();
                    second.endTick();
                }

                @Override
                public void push(String p_18594_) {
                    first.push(p_18594_);
                    second.push(p_18594_);
                }

                @Override
                public void push(Supplier<String> p_18596_) {
                    first.push(p_18596_);
                    second.push(p_18596_);
                }

                @Override
                public void markForCharting(MetricCategory p_145961_) {
                    first.markForCharting(p_145961_);
                    second.markForCharting(p_145961_);
                }

                @Override
                public void pop() {
                    first.pop();
                    second.pop();
                }

                @Override
                public void popPush(String p_18599_) {
                    first.popPush(p_18599_);
                    second.popPush(p_18599_);
                }

                @Override
                public void popPush(Supplier<String> p_18601_) {
                    first.popPush(p_18601_);
                    second.popPush(p_18601_);
                }

                @Override
                public void incrementCounter(String p_185263_, int p_185264_) {
                    first.incrementCounter(p_185263_, p_185264_);
                    second.incrementCounter(p_185263_, p_185264_);
                }

                @Override
                public void incrementCounter(Supplier<String> p_185266_, int p_185267_) {
                    first.incrementCounter(p_185266_, p_185267_);
                    second.incrementCounter(p_185266_, p_185267_);
                }
            };
        }
    }
}
