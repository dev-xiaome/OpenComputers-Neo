package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FpsDebugChart extends AbstractDebugChart {
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;
    private static final int CHART_TOP_FPS = 30;
    private static final double CHART_TOP_VALUE = 33.333333333333336;

    public FpsDebugChart(Font font, SampleStorage sampleStorage) {
        super(font, sampleStorage);
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
        this.drawStringWithShade(guiGraphics, "30 FPS", x + 1, height - 60 + 1);
        this.drawStringWithShade(guiGraphics, "60 FPS", x + 1, height - 30 + 1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, height - 30, -1);
        int i = Minecraft.getInstance().options.framerateLimit().get();
        if (i > 0 && i <= 250) {
            guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, height - this.getSampleHeight(1.0E9 / (double)i) - 1, -16711681);
        }
    }

    @Override
    protected String toDisplayString(double value) {
        return String.format(Locale.ROOT, "%d ms", (int)Math.round(toMilliseconds(value)));
    }

    @Override
    protected int getSampleHeight(double value) {
        return (int)Math.round(toMilliseconds(value) * 60.0 / 33.333333333333336);
    }

    @Override
    protected int getSampleColor(long value) {
        return this.getSampleColor(toMilliseconds((double)value), 0.0, -16711936, 28.0, -256, 56.0, -65536);
    }

    private static double toMilliseconds(double value) {
        return value / 1000000.0;
    }
}
