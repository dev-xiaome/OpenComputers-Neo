package net.minecraft.client.gui.screens.inventory;

import java.util.Objects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LecternScreen extends BookViewScreen implements MenuAccess<LecternMenu> {
    private final LecternMenu menu;
    private final ContainerListener listener = new ContainerListener() {
        @Override
        public void slotChanged(AbstractContainerMenu p_99054_, int p_99055_, ItemStack p_99056_) {
            LecternScreen.this.bookChanged();
        }

        @Override
        public void dataChanged(AbstractContainerMenu p_169772_, int p_169773_, int p_169774_) {
            if (p_169773_ == 0) {
                LecternScreen.this.pageChanged();
            }
        }
    };

    public LecternScreen(LecternMenu menu, Inventory playerInventory, Component title) {
        this.menu = menu;
    }

    public LecternMenu getMenu() {
        return this.menu;
    }

    @Override
    protected void init() {
        super.init();
        this.menu.addSlotListener(this.listener);
    }

    @Override
    public void onClose() {
        this.minecraft.player.closeContainer();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        this.menu.removeSlotListener(this.listener);
    }

    @Override
    protected void createMenuControls() {
        if (this.minecraft.player.mayBuild()) {
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, p_99033_ -> this.onClose()).bounds(this.width / 2 - 100, 196, 98, 20).build());
            this.addRenderableWidget(
                Button.builder(Component.translatable("lectern.take_book"), p_99024_ -> this.sendButtonClick(3))
                    .bounds(this.width / 2 + 2, 196, 98, 20)
                    .build()
            );
        } else {
            super.createMenuControls();
        }
    }

    @Override
    protected void pageBack() {
        this.sendButtonClick(1);
    }

    @Override
    protected void pageForward() {
        this.sendButtonClick(2);
    }

    /**
     * I'm not sure why this exists. The function it calls is public and does all the work.
     */
    @Override
    protected boolean forcePage(int pageNum) {
        if (pageNum != this.menu.getPage()) {
            this.sendButtonClick(100 + pageNum);
            return true;
        } else {
            return false;
        }
    }

    private void sendButtonClick(int pageData) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, pageData);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    void bookChanged() {
        ItemStack itemstack = this.menu.getBook();
        this.setBookAccess(Objects.requireNonNullElse(BookViewScreen.BookAccess.fromItem(itemstack), BookViewScreen.EMPTY_ACCESS));
    }

    void pageChanged() {
        this.setPage(this.menu.getPage());
    }

    @Override
    protected void closeScreen() {
        this.minecraft.player.closeContainer();
    }
}
