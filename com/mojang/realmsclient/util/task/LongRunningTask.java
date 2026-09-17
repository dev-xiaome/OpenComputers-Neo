package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsGenericErrorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public abstract class LongRunningTask implements Runnable {
    protected static final int NUMBER_OF_RETRIES = 25;
    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean aborted = false;

    protected static void pause(long seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException interruptedexception) {
            Thread.currentThread().interrupt();
            LOGGER.error("", (Throwable)interruptedexception);
        }
    }

    public static void setScreen(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(screen));
    }

    protected void error(Component message) {
        this.abortTask();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new RealmsGenericErrorScreen(message, new RealmsMainScreen(new TitleScreen()))));
    }

    protected void error(Exception exception) {
        if (exception instanceof RealmsServiceException realmsserviceexception) {
            this.error(realmsserviceexception.realmsError.errorMessage());
        } else {
            this.error(Component.literal(exception.getMessage()));
        }
    }

    protected void error(RealmsServiceException exception) {
        this.error(exception.realmsError.errorMessage());
    }

    public abstract Component getTitle();

    public boolean aborted() {
        return this.aborted;
    }

    public void tick() {
    }

    public void init() {
    }

    public void abortTask() {
        this.aborted = true;
    }
}
