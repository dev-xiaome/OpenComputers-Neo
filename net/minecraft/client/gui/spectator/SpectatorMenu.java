package net.minecraft.client.gui.spectator;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.spectator.categories.SpectatorPage;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpectatorMenu {
    static final ResourceLocation CLOSE_SPRITE = ResourceLocation.withDefaultNamespace("spectator/close");
    static final ResourceLocation SCROLL_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("spectator/scroll_left");
    static final ResourceLocation SCROLL_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("spectator/scroll_right");
    private static final SpectatorMenuItem CLOSE_ITEM = new SpectatorMenu.CloseSpectatorItem();
    private static final SpectatorMenuItem SCROLL_LEFT = new SpectatorMenu.ScrollMenuItem(-1, true);
    private static final SpectatorMenuItem SCROLL_RIGHT_ENABLED = new SpectatorMenu.ScrollMenuItem(1, true);
    private static final SpectatorMenuItem SCROLL_RIGHT_DISABLED = new SpectatorMenu.ScrollMenuItem(1, false);
    private static final int MAX_PER_PAGE = 8;
    static final Component CLOSE_MENU_TEXT = Component.translatable("spectatorMenu.close");
    static final Component PREVIOUS_PAGE_TEXT = Component.translatable("spectatorMenu.previous_page");
    static final Component NEXT_PAGE_TEXT = Component.translatable("spectatorMenu.next_page");
    public static final SpectatorMenuItem EMPTY_SLOT = new SpectatorMenuItem() {
        @Override
        public void selectItem(SpectatorMenu p_101812_) {
        }

        @Override
        public Component getName() {
            return CommonComponents.EMPTY;
        }

        @Override
        public void renderIcon(GuiGraphics p_283652_, float p_101809_, int p_101810_) {
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    };
    private final SpectatorMenuListener listener;
    private SpectatorMenuCategory category;
    private int selectedSlot = -1;
    int page;

    public SpectatorMenu(SpectatorMenuListener listener) {
        this.category = new RootSpectatorMenuCategory();
        this.listener = listener;
    }

    public SpectatorMenuItem getItem(int index) {
        int i = index + this.page * 6;
        if (this.page > 0 && index == 0) {
            return SCROLL_LEFT;
        } else if (index == 7) {
            return i < this.category.getItems().size() ? SCROLL_RIGHT_ENABLED : SCROLL_RIGHT_DISABLED;
        } else if (index == 8) {
            return CLOSE_ITEM;
        } else {
            return i >= 0 && i < this.category.getItems().size() ? MoreObjects.firstNonNull(this.category.getItems().get(i), EMPTY_SLOT) : EMPTY_SLOT;
        }
    }

    public List<SpectatorMenuItem> getItems() {
        List<SpectatorMenuItem> list = Lists.newArrayList();

        for (int i = 0; i <= 8; i++) {
            list.add(this.getItem(i));
        }

        return list;
    }

    public SpectatorMenuItem getSelectedItem() {
        return this.getItem(this.selectedSlot);
    }

    public SpectatorMenuCategory getSelectedCategory() {
        return this.category;
    }

    public void selectSlot(int slot) {
        SpectatorMenuItem spectatormenuitem = this.getItem(slot);
        if (spectatormenuitem != EMPTY_SLOT) {
            if (this.selectedSlot == slot && spectatormenuitem.isEnabled()) {
                spectatormenuitem.selectItem(this);
            } else {
                this.selectedSlot = slot;
            }
        }
    }

    public void exit() {
        this.listener.onSpectatorMenuClosed(this);
    }

    public int getSelectedSlot() {
        return this.selectedSlot;
    }

    public void selectCategory(SpectatorMenuCategory category) {
        this.category = category;
        this.selectedSlot = -1;
        this.page = 0;
    }

    public SpectatorPage getCurrentPage() {
        return new SpectatorPage(this.getItems(), this.selectedSlot);
    }

    @OnlyIn(Dist.CLIENT)
    static class CloseSpectatorItem implements SpectatorMenuItem {
        @Override
        public void selectItem(SpectatorMenu menu) {
            menu.exit();
        }

        @Override
        public Component getName() {
            return SpectatorMenu.CLOSE_MENU_TEXT;
        }

        @Override
        public void renderIcon(GuiGraphics guiGraphics, float shadeColor, int alpha) {
            guiGraphics.blitSprite(SpectatorMenu.CLOSE_SPRITE, 0, 0, 16, 16);
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class ScrollMenuItem implements SpectatorMenuItem {
        private final int direction;
        private final boolean enabled;

        public ScrollMenuItem(int direction, boolean enabled) {
            this.direction = direction;
            this.enabled = enabled;
        }

        @Override
        public void selectItem(SpectatorMenu menu) {
            menu.page = menu.page + this.direction;
        }

        @Override
        public Component getName() {
            return this.direction < 0 ? SpectatorMenu.PREVIOUS_PAGE_TEXT : SpectatorMenu.NEXT_PAGE_TEXT;
        }

        @Override
        public void renderIcon(GuiGraphics guiGraphics, float shadeColor, int alpha) {
            if (this.direction < 0) {
                guiGraphics.blitSprite(SpectatorMenu.SCROLL_LEFT_SPRITE, 0, 0, 16, 16);
            } else {
                guiGraphics.blitSprite(SpectatorMenu.SCROLL_RIGHT_SPRITE, 0, 0, 16, 16);
            }
        }

        @Override
        public boolean isEnabled() {
            return this.enabled;
        }
    }
}
