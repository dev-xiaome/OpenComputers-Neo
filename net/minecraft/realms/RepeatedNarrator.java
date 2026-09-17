package net.minecraft.realms;

import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.GameNarrator;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RepeatedNarrator {
    private final float permitsPerSecond;
    private final AtomicReference<RepeatedNarrator.Params> params = new AtomicReference<>();

    public RepeatedNarrator(Duration duration) {
        this.permitsPerSecond = 1000.0F / (float)duration.toMillis();
    }

    public void narrate(GameNarrator narrator, Component narration) {
        RepeatedNarrator.Params repeatednarrator$params = this.params
            .updateAndGet(
                p_339420_ -> p_339420_ != null && narration.equals(p_339420_.narration)
                        ? p_339420_
                        : new RepeatedNarrator.Params(narration, RateLimiter.create((double)this.permitsPerSecond))
            );
        if (repeatednarrator$params.rateLimiter.tryAcquire(1)) {
            narrator.sayNow(narration);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class Params {
        final Component narration;
        final RateLimiter rateLimiter;

        Params(Component narration, RateLimiter rateLimiter) {
            this.narration = narration;
            this.rateLimiter = rateLimiter;
        }
    }
}
