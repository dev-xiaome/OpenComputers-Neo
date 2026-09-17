package net.minecraft.client.gui.components;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PlayerSkinWidget extends AbstractWidget {
    private static final float MODEL_OFFSET = 0.0625F;
    private static final float MODEL_HEIGHT = 2.125F;
    private static final float Z_OFFSET = 100.0F;
    private static final float ROTATION_SENSITIVITY = 2.5F;
    private static final float DEFAULT_ROTATION_X = -5.0F;
    private static final float DEFAULT_ROTATION_Y = 30.0F;
    private static final float ROTATION_X_LIMIT = 50.0F;
    private final PlayerSkinWidget.Model model;
    private final Supplier<PlayerSkin> skin;
    private float rotationX = -5.0F;
    private float rotationY = 30.0F;

    public PlayerSkinWidget(int width, int height, EntityModelSet model, Supplier<PlayerSkin> skin) {
        super(0, 0, width, height, CommonComponents.EMPTY);
        this.model = PlayerSkinWidget.Model.bake(model);
        this.skin = skin;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float)this.getX() + (float)this.getWidth() / 2.0F, (float)(this.getY() + this.getHeight()), 100.0F);
        float f = (float)this.getHeight() / 2.125F;
        guiGraphics.pose().scale(f, f, f);
        guiGraphics.pose().translate(0.0F, -0.0625F, 0.0F);
        guiGraphics.pose().rotateAround(Axis.XP.rotationDegrees(this.rotationX), 0.0F, -1.0625F, 0.0F);
        guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(this.rotationY));
        guiGraphics.flush();
        Lighting.setupForEntityInInventory(Axis.XP.rotationDegrees(this.rotationX));
        this.model.render(guiGraphics, this.skin.get());
        guiGraphics.flush();
        Lighting.setupFor3DItems();
        guiGraphics.pose().popPose();
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.rotationX = Mth.clamp(this.rotationX - (float)dragY * 2.5F, -50.0F, 50.0F);
        this.rotationY += (float)dragX * 2.5F;
    }

    @Override
    public void playDownSound(SoundManager handler) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    /**
     * Retrieves the next focus path based on the given focus navigation event.
     * <p>
     * @return the next focus path as a ComponentPath, or {@code null} if there is no next focus path.
     *
     * @param event the focus navigation event.
     */
    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    static record Model(PlayerModel<?> wideModel, PlayerModel<?> slimModel) {
        public static PlayerSkinWidget.Model bake(EntityModelSet model) {
            PlayerModel<?> playermodel = new PlayerModel(model.bakeLayer(ModelLayers.PLAYER), false);
            PlayerModel<?> playermodel1 = new PlayerModel(model.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            playermodel.young = false;
            playermodel1.young = false;
            return new PlayerSkinWidget.Model(playermodel, playermodel1);
        }

        public void render(GuiGraphics guiGraphics, PlayerSkin skin) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(1.0F, 1.0F, -1.0F);
            guiGraphics.pose().translate(0.0F, -1.5F, 0.0F);
            PlayerModel<?> playermodel = skin.model() == PlayerSkin.Model.SLIM ? this.slimModel : this.wideModel;
            RenderType rendertype = playermodel.renderType(skin.texture());
            playermodel.renderToBuffer(guiGraphics.pose(), guiGraphics.bufferSource().getBuffer(rendertype), 15728880, OverlayTexture.NO_OVERLAY);
            guiGraphics.pose().popPose();
        }
    }
}
