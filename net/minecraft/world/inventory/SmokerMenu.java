package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.RecipeType;

public class SmokerMenu extends AbstractFurnaceMenu {
    public SmokerMenu(int containerId, Inventory playerInventory) {
        super(MenuType.SMOKER, RecipeType.SMOKING, RecipeBookType.SMOKER, containerId, playerInventory);
    }

    public SmokerMenu(int containerId, Inventory playerInventory, Container smokerContainer, ContainerData smokerData) {
        super(MenuType.SMOKER, RecipeType.SMOKING, RecipeBookType.SMOKER, containerId, playerInventory, smokerContainer, smokerData);
    }
}
