package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.DeltaTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LayeredDraw {
    public static final float Z_SEPARATION = 200.0F;
    private final List<LayeredDraw.Layer> layers = new ArrayList<>();

    public LayeredDraw add(LayeredDraw.Layer layer) {
        this.layers.add(layer);
        return this;
    }

    public LayeredDraw add(LayeredDraw layeredDraw, BooleanSupplier renderInner) {
        return this.add((p_348091_, p_348092_) -> {
            if (renderInner.getAsBoolean()) {
                layeredDraw.renderInner(p_348091_, p_348092_);
            }
        });
    }

    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        guiGraphics.pose().pushPose();
        this.renderInner(guiGraphics, deltaTracker);
        guiGraphics.pose().popPose();
    }

    private void renderInner(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        for (LayeredDraw.Layer layereddraw$layer : this.layers) {
            layereddraw$layer.render(guiGraphics, deltaTracker);
            guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public interface Layer {
        void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker);
    }
}
