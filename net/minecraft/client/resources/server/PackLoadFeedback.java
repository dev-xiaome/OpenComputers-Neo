package net.minecraft.client.resources.server;

import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PackLoadFeedback {
    void reportUpdate(UUID id, PackLoadFeedback.Update update);

    void reportFinalResult(UUID id, PackLoadFeedback.FinalResult result);

    @OnlyIn(Dist.CLIENT)
    public static enum FinalResult {
        DECLINED,
        APPLIED,
        DISCARDED,
        DOWNLOAD_FAILED,
        ACTIVATION_FAILED;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Update {
        ACCEPTED,
        DOWNLOADED;
    }
}
