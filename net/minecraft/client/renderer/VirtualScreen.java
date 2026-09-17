package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VirtualScreen implements AutoCloseable {
    private final Minecraft minecraft;
    private final ScreenManager screenManager;

    public VirtualScreen(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.screenManager = new ScreenManager(Monitor::new);
    }

    public Window newWindow(DisplayData screenSize, @Nullable String videoModeName, String title) {
        return new Window(this.minecraft, this.screenManager, screenSize, videoModeName, title);
    }

    @Override
    public void close() {
        this.screenManager.shutdown();
    }
}
