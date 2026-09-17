package net.minecraft.client.resources.server;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PackReloadConfig {
    void scheduleReload(PackReloadConfig.Callbacks callbacks);

    @OnlyIn(Dist.CLIENT)
    public interface Callbacks {
        void onSuccess();

        void onFailure(boolean recoveryFailure);

        List<PackReloadConfig.IdAndPath> packsToLoad();
    }

    @OnlyIn(Dist.CLIENT)
    public static record IdAndPath(UUID id, Path path) {
    }
}
