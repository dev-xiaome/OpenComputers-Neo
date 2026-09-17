package com.mojang.realmsclient.gui.screens;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.util.task.GetServerDetailsTask;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.CommonLinks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsTermsScreen extends RealmsScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.terms.title");
    private static final Component TERMS_STATIC_TEXT = Component.translatable("mco.terms.sentence.1");
    private static final Component TERMS_LINK_TEXT = CommonComponents.space()
        .append(Component.translatable("mco.terms.sentence.2").withStyle(Style.EMPTY.withUnderlined(true)));
    private final Screen lastScreen;
    /**
     * The screen to display when OK is clicked on the disconnect screen.
     *
     * Seems to be either null (integrated server) or an instance of either {@link MultiplayerScreen} (when connecting to a server) or {@link com.mojang.realmsclient.gui.screens.RealmsTermsScreen} (when connecting to MCO server)
     */
    private final RealmsServer realmsServer;
    private boolean onLink;

    public RealmsTermsScreen(Screen lastScreen, RealmsServer realmsServer) {
        super(TITLE);
        this.lastScreen = lastScreen;
        this.realmsServer = realmsServer;
    }

    @Override
    public void init() {
        int i = this.width / 4 - 2;
        this.addRenderableWidget(
            Button.builder(Component.translatable("mco.terms.buttons.agree"), p_90054_ -> this.agreedToTos()).bounds(this.width / 4, row(12), i, 20).build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("mco.terms.buttons.disagree"), p_280762_ -> this.minecraft.setScreen(this.lastScreen))
                .bounds(this.width / 2 + 4, row(12), i, 20)
                .build()
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.minecraft.setScreen(this.lastScreen);
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private void agreedToTos() {
        RealmsClient realmsclient = RealmsClient.create();

        try {
            realmsclient.agreeToTos();
            this.minecraft.setScreen(new RealmsLongRunningMcoTaskScreen(this.lastScreen, new GetServerDetailsTask(this.lastScreen, this.realmsServer)));
        } catch (RealmsServiceException realmsserviceexception) {
            LOGGER.error("Couldn't agree to TOS", (Throwable)realmsserviceexception);
        }
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.onLink) {
            this.minecraft.keyboardHandler.setClipboard(CommonLinks.REALMS_TERMS.toString());
            Util.getPlatform().openUri(CommonLinks.REALMS_TERMS);
            return true;
        } else {
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), TERMS_STATIC_TEXT).append(CommonComponents.SPACE).append(TERMS_LINK_TEXT);
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 17, -1);
        guiGraphics.drawString(this.font, TERMS_STATIC_TEXT, this.width / 2 - 120, row(5), -1, false);
        int i = this.font.width(TERMS_STATIC_TEXT);
        int j = this.width / 2 - 121 + i;
        int k = row(5);
        int l = j + this.font.width(TERMS_LINK_TEXT) + 1;
        int i1 = k + 1 + 9;
        this.onLink = j <= mouseX && mouseX <= l && k <= mouseY && mouseY <= i1;
        guiGraphics.drawString(this.font, TERMS_LINK_TEXT, this.width / 2 - 120 + i, row(5), this.onLink ? 7107012 : 3368635, false);
    }
}
