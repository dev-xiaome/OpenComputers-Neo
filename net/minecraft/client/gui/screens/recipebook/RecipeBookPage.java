package net.minecraft.client.gui.screens.recipebook;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RecipeBookPage {
    public static final int ITEMS_PER_PAGE = 20;
    private static final WidgetSprites PAGE_FORWARD_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/page_forward"), ResourceLocation.withDefaultNamespace("recipe_book/page_forward_highlighted")
    );
    private static final WidgetSprites PAGE_BACKWARD_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/page_backward"), ResourceLocation.withDefaultNamespace("recipe_book/page_backward_highlighted")
    );
    private final List<RecipeButton> buttons = Lists.newArrayListWithCapacity(20);
    @Nullable
    private RecipeButton hoveredButton;
    private final OverlayRecipeComponent overlay = new OverlayRecipeComponent();
    private Minecraft minecraft;
    private final List<RecipeShownListener> showListeners = Lists.newArrayList();
    private List<RecipeCollection> recipeCollections = ImmutableList.of();
    private StateSwitchingButton forwardButton;
    private StateSwitchingButton backButton;
    private int totalPages;
    private int currentPage;
    private RecipeBook recipeBook;
    @Nullable
    private RecipeHolder<?> lastClickedRecipe;
    @Nullable
    private RecipeCollection lastClickedRecipeCollection;

    public RecipeBookPage() {
        for (int i = 0; i < 20; i++) {
            this.buttons.add(new RecipeButton());
        }
    }

    public void init(Minecraft minecraft, int x, int y) {
        this.minecraft = minecraft;
        this.recipeBook = minecraft.player.getRecipeBook();

        for (int i = 0; i < this.buttons.size(); i++) {
            this.buttons.get(i).setPosition(x + 11 + 25 * (i % 5), y + 31 + 25 * (i / 5));
        }

        this.forwardButton = new StateSwitchingButton(x + 93, y + 137, 12, 17, false);
        this.forwardButton.initTextureValues(PAGE_FORWARD_SPRITES);
        this.backButton = new StateSwitchingButton(x + 38, y + 137, 12, 17, true);
        this.backButton.initTextureValues(PAGE_BACKWARD_SPRITES);
    }

    public void addListener(RecipeBookComponent listener) {
        this.showListeners.remove(listener);
        this.showListeners.add(listener);
    }

    public void updateCollections(List<RecipeCollection> recipeCollections, boolean resetPageNumber) {
        this.recipeCollections = recipeCollections;
        this.totalPages = (int)Math.ceil((double)recipeCollections.size() / 20.0);
        if (this.totalPages <= this.currentPage || resetPageNumber) {
            this.currentPage = 0;
        }

        this.updateButtonsForPage();
    }

    private void updateButtonsForPage() {
        int i = 20 * this.currentPage;

        for (int j = 0; j < this.buttons.size(); j++) {
            RecipeButton recipebutton = this.buttons.get(j);
            if (i + j < this.recipeCollections.size()) {
                RecipeCollection recipecollection = this.recipeCollections.get(i + j);
                recipebutton.init(recipecollection, this);
                recipebutton.visible = true;
            } else {
                recipebutton.visible = false;
            }
        }

        this.updateArrowButtons();
    }

    private void updateArrowButtons() {
        this.forwardButton.visible = this.totalPages > 1 && this.currentPage < this.totalPages - 1;
        this.backButton.visible = this.totalPages > 1 && this.currentPage > 0;
    }

    public void render(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        if (this.totalPages > 1) {
            Component component = Component.translatable("gui.recipebook.page", this.currentPage + 1, this.totalPages);
            int i = this.minecraft.font.width(component);
            guiGraphics.drawString(this.minecraft.font, component, x - i / 2 + 73, y + 141, -1, false);
        }

        this.hoveredButton = null;

        for (RecipeButton recipebutton : this.buttons) {
            recipebutton.render(guiGraphics, mouseX, mouseY, partialTick);
            if (recipebutton.visible && recipebutton.isHoveredOrFocused()) {
                this.hoveredButton = recipebutton;
            }
        }

        this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.forwardButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.overlay.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    public void renderTooltip(GuiGraphics guiGraphics, int x, int y) {
        if (this.minecraft.screen != null && this.hoveredButton != null && !this.overlay.isVisible()) {
            guiGraphics.renderComponentTooltip(this.minecraft.font, this.hoveredButton.getTooltipText(), x, y);
        }
    }

    @Nullable
    public RecipeHolder<?> getLastClickedRecipe() {
        return this.lastClickedRecipe;
    }

    @Nullable
    public RecipeCollection getLastClickedRecipeCollection() {
        return this.lastClickedRecipeCollection;
    }

    public void setInvisible() {
        this.overlay.setVisible(false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int p_100413_, int p_100414_, int p_100415_, int p_100416_) {
        this.lastClickedRecipe = null;
        this.lastClickedRecipeCollection = null;
        if (this.overlay.isVisible()) {
            if (this.overlay.mouseClicked(mouseX, mouseY, button)) {
                this.lastClickedRecipe = this.overlay.getLastRecipeClicked();
                this.lastClickedRecipeCollection = this.overlay.getRecipeCollection();
            } else {
                this.overlay.setVisible(false);
            }

            return true;
        } else if (this.forwardButton.mouseClicked(mouseX, mouseY, button)) {
            this.currentPage++;
            this.updateButtonsForPage();
            return true;
        } else if (this.backButton.mouseClicked(mouseX, mouseY, button)) {
            this.currentPage--;
            this.updateButtonsForPage();
            return true;
        } else {
            for (RecipeButton recipebutton : this.buttons) {
                if (recipebutton.mouseClicked(mouseX, mouseY, button)) {
                    if (button == 0) {
                        this.lastClickedRecipe = recipebutton.getRecipe();
                        this.lastClickedRecipeCollection = recipebutton.getCollection();
                    } else if (button == 1 && !this.overlay.isVisible() && !recipebutton.isOnlyOption()) {
                        this.overlay
                            .init(
                                this.minecraft,
                                recipebutton.getCollection(),
                                recipebutton.getX(),
                                recipebutton.getY(),
                                p_100413_ + p_100415_ / 2,
                                p_100414_ + 13 + p_100416_ / 2,
                                (float)recipebutton.getWidth()
                            );
                    }

                    return true;
                }
            }

            return false;
        }
    }

    public void recipesShown(List<RecipeHolder<?>> recipes) {
        for (RecipeShownListener recipeshownlistener : this.showListeners) {
            recipeshownlistener.recipesShown(recipes);
        }
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public RecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    protected void listButtons(Consumer<AbstractWidget> consumer) {
        consumer.accept(this.forwardButton);
        consumer.accept(this.backButton);
        this.buttons.forEach(consumer);
    }
}
