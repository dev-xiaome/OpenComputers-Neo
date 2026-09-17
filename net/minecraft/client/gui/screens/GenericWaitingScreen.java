package net.minecraft.client.gui.screens;

import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GenericWaitingScreen extends Screen {
    private static final int TITLE_Y = 80;
    private static final int MESSAGE_Y = 120;
    private static final int MESSAGE_MAX_WIDTH = 360;
    @Nullable
    private final Component messageText;
    private final Component buttonLabel;
    private final Runnable buttonCallback;
    @Nullable
    private MultiLineLabel message;
    private Button button;
    private int disableButtonTicks;

    public static GenericWaitingScreen createWaiting(Component title, Component buttonLabel, Runnable buttonCallback) {
        return new GenericWaitingScreen(title, null, buttonLabel, buttonCallback, 0);
    }

    public static GenericWaitingScreen createCompleted(Component title, Component messageText, Component buttonLabel, Runnable buttonCallback) {
        return new GenericWaitingScreen(title, messageText, buttonLabel, buttonCallback, 20);
    }

    protected GenericWaitingScreen(Component title, @Nullable Component messageText, Component buttonLabel, Runnable buttonCallback, int disableButtonTicks) {
        super(title);
        this.messageText = messageText;
        this.buttonLabel = buttonLabel;
        this.buttonCallback = buttonCallback;
        this.disableButtonTicks = disableButtonTicks;
    }

    @Override
    protected void init() {
        super.init();
        if (this.messageText != null) {
            this.message = MultiLineLabel.create(this.font, this.messageText, 360);
        }

        int i = 150;
        int j = 20;
        int k = this.message != null ? this.message.getLineCount() : 1;
        int l = Math.max(k, 5) * 9;
        int i1 = Math.min(120 + l, this.height - 40);
        this.button = this.addRenderableWidget(
            Button.builder(this.buttonLabel, p_239908_ -> this.onClose()).bounds((this.width - 150) / 2, i1, 150, 20).build()
        );
    }

    @Override
    public void tick() {
        if (this.disableButtonTicks > 0) {
            this.disableButtonTicks--;
        }

        this.button.active = this.disableButtonTicks == 0;
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 80, 16777215);
        if (this.message == null) {
            String s = LoadingDotsText.get(Util.getMillis());
            guiGraphics.drawCenteredString(this.font, s, this.width / 2, 120, 10526880);
        } else {
            this.message.renderCentered(guiGraphics, this.width / 2, 120);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.message != null && this.button.active;
    }

    @Override
    public void onClose() {
        this.buttonCallback.run();
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(this.title, this.messageText != null ? this.messageText : CommonComponents.EMPTY);
    }
}
