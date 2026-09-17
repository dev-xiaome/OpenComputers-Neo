package net.minecraft.client.gui.screens;

import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CreateFlatWorldScreen extends Screen {
    static final ResourceLocation SLOT_SPRITE = ResourceLocation.withDefaultNamespace("container/slot");
    private static final int SLOT_BG_SIZE = 18;
    private static final int SLOT_STAT_HEIGHT = 20;
    private static final int SLOT_BG_X = 1;
    private static final int SLOT_BG_Y = 1;
    private static final int SLOT_FG_X = 2;
    private static final int SLOT_FG_Y = 2;
    protected final CreateWorldScreen parent;
    private final Consumer<FlatLevelGeneratorSettings> applySettings;
    FlatLevelGeneratorSettings generator;
    /**
     * The text used to identify the material for a layer
     */
    private Component columnType;
    /**
     * The text used to identify the height of a layer
     */
    private Component columnHeight;
    private CreateFlatWorldScreen.DetailsList list;
    /**
     * The remove layer button
     */
    private Button deleteLayerButton;

    public CreateFlatWorldScreen(CreateWorldScreen parent, Consumer<FlatLevelGeneratorSettings> applySettings, FlatLevelGeneratorSettings generator) {
        super(Component.translatable("createWorld.customize.flat.title"));
        this.parent = parent;
        this.applySettings = applySettings;
        this.generator = generator;
    }

    public FlatLevelGeneratorSettings settings() {
        return this.generator;
    }

    public void setConfig(FlatLevelGeneratorSettings generator) {
        this.generator = generator;
    }

    @Override
    protected void init() {
        this.columnType = Component.translatable("createWorld.customize.flat.tile");
        this.columnHeight = Component.translatable("createWorld.customize.flat.height");
        this.list = this.addRenderableWidget(new CreateFlatWorldScreen.DetailsList());
        this.deleteLayerButton = this.addRenderableWidget(Button.builder(Component.translatable("createWorld.customize.flat.removeLayer"), p_95845_ -> {
            if (this.hasValidSelection()) {
                List<FlatLayerInfo> list = this.generator.getLayersInfo();
                int i = this.list.children().indexOf(this.list.getSelected());
                int j = list.size() - i - 1;
                list.remove(j);
                this.list.setSelected(list.isEmpty() ? null : this.list.children().get(Math.min(i, list.size() - 1)));
                this.generator.updateLayers();
                this.list.resetRows();
                this.updateButtonValidity();
            }
        }).bounds(this.width / 2 - 155, this.height - 52, 150, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("createWorld.customize.presets"), p_280790_ -> {
            this.minecraft.setScreen(new PresetFlatWorldScreen(this));
            this.generator.updateLayers();
            this.updateButtonValidity();
        }).bounds(this.width / 2 + 5, this.height - 52, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, p_280791_ -> {
            this.applySettings.accept(this.generator);
            this.minecraft.setScreen(this.parent);
            this.generator.updateLayers();
        }).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, p_280792_ -> {
            this.minecraft.setScreen(this.parent);
            this.generator.updateLayers();
        }).bounds(this.width / 2 + 5, this.height - 28, 150, 20).build());
        this.generator.updateLayers();
        this.updateButtonValidity();
    }

    void updateButtonValidity() {
        this.deleteLayerButton.active = this.hasValidSelection();
    }

    private boolean hasValidSelection() {
        return this.list.getSelected() != null;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
        int i = this.width / 2 - 92 - 16;
        guiGraphics.drawString(this.font, this.columnType, i, 32, 16777215);
        guiGraphics.drawString(this.font, this.columnHeight, i + 2 + 213 - this.font.width(this.columnHeight), 32, 16777215);
    }

    @OnlyIn(Dist.CLIENT)
    class DetailsList extends ObjectSelectionList<CreateFlatWorldScreen.DetailsList.Entry> {
        public DetailsList() {
            super(CreateFlatWorldScreen.this.minecraft, CreateFlatWorldScreen.this.width, CreateFlatWorldScreen.this.height - 103, 43, 24);

            for (int i = 0; i < CreateFlatWorldScreen.this.generator.getLayersInfo().size(); i++) {
                this.addEntry(new CreateFlatWorldScreen.DetailsList.Entry());
            }
        }

        public void setSelected(@Nullable CreateFlatWorldScreen.DetailsList.Entry entry) {
            super.setSelected(entry);
            CreateFlatWorldScreen.this.updateButtonValidity();
        }

        public void resetRows() {
            int i = this.children().indexOf(this.getSelected());
            this.clearEntries();

            for (int j = 0; j < CreateFlatWorldScreen.this.generator.getLayersInfo().size(); j++) {
                this.addEntry(new CreateFlatWorldScreen.DetailsList.Entry());
            }

            List<CreateFlatWorldScreen.DetailsList.Entry> list = this.children();
            if (i >= 0 && i < list.size()) {
                this.setSelected(list.get(i));
            }
        }

        @OnlyIn(Dist.CLIENT)
        class Entry extends ObjectSelectionList.Entry<CreateFlatWorldScreen.DetailsList.Entry> {
            @Override
            public void render(
                GuiGraphics guiGraphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovering,
                float partialTick
            ) {
                FlatLayerInfo flatlayerinfo = CreateFlatWorldScreen.this.generator
                    .getLayersInfo()
                    .get(CreateFlatWorldScreen.this.generator.getLayersInfo().size() - index - 1);
                BlockState blockstate = flatlayerinfo.getBlockState();
                ItemStack itemstack = this.getDisplayItem(blockstate);
                this.blitSlot(guiGraphics, left, top, itemstack);
                guiGraphics.drawString(CreateFlatWorldScreen.this.font, itemstack.getHoverName(), left + 18 + 5, top + 3, 16777215, false);
                Component component;
                if (index == 0) {
                    component = Component.translatable("createWorld.customize.flat.layer.top", flatlayerinfo.getHeight());
                } else if (index == CreateFlatWorldScreen.this.generator.getLayersInfo().size() - 1) {
                    component = Component.translatable("createWorld.customize.flat.layer.bottom", flatlayerinfo.getHeight());
                } else {
                    component = Component.translatable("createWorld.customize.flat.layer", flatlayerinfo.getHeight());
                }

                guiGraphics.drawString(
                    CreateFlatWorldScreen.this.font,
                    component,
                    left + 2 + 213 - CreateFlatWorldScreen.this.font.width(component),
                    top + 3,
                    16777215,
                    false
                );
            }

            private ItemStack getDisplayItem(BlockState state) {
                Item item = state.getBlock().asItem();
                if (item == Items.AIR) {
                    if (state.is(Blocks.WATER)) {
                        item = Items.WATER_BUCKET;
                    } else if (state.is(Blocks.LAVA)) {
                        item = Items.LAVA_BUCKET;
                    }
                }

                return new ItemStack(item);
            }

            @Override
            public Component getNarration() {
                FlatLayerInfo flatlayerinfo = CreateFlatWorldScreen.this.generator
                    .getLayersInfo()
                    .get(CreateFlatWorldScreen.this.generator.getLayersInfo().size() - DetailsList.this.children().indexOf(this) - 1);
                ItemStack itemstack = this.getDisplayItem(flatlayerinfo.getBlockState());
                return (Component)(!itemstack.isEmpty() ? Component.translatable("narrator.select", itemstack.getHoverName()) : CommonComponents.EMPTY);
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
                DetailsList.this.setSelected(this);
                return super.mouseClicked(mouseX, mouseY, button);
            }

            private void blitSlot(GuiGraphics guiGraphics, int x, int y, ItemStack stack) {
                this.blitSlotBg(guiGraphics, x + 1, y + 1);
                if (!stack.isEmpty()) {
                    guiGraphics.renderFakeItem(stack, x + 2, y + 2);
                }
            }

            private void blitSlotBg(GuiGraphics guiGraphics, int x, int y) {
                guiGraphics.blitSprite(CreateFlatWorldScreen.SLOT_SPRITE, x, y, 0, 18, 18);
            }
        }
    }
}
