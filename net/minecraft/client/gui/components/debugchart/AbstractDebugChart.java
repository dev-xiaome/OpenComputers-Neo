package net.minecraft.client.gui.components.debugchart;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.debugchart.SampleStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractDebugChart {
    protected static final int COLOR_GREY = 14737632;
    protected static final int CHART_HEIGHT = 60;
    protected static final int LINE_WIDTH = 1;
    protected final Font font;
    protected final SampleStorage sampleStorage;

    protected AbstractDebugChart(Font font, SampleStorage sampleStorage) {
        this.font = font;
        this.sampleStorage = sampleStorage;
    }

    public int getWidth(int maxWidth) {
        return Math.min(this.sampleStorage.capacity() + 2, maxWidth);
    }

    public void drawChart(GuiGraphics guiGraphics, int x, int width) {
        int i = guiGraphics.guiHeight();
        guiGraphics.fill(RenderType.guiOverlay(), x, i - 60, x + width, i, -1873784752);
        long j = 0L;
        long k = 2147483647L;
        long l = -2147483648L;
        int i1 = Math.max(0, this.sampleStorage.capacity() - (width - 2));
        int j1 = this.sampleStorage.size() - i1;

        for (int k1 = 0; k1 < j1; k1++) {
            int l1 = x + k1 + 1;
            int i2 = i1 + k1;
            long j2 = this.getValueForAggregation(i2);
            k = Math.min(k, j2);
            l = Math.max(l, j2);
            j += j2;
            this.drawDimensions(guiGraphics, i, l1, i2);
        }

        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, i - 60, -1);
        guiGraphics.hLine(RenderType.guiOverlay(), x, x + width - 1, i - 1, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x, i - 60, i, -1);
        guiGraphics.vLine(RenderType.guiOverlay(), x + width - 1, i - 60, i, -1);
        if (j1 > 0) {
            String s = this.toDisplayString((double)k) + " min";
            String s1 = this.toDisplayString((double)j / (double)j1) + " avg";
            String s2 = this.toDisplayString((double)l) + " max";
            guiGraphics.drawString(this.font, s, x + 2, i - 60 - 9, 14737632);
            guiGraphics.drawCenteredString(this.font, s1, x + width / 2, i - 60 - 9, 14737632);
            guiGraphics.drawString(this.font, s2, x + width - this.font.width(s2) - 2, i - 60 - 9, 14737632);
        }

        this.renderAdditionalLinesAndLabels(guiGraphics, x, width, i);
    }

    protected void drawDimensions(GuiGraphics guiGraphics, int height, int x, int index) {
        this.drawMainDimension(guiGraphics, height, x, index);
        this.drawAdditionalDimensions(guiGraphics, height, x, index);
    }

    protected void drawMainDimension(GuiGraphics guiGraphics, int height, int x, int index) {
        long i = this.sampleStorage.get(index);
        int j = this.getSampleHeight((double)i);
        int k = this.getSampleColor(i);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - j, x + 1, height, k);
    }

    protected void drawAdditionalDimensions(GuiGraphics guiGraphics, int height, int x, int index) {
    }

    protected long getValueForAggregation(int index) {
        return this.sampleStorage.get(index);
    }

    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
    }

    protected void drawStringWithShade(GuiGraphics guiGraphics, String text, int x, int y) {
        guiGraphics.fill(RenderType.guiOverlay(), x, y, x + this.font.width(text) + 1, y + 9, -1873784752);
        guiGraphics.drawString(this.font, text, x + 1, y + 1, 14737632, false);
    }

    protected abstract String toDisplayString(double value);

    protected abstract int getSampleHeight(double value);

    protected abstract int getSampleColor(long value);

    protected int getSampleColor(double value, double minPosition, int minColor, double midPosition, int midColor, double maxPosition, int guiGraphics) {
        value = Mth.clamp(value, minPosition, maxPosition);
        return value < midPosition
            ? FastColor.ARGB32.lerp((float)((value - minPosition) / (midPosition - minPosition)), minColor, midColor)
            : FastColor.ARGB32.lerp((float)((value - midPosition) / (maxPosition - midPosition)), midColor, guiGraphics);
    }
}
