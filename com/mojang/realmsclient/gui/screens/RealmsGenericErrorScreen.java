package com.mojang.realmsclient.gui.screens;

import com.mojang.realmsclient.client.RealmsError;
import com.mojang.realmsclient.exception.RealmsServiceException;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RealmsGenericErrorScreen extends RealmsScreen {
    private final Screen nextScreen;
    private final RealmsGenericErrorScreen.ErrorMessage lines;
    private MultiLineLabel line2Split = MultiLineLabel.EMPTY;

    public RealmsGenericErrorScreen(RealmsServiceException serviceException, Screen nextScreen) {
        super(GameNarrator.NO_TITLE);
        this.nextScreen = nextScreen;
        this.lines = errorMessage(serviceException);
    }

    public RealmsGenericErrorScreen(Component message, Screen nextScreen) {
        super(GameNarrator.NO_TITLE);
        this.nextScreen = nextScreen;
        this.lines = errorMessage(message);
    }

    public RealmsGenericErrorScreen(Component title, Component line2, Screen message) {
        super(GameNarrator.NO_TITLE);
        this.nextScreen = message;
        this.lines = errorMessage(title, line2);
    }

    private static RealmsGenericErrorScreen.ErrorMessage errorMessage(RealmsServiceException exception) {
        RealmsError realmserror = exception.realmsError;
        return errorMessage(Component.translatable("mco.errorMessage.realmsService.realmsError", realmserror.errorCode()), realmserror.errorMessage());
    }

    private static RealmsGenericErrorScreen.ErrorMessage errorMessage(Component message) {
        return errorMessage(Component.translatable("mco.errorMessage.generic"), message);
    }

    private static RealmsGenericErrorScreen.ErrorMessage errorMessage(Component title, Component message) {
        return new RealmsGenericErrorScreen.ErrorMessage(title, message);
    }

    @Override
    public void init() {
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_OK, p_315811_ -> this.onClose()).bounds(this.width / 2 - 100, this.height - 52, 200, 20).build()
        );
        this.line2Split = MultiLineLabel.create(this.font, this.lines.detail, this.width * 3 / 4);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.nextScreen);
    }

    @Override
    public Component getNarrationMessage() {
        return Component.empty().append(this.lines.title).append(": ").append(this.lines.detail);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(this.nextScreen);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
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
        guiGraphics.drawCenteredString(this.font, this.lines.title, this.width / 2, 80, -1);
        this.line2Split.renderCentered(guiGraphics, this.width / 2, 100, 9, -2142128);
    }

    @OnlyIn(Dist.CLIENT)
    static record ErrorMessage(Component title, Component detail) {
    }
}
