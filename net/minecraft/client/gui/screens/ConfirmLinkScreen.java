package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.net.URI;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ConfirmLinkScreen extends ConfirmScreen {
    private static final Component COPY_BUTTON_TEXT = Component.translatable("chat.copy");
    private static final Component WARNING_TEXT = Component.translatable("chat.link.warning");
    private final String url;
    private final boolean showWarning;

    public ConfirmLinkScreen(BooleanConsumer callback, String url, boolean trusted) {
        this(
            callback,
            confirmMessage(trusted),
            Component.literal(url),
            url,
            trusted ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_NO,
            trusted
        );
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, String url, boolean trusted) {
        this(
            callback, title, confirmMessage(trusted, url), url, trusted ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_NO, trusted
        );
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, URI uri, boolean trusted) {
        this(callback, title, uri.toString(), trusted);
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, Component message, URI uri, Component noButton, boolean trusted) {
        this(callback, title, message, uri.toString(), noButton, true);
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, Component message, String url, Component noButton, boolean trusted) {
        super(callback, title, message);
        this.yesButton = (Component)(trusted ? Component.translatable("chat.link.open") : CommonComponents.GUI_YES);
        this.noButton = noButton;
        this.showWarning = !trusted;
        this.url = url;
    }

    protected static MutableComponent confirmMessage(boolean trusted, String extraInfo) {
        return confirmMessage(trusted).append(CommonComponents.SPACE).append(Component.literal(extraInfo));
    }

    protected static MutableComponent confirmMessage(boolean trusted) {
        return Component.translatable(trusted ? "chat.link.confirmTrusted" : "chat.link.confirm");
    }

    @Override
    protected void addButtons(int y) {
        this.addRenderableWidget(
            Button.builder(this.yesButton, p_169249_ -> this.callback.accept(true)).bounds(this.width / 2 - 50 - 105, y, 100, 20).build()
        );
        this.addRenderableWidget(Button.builder(COPY_BUTTON_TEXT, p_169247_ -> {
            this.copyToClipboard();
            this.callback.accept(false);
        }).bounds(this.width / 2 - 50, y, 100, 20).build());
        this.addRenderableWidget(
            Button.builder(this.noButton, p_169245_ -> this.callback.accept(false)).bounds(this.width / 2 - 50 + 105, y, 100, 20).build()
        );
    }

    public void copyToClipboard() {
        this.minecraft.keyboardHandler.setClipboard(this.url);
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
        if (this.showWarning) {
            guiGraphics.drawCenteredString(this.font, WARNING_TEXT, this.width / 2, 110, 16764108);
        }
    }

    public static void confirmLinkNow(Screen lastScreen, String url, boolean trusted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmLinkScreen(p_274671_ -> {
            if (p_274671_) {
                Util.getPlatform().openUri(url);
            }

            minecraft.setScreen(lastScreen);
        }, url, trusted));
    }

    public static void confirmLinkNow(Screen lastScreen, URI uri, boolean trusted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmLinkScreen(p_351650_ -> {
            if (p_351650_) {
                Util.getPlatform().openUri(uri);
            }

            minecraft.setScreen(lastScreen);
        }, uri.toString(), trusted));
    }

    public static void confirmLinkNow(Screen lastScreen, URI uri) {
        confirmLinkNow(lastScreen, uri, true);
    }

    public static void confirmLinkNow(Screen lastScreen, String url) {
        confirmLinkNow(lastScreen, url, true);
    }

    public static Button.OnPress confirmLink(Screen lastScreen, String url, boolean trusted) {
        return p_349796_ -> confirmLinkNow(lastScreen, url, trusted);
    }

    public static Button.OnPress confirmLink(Screen lastScreen, URI uri, boolean trusted) {
        return p_351646_ -> confirmLinkNow(lastScreen, uri, trusted);
    }

    public static Button.OnPress confirmLink(Screen lastScreen, String url) {
        return confirmLink(lastScreen, url, true);
    }

    public static Button.OnPress confirmLink(Screen lastScreen, URI uri) {
        return confirmLink(lastScreen, uri, true);
    }
}
