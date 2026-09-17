package net.minecraft.client.gui.screens;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShareToLanScreen extends Screen {
    private static final int PORT_LOWER_BOUND = 1024;
    private static final int PORT_HIGHER_BOUND = 65535;
    private static final Component ALLOW_COMMANDS_LABEL = Component.translatable("selectWorld.allowCommands.new");
    private static final Component GAME_MODE_LABEL = Component.translatable("selectWorld.gameMode");
    private static final Component INFO_TEXT = Component.translatable("lanServer.otherPlayers");
    private static final Component PORT_INFO_TEXT = Component.translatable("lanServer.port");
    private static final Component PORT_UNAVAILABLE = Component.translatable("lanServer.port.unavailable.new", 1024, 65535);
    private static final Component INVALID_PORT = Component.translatable("lanServer.port.invalid.new", 1024, 65535);
    private static final int INVALID_PORT_COLOR = 16733525;
    private final Screen lastScreen;
    private GameType gameMode = GameType.SURVIVAL;
    private boolean commands;
    private int port = HttpUtil.getAvailablePort();
    @Nullable
    private EditBox portEdit;

    public ShareToLanScreen(Screen lastScreen) {
        super(Component.translatable("lanServer.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
        this.gameMode = integratedserver.getDefaultGameType();
        this.commands = integratedserver.getWorldData().isAllowCommands();
        this.addRenderableWidget(
            CycleButton.builder(GameType::getShortDisplayName)
                .withValues(GameType.SURVIVAL, GameType.SPECTATOR, GameType.CREATIVE, GameType.ADVENTURE)
                .withInitialValue(this.gameMode)
                .create(this.width / 2 - 155, 100, 150, 20, GAME_MODE_LABEL, (p_169429_, p_169430_) -> this.gameMode = p_169430_)
        );
        this.addRenderableWidget(
            CycleButton.onOffBuilder(this.commands)
                .create(this.width / 2 + 5, 100, 150, 20, ALLOW_COMMANDS_LABEL, (p_169432_, p_169433_) -> this.commands = p_169433_)
        );
        Button button = Button.builder(Component.translatable("lanServer.start"), p_280826_ -> {
            this.minecraft.setScreen(null);
            Component component;
            if (integratedserver.publishServer(this.gameMode, this.commands, this.port)) {
                component = PublishCommand.getSuccessMessage(this.port);
            } else {
                component = Component.translatable("commands.publish.failed");
            }

            this.minecraft.gui.getChat().addMessage(component);
            this.minecraft.updateTitle();
        }).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build();
        this.portEdit = new EditBox(this.font, this.width / 2 - 75, 160, 150, 20, Component.translatable("lanServer.port"));
        this.portEdit.setResponder(p_258130_ -> {
            Component component = this.tryParsePort(p_258130_);
            this.portEdit.setHint(Component.literal(this.port + "").withStyle(ChatFormatting.DARK_GRAY));
            if (component == null) {
                this.portEdit.setTextColor(14737632);
                this.portEdit.setTooltip(null);
                button.active = true;
            } else {
                this.portEdit.setTextColor(16733525);
                this.portEdit.setTooltip(Tooltip.create(component));
                button.active = false;
            }
        });
        this.portEdit.setHint(Component.literal(this.port + "").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.portEdit);
        this.addRenderableWidget(button);
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_CANCEL, p_329722_ -> this.onClose()).bounds(this.width / 2 + 5, this.height - 28, 150, 20).build()
        );
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Nullable
    private Component tryParsePort(String port) {
        if (port.isBlank()) {
            this.port = HttpUtil.getAvailablePort();
            return null;
        } else {
            try {
                this.port = Integer.parseInt(port);
                if (this.port < 1024 || this.port > 65535) {
                    return INVALID_PORT;
                } else {
                    return !HttpUtil.isPortAvailable(this.port) ? PORT_UNAVAILABLE : null;
                }
            } catch (NumberFormatException numberformatexception) {
                this.port = HttpUtil.getAvailablePort();
                return INVALID_PORT;
            }
        }
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 50, 16777215);
        guiGraphics.drawCenteredString(this.font, INFO_TEXT, this.width / 2, 82, 16777215);
        guiGraphics.drawCenteredString(this.font, PORT_INFO_TEXT, this.width / 2, 142, 16777215);
    }
}
