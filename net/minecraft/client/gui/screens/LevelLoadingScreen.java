package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LevelLoadingScreen extends Screen {
    private static final long NARRATION_DELAY_MS = 2000L;
    private final StoringChunkProgressListener progressListener;
    private long lastNarration = -1L;
    private boolean done;
    private static final Object2IntMap<ChunkStatus> COLORS = Util.make(new Object2IntOpenHashMap<>(), p_280803_ -> {
        p_280803_.defaultReturnValue(0);
        p_280803_.put(ChunkStatus.EMPTY, 5526612);
        p_280803_.put(ChunkStatus.STRUCTURE_STARTS, 10066329);
        p_280803_.put(ChunkStatus.STRUCTURE_REFERENCES, 6250897);
        p_280803_.put(ChunkStatus.BIOMES, 8434258);
        p_280803_.put(ChunkStatus.NOISE, 13750737);
        p_280803_.put(ChunkStatus.SURFACE, 7497737);
        p_280803_.put(ChunkStatus.CARVERS, 3159410);
        p_280803_.put(ChunkStatus.FEATURES, 2213376);
        p_280803_.put(ChunkStatus.INITIALIZE_LIGHT, 13421772);
        p_280803_.put(ChunkStatus.LIGHT, 16769184);
        p_280803_.put(ChunkStatus.SPAWN, 15884384);
        p_280803_.put(ChunkStatus.FULL, 16777215);
    });

    public LevelLoadingScreen(StoringChunkProgressListener progressListener) {
        super(GameNarrator.NO_TITLE);
        this.progressListener = progressListener;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void removed() {
        this.done = true;
        this.triggerImmediateNarration(true);
    }

    @Override
    protected void updateNarratedWidget(NarrationElementOutput narrationElementOutput) {
        if (this.done) {
            narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("narrator.loading.done"));
        } else {
            narrationElementOutput.add(NarratedElementType.TITLE, this.getFormattedProgress());
        }
    }

    private Component getFormattedProgress() {
        return Component.translatable("loading.progress", Mth.clamp(this.progressListener.getProgress(), 0, 100));
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        long i = Util.getMillis();
        if (i - this.lastNarration > 2000L) {
            this.lastNarration = i;
            this.triggerImmediateNarration(true);
        }

        int j = this.width / 2;
        int k = this.height / 2;
        renderChunks(guiGraphics, this.progressListener, j, k, 2, 0);
        int l = this.progressListener.getDiameter() + 9 + 2;
        guiGraphics.drawCenteredString(this.font, this.getFormattedProgress(), j, k - l, 16777215);
    }

    public static void renderChunks(GuiGraphics guiGraphics, StoringChunkProgressListener progressListener, int x, int y, int p_96154_, int p_96155_) {
        int i = p_96154_ + p_96155_;
        int j = progressListener.getFullDiameter();
        int k = j * i - p_96155_;
        int l = progressListener.getDiameter();
        int i1 = l * i - p_96155_;
        int j1 = x - i1 / 2;
        int k1 = y - i1 / 2;
        int l1 = k / 2 + 1;
        int i2 = -16772609;
        guiGraphics.drawManaged(() -> {
            if (p_96155_ != 0) {
                guiGraphics.fill(x - l1, y - l1, x - l1 + 1, y + l1, -16772609);
                guiGraphics.fill(x + l1 - 1, y - l1, x + l1, y + l1, -16772609);
                guiGraphics.fill(x - l1, y - l1, x + l1, y - l1 + 1, -16772609);
                guiGraphics.fill(x - l1, y + l1 - 1, x + l1, y + l1, -16772609);
            }

            for (int j2 = 0; j2 < l; j2++) {
                for (int k2 = 0; k2 < l; k2++) {
                    ChunkStatus chunkstatus = progressListener.getStatus(j2, k2);
                    int l2 = j1 + j2 * i;
                    int i3 = k1 + k2 * i;
                    guiGraphics.fill(l2, i3, l2 + p_96154_, i3 + p_96154_, COLORS.getInt(chunkstatus) | 0xFF000000);
                }
            }
        });
    }
}
