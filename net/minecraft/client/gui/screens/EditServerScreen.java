package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EditServerScreen extends Screen {
    private static final Component NAME_LABEL = Component.translatable("addServer.enterName");
    private static final Component IP_LABEL = Component.translatable("addServer.enterIp");
    private Button addButton;
    private final BooleanConsumer callback;
    private final ServerData serverData;
    private EditBox ipEdit;
    private EditBox nameEdit;
    private final Screen lastScreen;

    public EditServerScreen(Screen lastScreen, BooleanConsumer callback, ServerData serverData) {
        super(Component.translatable("addServer.title"));
        this.lastScreen = lastScreen;
        this.callback = callback;
        this.serverData = serverData;
    }

    @Override
    protected void init() {
        this.nameEdit = new EditBox(this.font, this.width / 2 - 100, 66, 200, 20, Component.translatable("addServer.enterName"));
        this.nameEdit.setValue(this.serverData.name);
        this.nameEdit.setResponder(p_169304_ -> this.updateAddButtonStatus());
        this.addWidget(this.nameEdit);
        this.ipEdit = new EditBox(this.font, this.width / 2 - 100, 106, 200, 20, Component.translatable("addServer.enterIp"));
        this.ipEdit.setMaxLength(128);
        this.ipEdit.setValue(this.serverData.ip);
        this.ipEdit.setResponder(p_169302_ -> this.updateAddButtonStatus());
        this.addWidget(this.ipEdit);
        this.addRenderableWidget(
            CycleButton.builder(ServerData.ServerPackStatus::getName)
                .withValues(ServerData.ServerPackStatus.values())
                .withInitialValue(this.serverData.getResourcePackStatus())
                .create(
                    this.width / 2 - 100,
                    this.height / 4 + 72,
                    200,
                    20,
                    Component.translatable("addServer.resourcePack"),
                    (p_169299_, p_169300_) -> this.serverData.setResourcePackStatus(p_169300_)
                )
        );
        this.addButton = this.addRenderableWidget(
            Button.builder(Component.translatable("addServer.add"), p_96030_ -> this.onAdd())
                .bounds(this.width / 2 - 100, this.height / 4 + 96 + 18, 200, 20)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, p_169297_ -> this.callback.accept(false))
                .bounds(this.width / 2 - 100, this.height / 4 + 120 + 18, 200, 20)
                .build()
        );
        this.updateAddButtonStatus();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.nameEdit);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String s = this.ipEdit.getValue();
        String s1 = this.nameEdit.getValue();
        this.init(minecraft, width, height);
        this.ipEdit.setValue(s);
        this.nameEdit.setValue(s1);
    }

    private void onAdd() {
        this.serverData.name = this.nameEdit.getValue();
        this.serverData.ip = this.ipEdit.getValue();
        this.callback.accept(true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    private void updateAddButtonStatus() {
        this.addButton.active = ServerAddress.isValidAddress(this.ipEdit.getValue()) && !this.nameEdit.getValue().isEmpty();
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 17, 16777215);
        guiGraphics.drawString(this.font, NAME_LABEL, this.width / 2 - 100 + 1, 53, 10526880);
        guiGraphics.drawString(this.font, IP_LABEL, this.width / 2 - 100 + 1, 94, 10526880);
        this.nameEdit.render(guiGraphics, mouseX, mouseY, partialTick);
        this.ipEdit.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
