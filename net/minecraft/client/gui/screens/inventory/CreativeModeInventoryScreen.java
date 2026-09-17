package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.HotbarManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.inventory.Hotbar;
import net.minecraft.client.searchtree.SearchTree;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CreativeModeInventoryScreen extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final ResourceLocation[] UNSELECTED_TOP_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_unselected_7")
    };
    private static final ResourceLocation[] SELECTED_TOP_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_selected_7")
    };
    private static final ResourceLocation[] UNSELECTED_BOTTOM_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_unselected_7")
    };
    private static final ResourceLocation[] SELECTED_BOTTOM_TABS = new ResourceLocation[]{
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_1"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_2"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_3"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_4"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_5"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_6"),
        ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_bottom_selected_7")
    };
    private static final int NUM_ROWS = 5;
    private static final int NUM_COLS = 9;
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    static final SimpleContainer CONTAINER = new SimpleContainer(45);
    private static final Component TRASH_SLOT_TOOLTIP = Component.translatable("inventory.binSlot");
    private static final int TEXT_COLOR = 16777215;
    /**
     * Currently selected creative inventory tab index.
     */
    private static CreativeModeTab selectedTab = CreativeModeTabs.getDefaultTab();
    /**
     * Amount scrolled in Creative mode inventory (0 = top, 1 = bottom)
     */
    private float scrollOffs;
    /**
     * True if the scrollbar is being dragged
     */
    private boolean scrolling;
    private EditBox searchBox;
    @Nullable
    private List<Slot> originalSlots;
    @Nullable
    private Slot destroyItemSlot;
    private CreativeInventoryListener listener;
    private boolean ignoreTextInput;
    private boolean hasClickedOutside;
    private final Set<TagKey<Item>> visibleTags = new HashSet<>();
    private final boolean displayOperatorCreativeTab;
    private final List<net.neoforged.neoforge.client.gui.CreativeTabsScreenPage> pages = new java.util.ArrayList<>();
    private net.neoforged.neoforge.client.gui.CreativeTabsScreenPage currentPage = new net.neoforged.neoforge.client.gui.CreativeTabsScreenPage(new java.util.ArrayList<>());

    public CreativeModeInventoryScreen(LocalPlayer player, FeatureFlagSet enabledFeatures, boolean displayOperatorCreativeTab) {
        super(new CreativeModeInventoryScreen.ItemPickerMenu(player), player.getInventory(), CommonComponents.EMPTY);
        player.containerMenu = this.menu;
        this.imageHeight = 136;
        this.imageWidth = 195;
        this.displayOperatorCreativeTab = displayOperatorCreativeTab;
        this.tryRebuildTabContents(player.connection.searchTrees(), enabledFeatures, this.hasPermissions(player), player.level().registryAccess());
    }

    private boolean hasPermissions(Player player) {
        return player.canUseGameMasterBlocks() && this.displayOperatorCreativeTab;
    }

    private void tryRefreshInvalidatedTabs(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider provider) {
        ClientPacketListener clientpacketlistener = this.minecraft.getConnection();
        if (this.tryRebuildTabContents(clientpacketlistener != null ? clientpacketlistener.searchTrees() : null, enabledFeatures, hasPermissions, provider)) {
            for (CreativeModeTab creativemodetab : CreativeModeTabs.allTabs()) {
                Collection<ItemStack> collection = creativemodetab.getDisplayItems();
                if (creativemodetab == selectedTab) {
                    if (creativemodetab.getType() == CreativeModeTab.Type.CATEGORY && collection.isEmpty()) {
                        this.selectTab(CreativeModeTabs.getDefaultTab());
                    } else {
                        this.refreshCurrentTabContents(collection);
                    }
                }
            }
        }
    }

    private boolean tryRebuildTabContents(@Nullable SessionSearchTrees searchTrees, FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider registries) {
        if (!CreativeModeTabs.tryRebuildTabContents(enabledFeatures, hasPermissions, registries)) {
            return false;
        } else {
            if (searchTrees != null) {
                CreativeModeTabs.allTabs().stream().filter(net.minecraft.world.item.CreativeModeTab::hasSearchBar).forEach(tab -> {
                    List<ItemStack> list = List.copyOf(tab.getDisplayItems());
                    searchTrees.updateCreativeTooltips(registries, list, net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchKey(tab));
                    searchTrees.updateCreativeTags(list, net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchKey(tab));
                });
            }

            return true;
        }
    }

    private void refreshCurrentTabContents(Collection<ItemStack> items) {
        int i = this.menu.getRowIndexForScroll(this.scrollOffs);
        this.menu.items.clear();
        if (selectedTab.hasSearchBar()) {
            this.refreshSearchResults();
        } else {
            this.menu.items.addAll(items);
        }

        this.scrollOffs = this.menu.getScrollForRowIndex(i);
        this.menu.scrollTo(this.scrollOffs);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.minecraft != null) {
            if (this.minecraft.player != null) {
                this.tryRefreshInvalidatedTabs(
                    this.minecraft.player.connection.enabledFeatures(),
                    this.hasPermissions(this.minecraft.player),
                    this.minecraft.player.level().registryAccess()
                );
            }

            if (!this.minecraft.gameMode.hasInfiniteItems()) {
                this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
            }
        }
    }

    /**
     * Called when the mouse is clicked over a slot or outside the gui.
     */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (this.isCreativeSlot(slot)) {
            this.searchBox.moveCursorToEnd(false);
            this.searchBox.setHighlightPos(0);
        }

        boolean flag = type == ClickType.QUICK_MOVE;
        type = slotId == -999 && type == ClickType.PICKUP ? ClickType.THROW : type;
        if (slot == null && selectedTab.getType() != CreativeModeTab.Type.INVENTORY && type != ClickType.QUICK_CRAFT) {
            if (!this.menu.getCarried().isEmpty() && this.hasClickedOutside) {
                if (mouseButton == 0) {
                    this.minecraft.player.drop(this.menu.getCarried(), true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(this.menu.getCarried());
                    this.menu.setCarried(ItemStack.EMPTY);
                }

                if (mouseButton == 1) {
                    ItemStack itemstack5 = this.menu.getCarried().split(1);
                    this.minecraft.player.drop(itemstack5, true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(itemstack5);
                }
            }
        } else {
            if (slot != null && !slot.mayPickup(this.minecraft.player)) {
                return;
            }

            if (slot == this.destroyItemSlot && flag) {
                for (int j = 0; j < this.minecraft.player.inventoryMenu.getItems().size(); j++) {
                    this.minecraft.gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, j);
                }
            } else if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
                if (slot == this.destroyItemSlot) {
                    this.menu.setCarried(ItemStack.EMPTY);
                } else if (type == ClickType.THROW && slot != null && slot.hasItem()) {
                    ItemStack itemstack = slot.remove(mouseButton == 0 ? 1 : slot.getItem().getMaxStackSize());
                    ItemStack itemstack1 = slot.getItem();
                    this.minecraft.player.drop(itemstack, true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(itemstack);
                    this.minecraft.gameMode.handleCreativeModeItemAdd(itemstack1, ((CreativeModeInventoryScreen.SlotWrapper)slot).target.index);
                } else if (type == ClickType.THROW && !this.menu.getCarried().isEmpty()) {
                    this.minecraft.player.drop(this.menu.getCarried(), true);
                    this.minecraft.gameMode.handleCreativeModeItemDrop(this.menu.getCarried());
                    this.menu.setCarried(ItemStack.EMPTY);
                } else {
                    this.minecraft
                        .player
                        .inventoryMenu
                        .clicked(
                            slot == null ? slotId : ((CreativeModeInventoryScreen.SlotWrapper)slot).target.index,
                            mouseButton,
                            type,
                            this.minecraft.player
                        );
                    this.minecraft.player.inventoryMenu.broadcastChanges();
                }
            } else if (type != ClickType.QUICK_CRAFT && slot.container == CONTAINER) {
                ItemStack itemstack4 = this.menu.getCarried();
                ItemStack itemstack7 = slot.getItem();
                if (type == ClickType.SWAP) {
                    if (!itemstack7.isEmpty()) {
                        this.minecraft.player.getInventory().setItem(mouseButton, itemstack7.copyWithCount(itemstack7.getMaxStackSize()));
                        this.minecraft.player.inventoryMenu.broadcastChanges();
                    }

                    return;
                }

                if (type == ClickType.CLONE) {
                    if (this.menu.getCarried().isEmpty() && slot.hasItem()) {
                        ItemStack itemstack9 = slot.getItem();
                        this.menu.setCarried(itemstack9.copyWithCount(itemstack9.getMaxStackSize()));
                    }

                    return;
                }

                if (type == ClickType.THROW) {
                    if (!itemstack7.isEmpty()) {
                        ItemStack itemstack8 = itemstack7.copyWithCount(mouseButton == 0 ? 1 : itemstack7.getMaxStackSize());
                        this.minecraft.player.drop(itemstack8, true);
                        this.minecraft.gameMode.handleCreativeModeItemDrop(itemstack8);
                    }

                    return;
                }

                if (!itemstack4.isEmpty() && !itemstack7.isEmpty() && ItemStack.isSameItemSameComponents(itemstack4, itemstack7)) {
                    if (mouseButton == 0) {
                        if (flag) {
                            itemstack4.setCount(itemstack4.getMaxStackSize());
                        } else if (itemstack4.getCount() < itemstack4.getMaxStackSize()) {
                            itemstack4.grow(1);
                        }
                    } else {
                        itemstack4.shrink(1);
                    }
                } else if (!itemstack7.isEmpty() && itemstack4.isEmpty()) {
                    int l = flag ? itemstack7.getMaxStackSize() : itemstack7.getCount();
                    this.menu.setCarried(itemstack7.copyWithCount(l));
                } else if (mouseButton == 0) {
                    this.menu.setCarried(ItemStack.EMPTY);
                } else if (!this.menu.getCarried().isEmpty()) {
                    this.menu.getCarried().shrink(1);
                }
            } else if (this.menu != null) {
                ItemStack itemstack3 = slot == null ? ItemStack.EMPTY : this.menu.getSlot(slot.index).getItem();
                this.menu.clicked(slot == null ? slotId : slot.index, mouseButton, type, this.minecraft.player);
                if (AbstractContainerMenu.getQuickcraftHeader(mouseButton) == 2) {
                    for (int k = 0; k < 9; k++) {
                        this.minecraft.gameMode.handleCreativeModeItemAdd(this.menu.getSlot(45 + k).getItem(), 36 + k);
                    }
                } else if (slot != null) {
                    ItemStack itemstack6 = this.menu.getSlot(slot.index).getItem();
                    this.minecraft.gameMode.handleCreativeModeItemAdd(itemstack6, slot.index - this.menu.slots.size() + 9 + 36);
                    int i = 45 + mouseButton;
                    if (type == ClickType.SWAP) {
                        this.minecraft.gameMode.handleCreativeModeItemAdd(itemstack3, i - this.menu.slots.size() + 9 + 36);
                    } else if (type == ClickType.THROW && !itemstack3.isEmpty()) {
                        ItemStack itemstack2 = itemstack3.copyWithCount(mouseButton == 0 ? 1 : itemstack3.getMaxStackSize());
                        this.minecraft.player.drop(itemstack2, true);
                        this.minecraft.gameMode.handleCreativeModeItemDrop(itemstack2);
                    }

                    this.minecraft.player.inventoryMenu.broadcastChanges();
                }
            }
        }
    }

    private boolean isCreativeSlot(@Nullable Slot slot) {
        return slot != null && slot.container == CONTAINER;
    }

    @Override
    protected void init() {
        if (this.minecraft.gameMode.hasInfiniteItems()) {
            super.init();
            this.pages.clear();
            int tabIndex = 0;
            List<CreativeModeTab> currentPage = new java.util.ArrayList<>();
            for (CreativeModeTab sortedCreativeModeTab : net.neoforged.neoforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs().stream().filter(CreativeModeTab::hasAnyItems).toList()) {
                currentPage.add(sortedCreativeModeTab);
                tabIndex++;
                if (tabIndex == 10) {
                    this.pages.add(new net.neoforged.neoforge.client.gui.CreativeTabsScreenPage(currentPage));
                    currentPage = new java.util.ArrayList<>();
                    tabIndex = 0;
                }
            }
            if (tabIndex != 0) {
                this.pages.add(new net.neoforged.neoforge.client.gui.CreativeTabsScreenPage(currentPage));
            }
            if (this.pages.isEmpty()) {
                this.currentPage = new net.neoforged.neoforge.client.gui.CreativeTabsScreenPage(new java.util.ArrayList<>());
            } else {
                this.currentPage = this.pages.get(0);
            }
            if (this.pages.size() > 1) {
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("<"), b -> setCurrentPage(this.pages.get(Math.max(this.pages.indexOf(this.currentPage) - 1, 0)))).pos(leftPos,  topPos - 50).size(20, 20).build());
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal(">"), b -> setCurrentPage(this.pages.get(Math.min(this.pages.indexOf(this.currentPage) + 1, this.pages.size() - 1)))).pos(leftPos + imageWidth - 20, topPos - 50).size(20, 20).build());
            }
            this.currentPage = this.pages.stream().filter(page -> page.getVisibleTabs().contains(selectedTab)).findFirst().orElse(this.currentPage);
            if (!this.currentPage.getVisibleTabs().contains(selectedTab)) {
                selectedTab = this.currentPage.getVisibleTabs().get(0);
            }
            this.searchBox = new EditBox(this.font, this.leftPos + 82, this.topPos + 6, 80, 9, Component.translatable("itemGroup.search"));
            this.searchBox.setMaxLength(50);
            this.searchBox.setBordered(false);
            this.searchBox.setVisible(false);
            this.searchBox.setTextColor(16777215);
            this.addWidget(this.searchBox);
            CreativeModeTab creativemodetab = selectedTab;
            selectedTab = CreativeModeTabs.getDefaultTab();
            this.selectTab(creativemodetab);
            this.minecraft.player.inventoryMenu.removeSlotListener(this.listener);
            this.listener = new CreativeInventoryListener(this.minecraft);
            this.minecraft.player.inventoryMenu.addSlotListener(this.listener);
            if (!selectedTab.shouldDisplay()) {
                this.selectTab(CreativeModeTabs.getDefaultTab());
            }
        } else {
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        int i = this.menu.getRowIndexForScroll(this.scrollOffs);
        String s = this.searchBox.getValue();
        this.init(minecraft, width, height);
        this.searchBox.setValue(s);
        if (!this.searchBox.getValue().isEmpty()) {
            this.refreshSearchResults();
        }

        this.scrollOffs = this.menu.getScrollForRowIndex(i);
        this.menu.scrollTo(this.scrollOffs);
    }

    @Override
    public void removed() {
        super.removed();
        if (this.minecraft.player != null && this.minecraft.player.getInventory() != null) {
            this.minecraft.player.inventoryMenu.removeSlotListener(this.listener);
        }
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param codePoint the code point of the typed character.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.ignoreTextInput) {
            return false;
        } else if (!selectedTab.hasSearchBar()) {
            return false;
        } else {
            String s = this.searchBox.getValue();
            if (this.searchBox.charTyped(codePoint, modifiers)) {
                if (!Objects.equals(s, this.searchBox.getValue())) {
                    this.refreshSearchResults();
                }

                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.ignoreTextInput = false;
        if (!selectedTab.hasSearchBar()) {
            if (this.minecraft.options.keyChat.matches(keyCode, scanCode)) {
                this.ignoreTextInput = true;
                this.selectTab(CreativeModeTabs.searchTab());
                return true;
            } else {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        } else {
            boolean flag = !this.isCreativeSlot(this.hoveredSlot) || this.hoveredSlot.hasItem();
            boolean flag1 = InputConstants.getKey(keyCode, scanCode).getNumericKeyValue().isPresent();
            if (flag && flag1 && this.checkHotbarKeyPressed(keyCode, scanCode)) {
                this.ignoreTextInput = true;
                return true;
            } else {
                String s = this.searchBox.getValue();
                if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                    if (!Objects.equals(s, this.searchBox.getValue())) {
                        this.refreshSearchResults();
                    }

                    return true;
                } else {
                    return this.searchBox.isFocused() && this.searchBox.isVisible() && keyCode != 256 ? true : super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
        }
    }

    /**
     * Called when a keyboard key is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the released key.
     * @param scanCode  the scan code of the released key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        this.ignoreTextInput = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void refreshSearchResults() {
        if (!selectedTab.hasSearchBar()) return;
        this.menu.items.clear();
        this.visibleTags.clear();
        String s = this.searchBox.getValue();
        if (s.isEmpty()) {
            this.menu.items.addAll(selectedTab.getDisplayItems());
        } else {
            ClientPacketListener clientpacketlistener = this.minecraft.getConnection();
            if (clientpacketlistener != null) {
                SessionSearchTrees sessionsearchtrees = clientpacketlistener.searchTrees();
                SearchTree<ItemStack> searchtree;
                if (s.startsWith("#")) {
                    s = s.substring(1);
                    searchtree = sessionsearchtrees.creativeTagSearch(net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getTagSearchKey(selectedTab));
                    this.updateVisibleTags(s);
                } else {
                    searchtree = sessionsearchtrees.creativeNameSearch(net.neoforged.neoforge.client.CreativeModeTabSearchRegistry.getNameSearchKey(selectedTab));
                }

                this.menu.items.addAll(searchtree.search(s.toLowerCase(Locale.ROOT)));
            }
        }

        this.scrollOffs = 0.0F;
        this.menu.scrollTo(0.0F);
    }

    private void updateVisibleTags(String search) {
        int i = search.indexOf(58);
        Predicate<ResourceLocation> predicate;
        if (i == -1) {
            predicate = p_98609_ -> p_98609_.getPath().contains(search);
        } else {
            String s = search.substring(0, i).trim();
            String s1 = search.substring(i + 1).trim();
            predicate = p_98606_ -> p_98606_.getNamespace().contains(s) && p_98606_.getPath().contains(s1);
        }

        BuiltInRegistries.ITEM.getTagNames().filter(p_205410_ -> predicate.test(p_205410_.location())).forEach(this.visibleTags::add);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (selectedTab.showTitle()) {
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            guiGraphics.drawString(this.font, selectedTab.getDisplayName(), 8, 6, selectedTab.getLabelColor(), false);
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
        if (button == 0) {
            double d0 = mouseX - (double)this.leftPos;
            double d1 = mouseY - (double)this.topPos;

            for(CreativeModeTab creativemodetab : currentPage.getVisibleTabs()) {
                if (this.checkTabClicked(creativemodetab, d0, d1)) {
                    return true;
                }
            }

            if (selectedTab.getType() != CreativeModeTab.Type.INVENTORY && this.insideScrollbar(mouseX, mouseY)) {
                this.scrolling = this.canScroll();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Called when a mouse button is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was released.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double d0 = mouseX - (double)this.leftPos;
            double d1 = mouseY - (double)this.topPos;
            this.scrolling = false;

            for(CreativeModeTab creativemodetab : currentPage.getVisibleTabs()) {
                if (this.checkTabClicked(creativemodetab, d0, d1)) {
                    this.selectTab(creativemodetab);
                    return true;
                }
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean canScroll() {
        return selectedTab.canScroll() && this.menu.canScroll();
    }

    /**
     * Sets the current creative tab, restructuring the GUI as needed.
     */
    private void selectTab(CreativeModeTab tab) {
        CreativeModeTab creativemodetab = selectedTab;
        selectedTab = tab;
        slotColor = tab.getSlotColor();
        this.quickCraftSlots.clear();
        this.menu.items.clear();
        this.clearDraggingState();
        if (selectedTab.getType() == CreativeModeTab.Type.HOTBAR) {
            HotbarManager hotbarmanager = this.minecraft.getHotbarManager();

            for (int i = 0; i < 9; i++) {
                Hotbar hotbar = hotbarmanager.get(i);
                if (hotbar.isEmpty()) {
                    for (int j = 0; j < 9; j++) {
                        if (j == i) {
                            ItemStack itemstack = new ItemStack(Items.PAPER);
                            itemstack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
                            Component component = this.minecraft.options.keyHotbarSlots[i].getTranslatedKeyMessage();
                            Component component1 = this.minecraft.options.keySaveHotbarActivator.getTranslatedKeyMessage();
                            itemstack.set(DataComponents.ITEM_NAME, Component.translatable("inventory.hotbarInfo", component1, component));
                            this.menu.items.add(itemstack);
                        } else {
                            this.menu.items.add(ItemStack.EMPTY);
                        }
                    }
                } else {
                    this.menu.items.addAll(hotbar.load(this.minecraft.level.registryAccess()));
                }
            }
        } else if (selectedTab.getType() == CreativeModeTab.Type.CATEGORY) {
            this.menu.items.addAll(selectedTab.getDisplayItems());
        }

        if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
            AbstractContainerMenu abstractcontainermenu = this.minecraft.player.inventoryMenu;
            if (this.originalSlots == null) {
                this.originalSlots = ImmutableList.copyOf(this.menu.slots);
            }

            this.menu.slots.clear();

            for (int k = 0; k < abstractcontainermenu.slots.size(); k++) {
                int l;
                int i1;
                if (k >= 5 && k < 9) {
                    int k1 = k - 5;
                    int i2 = k1 / 2;
                    int k2 = k1 % 2;
                    l = 54 + i2 * 54;
                    i1 = 6 + k2 * 27;
                } else if (k >= 0 && k < 5) {
                    l = -2000;
                    i1 = -2000;
                } else if (k == 45) {
                    l = 35;
                    i1 = 20;
                } else {
                    int j1 = k - 9;
                    int l1 = j1 % 9;
                    int j2 = j1 / 9;
                    l = 9 + l1 * 18;
                    if (k >= 36) {
                        i1 = 112;
                    } else {
                        i1 = 54 + j2 * 18;
                    }
                }

                Slot slot = new CreativeModeInventoryScreen.SlotWrapper(abstractcontainermenu.slots.get(k), k, l, i1);
                this.menu.slots.add(slot);
            }

            this.destroyItemSlot = new Slot(CONTAINER, 0, 173, 112);
            this.menu.slots.add(this.destroyItemSlot);
        } else if (creativemodetab.getType() == CreativeModeTab.Type.INVENTORY) {
            this.menu.slots.clear();
            this.menu.slots.addAll(this.originalSlots);
            this.originalSlots = null;
        }

        if (selectedTab.hasSearchBar()) {
            this.searchBox.setVisible(true);
            this.searchBox.setCanLoseFocus(false);
            this.searchBox.setFocused(true);
            if (creativemodetab != tab) {
                this.searchBox.setValue("");
            }
            this.searchBox.setWidth(selectedTab.getSearchBarWidth());
            this.searchBox.setX(this.leftPos + (82 /*default left*/ + 89 /*default width*/) - this.searchBox.getWidth());

            this.refreshSearchResults();
        } else {
            this.searchBox.setVisible(false);
            this.searchBox.setCanLoseFocus(true);
            this.searchBox.setFocused(false);
            this.searchBox.setValue("");
        }

        this.scrollOffs = 0.0F;
        this.menu.scrollTo(0.0F);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.canScroll()) {
            return false;
        } else {
            this.scrollOffs = this.menu.subtractInputFromScroll(this.scrollOffs, scrollY);
            this.menu.scrollTo(this.scrollOffs);
            return true;
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean flag = mouseX < (double)guiLeft
            || mouseY < (double)guiTop
            || mouseX >= (double)(guiLeft + this.imageWidth)
            || mouseY >= (double)(guiTop + this.imageHeight);
        this.hasClickedOutside = flag && !this.checkTabClicked(selectedTab, mouseX, mouseY);
        return this.hasClickedOutside;
    }

    protected boolean insideScrollbar(double mouseX, double mouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        int k = i + 175;
        int l = j + 18;
        int i1 = k + 14;
        int j1 = l + 112;
        return mouseX >= (double)k && mouseY >= (double)l && mouseX < (double)i1 && mouseY < (double)j1;
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.scrolling) {
            int i = this.topPos + 18;
            int j = i + 112;
            this.scrollOffs = ((float)mouseY - (float)i - 7.5F) / ((float)(j - i) - 15.0F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.menu.scrollTo(this.scrollOffs);
            return true;
        } else {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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

        if (this.destroyItemSlot != null
            && selectedTab.getType() == CreativeModeTab.Type.INVENTORY
            && this.isHovering(this.destroyItemSlot.x, this.destroyItemSlot.y, 16, 16, (double)mouseX, (double)mouseY)) {
            guiGraphics.renderTooltip(this.font, TRASH_SLOT_TOOLTIP, mouseX, mouseY);
        }

        if (this.pages.size() != 1) {
            Component page = Component.literal(String.format("%d / %d", this.pages.indexOf(this.currentPage) + 1, this.pages.size()));
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0F, 0F, 300F);
            guiGraphics.drawString(font, page.getVisualOrderText(), leftPos + (imageWidth / 2) - (font.width(page) / 2), topPos - 44, -1);
            guiGraphics.pose().popPose();
        }

        for (CreativeModeTab creativemodetab : currentPage.getVisibleTabs()) {
            if (this.checkTabHovering(guiGraphics, creativemodetab, mouseX, mouseY)) {
                break;
            }
        }

        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public List<Component> getTooltipFromContainerItem(ItemStack stack) {
        boolean flag = this.hoveredSlot != null && this.hoveredSlot instanceof CreativeModeInventoryScreen.CustomCreativeSlot;
        boolean flag1 = selectedTab.getType() == CreativeModeTab.Type.CATEGORY;
        boolean flag2 = selectedTab.hasSearchBar();
        TooltipFlag.Default tooltipflag$default = this.minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        TooltipFlag tooltipflag = flag ? tooltipflag$default.asCreative() : tooltipflag$default;
        List<Component> list = stack.getTooltipLines(Item.TooltipContext.of(this.minecraft.level), this.minecraft.player, net.neoforged.neoforge.client.ClientTooltipFlag.of(tooltipflag));
        if (flag1 && flag) {
            return list;
        } else {
            List<Component> list1 = Lists.newArrayList(list);
            if (flag2 && flag) {
                this.visibleTags.forEach(p_339283_ -> {
                    if (stack.is((TagKey<Item>)p_339283_)) {
                        list1.add(1, Component.literal("#" + p_339283_.location()).withStyle(ChatFormatting.DARK_PURPLE));
                    }
                });
            }

            int i = 1;

            for (CreativeModeTab creativemodetab : CreativeModeTabs.tabs()) {
                if (!creativemodetab.hasSearchBar() && creativemodetab.contains(stack)) {
                    list1.add(i++, creativemodetab.getDisplayName().copy().withStyle(ChatFormatting.BLUE));
                }
            }

            return list1;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        for (CreativeModeTab creativemodetab : currentPage.getVisibleTabs()) {
            if (creativemodetab != selectedTab) {
                this.renderTabButton(guiGraphics, creativemodetab);
            }
        }

        guiGraphics.blit(selectedTab.getBackgroundTexture(), this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        int j = this.leftPos + 175;
        int k = this.topPos + 18;
        int i = k + 112;
        if (selectedTab.canScroll()) {
            ResourceLocation resourcelocation = selectedTab.getScrollerSprite(); // this.canScroll() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
            guiGraphics.blitSprite(resourcelocation, j, k + (int)((float)(i - k - 17) * this.scrollOffs), 12, 15);
        }

        if (currentPage.getVisibleTabs().contains(selectedTab)) //Forge: only display tab selection when the selected tab is on the current page
        this.renderTabButton(guiGraphics, selectedTab);
        if (selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                guiGraphics,
                this.leftPos + 73,
                this.topPos + 6,
                this.leftPos + 105,
                this.topPos + 49,
                20,
                0.0625F,
                (float)mouseX,
                (float)mouseY,
                this.minecraft.player
            );
        }
    }

    private int getTabX(CreativeModeTab tab) {
        int i = currentPage.getColumn(tab);
        int j = 27;
        int k = 27 * i;
        if (tab.isAlignedRight()) {
            k = this.imageWidth - 27 * (7 - i) + 1;
        }

        return k;
    }

    private int getTabY(CreativeModeTab tab) {
        int i = 0;
        if (currentPage.isTop(tab)) {
            i -= 32;
        } else {
            i += this.imageHeight;
        }

        return i;
    }

    protected boolean checkTabClicked(CreativeModeTab creativeModeTab, double relativeMouseX, double relativeMouseY) {
        int i = this.getTabX(creativeModeTab);
        int j = this.getTabY(creativeModeTab);
        return relativeMouseX >= (double)i && relativeMouseX <= (double)(i + 26) && relativeMouseY >= (double)j && relativeMouseY <= (double)(j + 32);
    }

    protected boolean checkTabHovering(GuiGraphics guiGraphics, CreativeModeTab creativeModeTab, int mouseX, int mouseY) {
        int i = this.getTabX(creativeModeTab);
        int j = this.getTabY(creativeModeTab);
        if (this.isHovering(i + 3, j + 3, 21, 27, (double)mouseX, (double)mouseY)) {
            guiGraphics.renderTooltip(this.font, creativeModeTab.getDisplayName(), mouseX, mouseY);
            return true;
        } else {
            return false;
        }
    }

    protected void renderTabButton(GuiGraphics guiGraphics, CreativeModeTab creativeModeTab) {
        boolean flag = creativeModeTab == selectedTab;
        boolean flag1 = currentPage.isTop(creativeModeTab);
        int i = currentPage.getColumn(creativeModeTab);
        int j = this.leftPos + this.getTabX(creativeModeTab);
        int k = this.topPos - (flag1 ? 28 : -(this.imageHeight - 4));
        ResourceLocation[] aresourcelocation;
        if (flag1) {
            aresourcelocation = flag ? SELECTED_TOP_TABS : UNSELECTED_TOP_TABS;
        } else {
            aresourcelocation = flag ? SELECTED_BOTTOM_TABS : UNSELECTED_BOTTOM_TABS;
        }

        //PATCH 1.20.2: Deal with custom tab backgrounds, and deal with transparency.
        guiGraphics.blitSprite(aresourcelocation[Mth.clamp(i, 0, aresourcelocation.length)], j, k, 26, 32);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        j += 5;
        k += 8 + (flag1 ? 1 : -1);
        ItemStack itemstack = creativeModeTab.getIconItem();
        guiGraphics.renderItem(itemstack, j, k);
        guiGraphics.renderItemDecorations(this.font, itemstack, j, k);
        guiGraphics.pose().popPose();
    }

    public boolean isInventoryOpen() {
        return selectedTab.getType() == CreativeModeTab.Type.INVENTORY;
    }

    public static void handleHotbarLoadOrSave(Minecraft client, int index, boolean load, boolean save) {
        LocalPlayer localplayer = client.player;
        RegistryAccess registryaccess = localplayer.level().registryAccess();
        HotbarManager hotbarmanager = client.getHotbarManager();
        Hotbar hotbar = hotbarmanager.get(index);
        if (load) {
            List<ItemStack> list = hotbar.load(registryaccess);

            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                ItemStack itemstack = list.get(i);
                localplayer.getInventory().setItem(i, itemstack);
                client.gameMode.handleCreativeModeItemAdd(itemstack, 36 + i);
            }

            localplayer.inventoryMenu.broadcastChanges();
        } else if (save) {
            hotbar.storeFrom(localplayer.getInventory(), registryaccess);
            Component component = client.options.keyHotbarSlots[index].getTranslatedKeyMessage();
            Component component1 = client.options.keyLoadHotbarActivator.getTranslatedKeyMessage();
            Component component2 = Component.translatable("inventory.hotbarSaved", component1, component);
            client.gui.setOverlayMessage(component2, false);
            client.getNarrator().sayNow(component2);
            hotbarmanager.save();
        }
    }

    public net.neoforged.neoforge.client.gui.CreativeTabsScreenPage getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(net.neoforged.neoforge.client.gui.CreativeTabsScreenPage currentPage) {
        this.currentPage = currentPage;
    }

    @OnlyIn(Dist.CLIENT)
    static class CustomCreativeSlot extends Slot {
        public CustomCreativeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        /**
         * Return whether this slot's stack can be taken from this slot.
         */
        @Override
        public boolean mayPickup(Player player) {
            ItemStack itemstack = this.getItem();
            return super.mayPickup(player) && !itemstack.isEmpty()
                ? itemstack.isItemEnabled(player.level().enabledFeatures()) && !itemstack.has(DataComponents.CREATIVE_SLOT_LOCK)
                : itemstack.isEmpty();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ItemPickerMenu extends AbstractContainerMenu {
        /**
         * The list of items in this container.
         */
        public final NonNullList<ItemStack> items = NonNullList.create();
        private final AbstractContainerMenu inventoryMenu;

        public ItemPickerMenu(Player player) {
            super(null, 0);
            this.inventoryMenu = player.inventoryMenu;
            Inventory inventory = player.getInventory();

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 9; j++) {
                    this.addSlot(new CreativeModeInventoryScreen.CustomCreativeSlot(CreativeModeInventoryScreen.CONTAINER, i * 9 + j, 9 + j * 18, 18 + i * 18));
                }
            }

            for (int k = 0; k < 9; k++) {
                this.addSlot(new Slot(inventory, k, 9 + k * 18, 112));
            }

            this.scrollTo(0.0F);
        }

        /**
         * Determines whether supplied player can use this container
         */
        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        protected int calculateRowCount() {
            return Mth.positiveCeilDiv(this.items.size(), 9) - 5;
        }

        protected int getRowIndexForScroll(float scrollOffs) {
            return Math.max((int)((double)(scrollOffs * (float)this.calculateRowCount()) + 0.5), 0);
        }

        protected float getScrollForRowIndex(int rowIndex) {
            return Mth.clamp((float)rowIndex / (float)this.calculateRowCount(), 0.0F, 1.0F);
        }

        protected float subtractInputFromScroll(float scrollOffs, double input) {
            return Mth.clamp(scrollOffs - (float)(input / (double)this.calculateRowCount()), 0.0F, 1.0F);
        }

        /**
         * Updates the gui slot's ItemStacks based on scroll position.
         */
        public void scrollTo(float pos) {
            int i = this.getRowIndexForScroll(pos);

            for (int j = 0; j < 5; j++) {
                for (int k = 0; k < 9; k++) {
                    int l = k + (j + i) * 9;
                    if (l >= 0 && l < this.items.size()) {
                        CreativeModeInventoryScreen.CONTAINER.setItem(k + j * 9, this.items.get(l));
                    } else {
                        CreativeModeInventoryScreen.CONTAINER.setItem(k + j * 9, ItemStack.EMPTY);
                    }
                }
            }
        }

        public boolean canScroll() {
            return this.items.size() > 45;
        }

        /**
         * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
         */
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index >= this.slots.size() - 9 && index < this.slots.size()) {
                Slot slot = this.slots.get(index);
                if (slot != null && slot.hasItem()) {
                    slot.setByPlayer(ItemStack.EMPTY);
                }
            }

            return ItemStack.EMPTY;
        }

        /**
         * Called to determine if the current slot is valid for the stack merging (double-click) code. The stack passed in is null for the initial slot that was double-clicked.
         */
        @Override
        public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
            return slot.container != CreativeModeInventoryScreen.CONTAINER;
        }

        /**
         * Returns {@code true} if the player can "drag-spilt" items into this slot. Returns {@code true} by default. Called to check if the slot can be added to a list of Slots to split the held ItemStack across.
         */
        @Override
        public boolean canDragTo(Slot slot) {
            return slot.container != CreativeModeInventoryScreen.CONTAINER;
        }

        @Override
        public ItemStack getCarried() {
            return this.inventoryMenu.getCarried();
        }

        @Override
        public void setCarried(ItemStack stack) {
            this.inventoryMenu.setCarried(stack);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class SlotWrapper extends Slot {
        final Slot target;

        public SlotWrapper(Slot slot, int index, int x, int y) {
            super(slot.container, index, x, y);
            this.target = slot;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            this.target.onTake(player, stack);
        }

        /**
         * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
         */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.target.mayPlace(stack);
        }

        @Override
        public ItemStack getItem() {
            return this.target.getItem();
        }

        @Override
        public boolean hasItem() {
            return this.target.hasItem();
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            this.target.setByPlayer(newStack, oldStack);
        }

        /**
         * Helper method to put a stack in the slot.
         */
        @Override
        public void set(ItemStack stack) {
            this.target.set(stack);
        }

        @Override
        public void setChanged() {
            this.target.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return this.target.getMaxStackSize();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return this.target.getMaxStackSize(stack);
        }

        @Nullable
        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return this.target.getNoItemIcon();
        }

        /**
         * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new stack.
         */
        @Override
        public ItemStack remove(int amount) {
            return this.target.remove(amount);
        }

        @Override
        public boolean isActive() {
            return this.target.isActive();
        }

        /**
         * Return whether this slot's stack can be taken from this slot.
         */
        @Override
        public boolean mayPickup(Player player) {
            return this.target.mayPickup(player);
        }

        @Override
        public int getSlotIndex() {
            return this.target.getSlotIndex();
        }

        @Override
        public boolean isSameInventory(Slot other) {
            return this.target.isSameInventory(other);
        }

        @Override
        public Slot setBackground(ResourceLocation atlas, ResourceLocation sprite) {
            this.target.setBackground(atlas, sprite);
            return this;
        }
    }
}
