package net.minecraft.client.tutorial;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Tutorial {
    private final Minecraft minecraft;
    @Nullable
    private TutorialStepInstance instance;
    private final List<Tutorial.TimedToast> timedToasts = Lists.newArrayList();
    private final BundleTutorial bundleTutorial;

    public Tutorial(Minecraft minecraft, Options options) {
        this.minecraft = minecraft;
        this.bundleTutorial = new BundleTutorial(this, options);
    }

    public void onInput(Input input) {
        if (this.instance != null) {
            this.instance.onInput(input);
        }
    }

    public void onMouse(double velocityX, double velocityY) {
        if (this.instance != null) {
            this.instance.onMouse(velocityX, velocityY);
        }
    }

    public void onLookAt(@Nullable ClientLevel level, @Nullable HitResult result) {
        if (this.instance != null && result != null && level != null) {
            this.instance.onLookAt(level, result);
        }
    }

    public void onDestroyBlock(ClientLevel level, BlockPos pos, BlockState state, float diggingStage) {
        if (this.instance != null) {
            this.instance.onDestroyBlock(level, pos, state, diggingStage);
        }
    }

    public void onOpenInventory() {
        if (this.instance != null) {
            this.instance.onOpenInventory();
        }
    }

    /**
     * Called when the player pick up an ItemStack
     */
    public void onGetItem(ItemStack stack) {
        if (this.instance != null) {
            this.instance.onGetItem(stack);
        }
    }

    public void stop() {
        if (this.instance != null) {
            this.instance.clear();
            this.instance = null;
        }
    }

    public void start() {
        if (this.instance != null) {
            this.stop();
        }

        this.instance = this.minecraft.options.tutorialStep.create(this);
    }

    public void addTimedToast(TutorialToast toast, int durationTicks) {
        this.timedToasts.add(new Tutorial.TimedToast(toast, durationTicks));
        this.minecraft.getToasts().addToast(toast);
    }

    public void removeTimedToast(TutorialToast toast) {
        this.timedToasts.removeIf(p_120577_ -> p_120577_.toast == toast);
        toast.hide();
    }

    public void tick() {
        this.timedToasts.removeIf(Tutorial.TimedToast::updateProgress);
        if (this.instance != null) {
            if (this.minecraft.level != null) {
                this.instance.tick();
            } else {
                this.stop();
            }
        } else if (this.minecraft.level != null) {
            this.start();
        }
    }

    /**
     * Sets a new step to the tutorial
     */
    public void setStep(TutorialSteps step) {
        this.minecraft.options.tutorialStep = step;
        this.minecraft.options.save();
        if (this.instance != null) {
            this.instance.clear();
            this.instance = step.create(this);
        }
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public boolean isSurvival() {
        return this.minecraft.gameMode == null ? false : this.minecraft.gameMode.getPlayerMode() == GameType.SURVIVAL;
    }

    public static Component key(String keybind) {
        return Component.keybind("key." + keybind).withStyle(ChatFormatting.BOLD);
    }

    public void onInventoryAction(ItemStack carriedStack, ItemStack slottedStack, ClickAction action) {
        this.bundleTutorial.onInventoryAction(carriedStack, slottedStack, action);
    }

    @OnlyIn(Dist.CLIENT)
    static final class TimedToast {
        final TutorialToast toast;
        private final int durationTicks;
        private int progress;

        TimedToast(TutorialToast toast, int durationTicks) {
            this.toast = toast;
            this.durationTicks = durationTicks;
        }

        private boolean updateProgress() {
            this.toast.updateProgress(Math.min((float)(++this.progress) / (float)this.durationTicks, 1.0F));
            if (this.progress > this.durationTicks) {
                this.toast.hide();
                return true;
            } else {
                return false;
            }
        }
    }
}
