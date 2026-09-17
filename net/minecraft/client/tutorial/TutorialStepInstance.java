package net.minecraft.client.tutorial;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TutorialStepInstance {
    default void clear() {
    }

    default void tick() {
    }

    /**
     * Handles the player movement
     */
    default void onInput(Input input) {
    }

    default void onMouse(double velocityX, double velocityY) {
    }

    /**
     * Handles blocks and entities hovering
     */
    default void onLookAt(ClientLevel level, HitResult result) {
    }

    /**
     * Called when a player hits block to destroy it.
     */
    default void onDestroyBlock(ClientLevel level, BlockPos pos, BlockState state, float diggingStage) {
    }

    default void onOpenInventory() {
    }

    /**
     * Called when the player pick up an ItemStack
     */
    default void onGetItem(ItemStack stack) {
    }
}
