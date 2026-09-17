package net.minecraft.client.gui.components.debugchart;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.SampleStorage;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TpsDebugChart extends AbstractDebugChart {
    private static final int RED = -65536;
    private static final int YELLOW = -256;
    private static final int GREEN = -16711936;
    private static final int TICK_METHOD_COLOR = -6745839;
    private static final int TASK_COLOR = -4548257;
    private static final int OTHER_COLOR = -10547572;
    private final Supplier<Float> msptSupplier;

    public TpsDebugChart(Font font, SampleStorage sampleStorage, Supplier<Float> msptSupplier) {
        super(font, sampleStorage);
        this.msptSupplier = msptSupplier;
    }

    @Override
    protected void renderAdditionalLinesAndLabels(GuiGraphics guiGraphics, int x, int width, int height) {
        float f = (float)TimeUtil.MILLISECONDS_PER_SECOND / this.msptSupplier.get();
        this.drawStringWithShade(guiGraphics, String.format("%.1f TPS", f), x + 1, height - 60 + 1);
    }

    @Override
    protected void drawAdditionalDimensions(GuiGraphics guiGraphics, int height, int x, int index) {
        long i = this.sampleStorage.get(index, TpsDebugDimensions.TICK_SERVER_METHOD.ordinal());
        int j = this.getSampleHeight((double)i);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - j, x + 1, height, -6745839);
        long k = this.sampleStorage.get(index, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
        int l = this.getSampleHeight((double)k);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - j - l, x + 1, height - j, -4548257);
        long i1 = this.sampleStorage.get(index) - this.sampleStorage.get(index, TpsDebugDimensions.IDLE.ordinal()) - i - k;
        int j1 = this.getSampleHeight((double)i1);
        guiGraphics.fill(RenderType.guiOverlay(), x, height - j1 - l - j, x + 1, height - l - j, -10547572);
    }

    @Override
    protected long getValueForAggregation(int index) {
        return this.sampleStorage.get(index) - this.sampleStorage.get(index, TpsDebugDimensions.IDLE.ordinal());
    }

    @Override
    protected String toDisplayString(double value) {
        return String.format(Locale.ROOT, "%d ms", (int)Math.round(toMilliseconds(value)));
    }

    @Override
    protected int getSampleHeight(double value) {
        return (int)Math.round(toMilliseconds(value) * 60.0 / (double)this.msptSupplier.get().floatValue());
    }

    @Override
    protected int getSampleColor(long value) {
        float f = this.msptSupplier.get();
        return this.getSampleColor(toMilliseconds((double)value), (double)f, -16711936, (double)f * 1.125, -256, (double)f * 1.25, -65536);
    }

    private static double toMilliseconds(double value) {
        return value / 1000000.0;
    }
}
