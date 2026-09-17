package net.minecraft.client.gui.components;

import java.util.OptionalInt;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.SingleKeyCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MultiLineTextWidget extends AbstractStringWidget {
    private OptionalInt maxWidth = OptionalInt.empty();
    private OptionalInt maxRows = OptionalInt.empty();
    private final SingleKeyCache<MultiLineTextWidget.CacheKey, MultiLineLabel> cache;
    private boolean centered = false;

    public MultiLineTextWidget(Component message, Font font) {
        this(0, 0, message, font);
    }

    public MultiLineTextWidget(int x, int y, Component message, Font font) {
        super(x, y, 0, 0, message, font);
        this.cache = Util.singleKeyCache(
            p_352660_ -> p_352660_.maxRows.isPresent()
                    ? MultiLineLabel.create(font, p_352660_.maxWidth, p_352660_.maxRows.getAsInt(), p_352660_.message)
                    : MultiLineLabel.create(font, p_352660_.message, p_352660_.maxWidth)
        );
        this.active = false;
    }

    public MultiLineTextWidget setColor(int color) {
        super.setColor(color);
        return this;
    }

    public MultiLineTextWidget setMaxWidth(int maxWidth) {
        this.maxWidth = OptionalInt.of(maxWidth);
        return this;
    }

    public MultiLineTextWidget setMaxRows(int maxRows) {
        this.maxRows = OptionalInt.of(maxRows);
        return this;
    }

    public MultiLineTextWidget setCentered(boolean centered) {
        this.centered = centered;
        return this;
    }

    @Override
    public int getWidth() {
        return this.cache.getValue(this.getFreshCacheKey()).getWidth();
    }

    @Override
    public int getHeight() {
        return this.cache.getValue(this.getFreshCacheKey()).getLineCount() * 9;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        MultiLineLabel multilinelabel = this.cache.getValue(this.getFreshCacheKey());
        int i = this.getX();
        int j = this.getY();
        int k = 9;
        int l = this.getColor();
        if (this.centered) {
            multilinelabel.renderCentered(guiGraphics, i + this.getWidth() / 2, j, k, l);
        } else {
            multilinelabel.renderLeftAligned(guiGraphics, i, j, k, l);
        }
    }

    private MultiLineTextWidget.CacheKey getFreshCacheKey() {
        return new MultiLineTextWidget.CacheKey(this.getMessage(), this.maxWidth.orElse(Integer.MAX_VALUE), this.maxRows);
    }

    @OnlyIn(Dist.CLIENT)
    static record CacheKey(Component message, int maxWidth, OptionalInt maxRows) {
    }
}
