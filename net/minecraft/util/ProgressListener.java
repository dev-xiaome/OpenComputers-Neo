package net.minecraft.util;

import net.minecraft.network.chat.Component;

public interface ProgressListener {
    void progressStartNoAbort(Component component);

    void progressStart(Component header);

    void progressStage(Component stage);

    /**
     * Updates the progress bar on the loading screen to the specified amount.
     */
    void progressStagePercentage(int progress);

    void stop();
}
