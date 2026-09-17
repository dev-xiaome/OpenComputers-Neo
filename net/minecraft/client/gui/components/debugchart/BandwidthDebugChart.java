package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BandwidthDebugChart extends AbstractDebugChart {
    private static final int MIN_COLOR = -16711681;
    private static final int MID_COLOR = -6250241;
    private static final int MAX_COLOR = -65536;
    private static final int KILOBYTE = 1024;
    private static final int MEGABYTE = 1048576;
    private static final int CHART_TOP_VALUE = 1048576;

    public BandwidthDebugChart(Font font, SampleStorage sampleStorage) {
        super(font, sampleStorage);
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
        this.drawLabeledLineAtValue(guiGraphics, x, width, height, 64);
        this.drawLabeledLineAtValue(guiGraphics, x, width, height, 1024);
        this.drawLabeledLineAtValue(guiGraphics, x, width, height, 16384);
        this.drawStringWithShade(guiGraphics, toDisplayStringInternal(1048576.0), x + 1, height - getSampleHeightInternal(1048576.0) + 1);
    }

    private void drawLabeledLineAtValue(GuiGraphics guiGraphics, int x, int width, int y, int value) {
        this.drawLineWithLabel(
            guiGraphics, x, width, y - getSampleHeightInternal((double)value), toDisplayStringInternal((double)value)
        );
    }

    private void drawLineWithLabel(GuiGraphics guiGraphics, int x, int width, int y, String text) {
        this.drawStringWithShade(guiGraphics, text, x + 1, y + 1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, y, -1);
    }

    @Override
    protected String toDisplayString(double value) {
        return toDisplayStringInternal(toBytesPerSecond(value));
    }

    private static String toDisplayStringInternal(double value) {
        if (value >= 1048576.0) {
            return String.format(Locale.ROOT, "%.1f MiB/s", value / 1048576.0);
        } else {
            return value >= 1024.0
                ? String.format(Locale.ROOT, "%.1f KiB/s", value / 1024.0)
                : String.format(Locale.ROOT, "%d B/s", Mth.floor(value));
        }
    }

    @Override
    protected int getSampleHeight(double value) {
        return getSampleHeightInternal(toBytesPerSecond(value));
    }

    private static int getSampleHeightInternal(double value) {
        return (int)Math.round(Math.log(value + 1.0) * 60.0 / Math.log(1048576.0));
    }

    @Override
    protected int getSampleColor(long value) {
        return this.getSampleColor(toBytesPerSecond((double)value), 0.0, -16711681, 8192.0, -6250241, 1.048576E7, -65536);
    }

    private static double toBytesPerSecond(double bytesPerTick) {
        return bytesPerTick * 20.0;
    }
}
