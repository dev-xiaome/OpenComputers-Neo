package net.minecraft.client.gui.screens.advancements;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AdvancementTab {
    private final Minecraft minecraft;
    private final AdvancementsScreen screen;
    private final AdvancementTabType type;
    private final int index;
    private final AdvancementNode rootNode;
    private final DisplayInfo display;
    private final ItemStack icon;
    private final Component title;
    private final AdvancementWidget root;
    private final Map<AdvancementHolder, AdvancementWidget> widgets = Maps.newLinkedHashMap();
    private double scrollX;
    private double scrollY;
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private float fade;
    private boolean centered;
    private int page;

    public AdvancementTab(
        Minecraft minecraft, AdvancementsScreen screen, AdvancementTabType type, int index, AdvancementNode rootNode, DisplayInfo display
    ) {
        this.minecraft = minecraft;
        this.screen = screen;
        this.type = type;
        this.index = index;
        this.rootNode = rootNode;
        this.display = display;
        this.icon = display.getIcon();
        this.title = display.getTitle();
        this.root = new AdvancementWidget(this, minecraft, rootNode, display);
        this.addWidget(this.root, rootNode.holder());
    }

    public AdvancementTab(Minecraft mc, AdvancementsScreen screen, AdvancementTabType type, int index, int page, AdvancementNode adv, DisplayInfo info) {
        this(mc, screen, type, index, adv, info);
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    public AdvancementTabType getType() {
        return this.type;
    }

    public int getIndex() {
        return this.index;
    }

    public AdvancementNode getRootNode() {
        return this.rootNode;
    }

    public Component getTitle() {
        return this.title;
    }

    public DisplayInfo getDisplay() {
        return this.display;
    }

    public void drawTab(GuiGraphics guiGraphics, int offsetX, int offsetY, boolean isSelected) {
        this.type.draw(guiGraphics, offsetX, offsetY, isSelected, this.index);
    }

    public void drawIcon(GuiGraphics guiGraphics, int offsetX, int offsetY) {
        this.type.drawIcon(guiGraphics, offsetX, offsetY, this.index, this.icon);
    }

    public void drawContents(GuiGraphics guiGraphics, int x, int y) {
        if (!this.centered) {
            this.scrollX = (double)(117 - (this.maxX + this.minX) / 2);
            this.scrollY = (double)(56 - (this.maxY + this.minY) / 2);
            this.centered = true;
        }

        guiGraphics.enableScissor(x, y, x + 234, y + 113);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate((float)x, (float)y, 0.0F);
        ResourceLocation resourcelocation = this.display.getBackground().orElse(TextureManager.INTENTIONAL_MISSING_TEXTURE);
        int i = Mth.floor(this.scrollX);
        int j = Mth.floor(this.scrollY);
        int k = i % 16;
        int l = j % 16;

        for (int i1 = -1; i1 <= 15; i1++) {
            for (int j1 = -1; j1 <= 8; j1++) {
                guiGraphics.blit(resourcelocation, k + 16 * i1, l + 16 * j1, 0.0F, 0.0F, 16, 16, 16, 16);
            }
        }

        this.root.drawConnectivity(guiGraphics, i, j, true);
        this.root.drawConnectivity(guiGraphics, i, j, false);
        this.root.draw(guiGraphics, i, j);
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();
    }

    public void drawTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int width, int height) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, -200.0F);
        guiGraphics.fill(0, 0, 234, 113, Mth.floor(this.fade * 255.0F) << 24);
        boolean flag = false;
        int i = Mth.floor(this.scrollX);
        int j = Mth.floor(this.scrollY);
        if (mouseX > 0 && mouseX < 234 && mouseY > 0 && mouseY < 113) {
            for (AdvancementWidget advancementwidget : this.widgets.values()) {
                if (advancementwidget.isMouseOver(i, j, mouseX, mouseY)) {
                    flag = true;
                    advancementwidget.drawHover(guiGraphics, i, j, this.fade, width, height);
                    break;
                }
            }
        }

        guiGraphics.pose().popPose();
        if (flag) {
            this.fade = Mth.clamp(this.fade + 0.02F, 0.0F, 0.3F);
        } else {
            this.fade = Mth.clamp(this.fade - 0.04F, 0.0F, 1.0F);
        }
    }

    public boolean isMouseOver(int offsetX, int offsetY, double mouseX, double mouseY) {
        return this.type.isMouseOver(offsetX, offsetY, this.index, mouseX, mouseY);
    }

    @Nullable
    public static AdvancementTab create(Minecraft minecraft, AdvancementsScreen screen, int index, AdvancementNode rootNode) {
        Optional<DisplayInfo> optional = rootNode.advancement().display();
        if (optional.isEmpty()) {
            return null;
        } else {
            for (AdvancementTabType advancementtabtype : AdvancementTabType.values()) {
                if ((index % AdvancementTabType.MAX_TABS) < advancementtabtype.getMax()) {
                    return new AdvancementTab(minecraft, screen, advancementtabtype, index % AdvancementTabType.MAX_TABS, index / AdvancementTabType.MAX_TABS, rootNode, optional.get());
                }

                index -= advancementtabtype.getMax();
            }

            return null;
        }
    }

    public void scroll(double dragX, double dragY) {
        if (this.maxX - this.minX > 234) {
            this.scrollX = Mth.clamp(this.scrollX + dragX, (double)(-(this.maxX - 234)), 0.0);
        }

        if (this.maxY - this.minY > 113) {
            this.scrollY = Mth.clamp(this.scrollY + dragY, (double)(-(this.maxY - 113)), 0.0);
        }
    }

    public void addAdvancement(AdvancementNode node) {
        Optional<DisplayInfo> optional = node.advancement().display();
        if (!optional.isEmpty()) {
            AdvancementWidget advancementwidget = new AdvancementWidget(this, this.minecraft, node, optional.get());
            this.addWidget(advancementwidget, node.holder());
        }
    }

    private void addWidget(AdvancementWidget widget, AdvancementHolder advancement) {
        this.widgets.put(advancement, widget);
        int i = widget.getX();
        int j = i + 28;
        int k = widget.getY();
        int l = k + 27;
        this.minX = Math.min(this.minX, i);
        this.maxX = Math.max(this.maxX, j);
        this.minY = Math.min(this.minY, k);
        this.maxY = Math.max(this.maxY, l);

        for (AdvancementWidget advancementwidget : this.widgets.values()) {
            advancementwidget.attachToParent();
        }
    }

    @Nullable
    public AdvancementWidget getWidget(AdvancementHolder advancement) {
        return this.widgets.get(advancement);
    }

    public AdvancementsScreen getScreen() {
        return this.screen;
    }
}
