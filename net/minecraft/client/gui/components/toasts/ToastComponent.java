package net.minecraft.client.gui.components.toasts;

import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ToastComponent {
    private static final int SLOT_COUNT = 5;
    private static final int NO_SPACE = -1;
    final Minecraft minecraft;
    private final List<ToastComponent.ToastInstance<?>> visible = new ArrayList<>();
    private final BitSet occupiedSlots = new BitSet(5);
    private final Deque<Toast> queued = Queues.newArrayDeque();

    public ToastComponent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(GuiGraphics guiGraphics) {
        if (!this.minecraft.options.hideGui) {
            int i = guiGraphics.guiWidth();
            this.visible.removeIf(p_280780_ -> {
                if (p_280780_ != null && p_280780_.render(i, guiGraphics)) {
                    this.occupiedSlots.clear(p_280780_.index, p_280780_.index + p_280780_.slotCount);
                    return true;
                } else {
                    return false;
                }
            });
            if (!this.queued.isEmpty() && this.freeSlots() > 0) {
                this.queued.removeIf(p_243239_ -> {
                    int j = p_243239_.slotCount();
                    int k = this.findFreeIndex(j);
                    if (k != -1) {
                        this.visible.add(new ToastComponent.ToastInstance<>(p_243239_, k, j));
                        this.occupiedSlots.set(k, k + j);
                        return true;
                    } else {
                        return false;
                    }
                });
            }
        }
    }

    private int findFreeIndex(int slotCount) {
        if (this.freeSlots() >= slotCount) {
            int i = 0;

            for (int j = 0; j < 5; j++) {
                if (this.occupiedSlots.get(j)) {
                    i = 0;
                } else if (++i == slotCount) {
                    return j + 1 - i;
                }
            }
        }

        return -1;
    }

    private int freeSlots() {
        return 5 - this.occupiedSlots.cardinality();
    }

    @Nullable
    public <T extends Toast> T getToast(Class<? extends T> toastClass, Object token) {
        for (ToastComponent.ToastInstance<?> toastinstance : this.visible) {
            if (toastinstance != null && toastClass.isAssignableFrom(toastinstance.getToast().getClass()) && toastinstance.getToast().getToken().equals(token)
                )
             {
                return (T)toastinstance.getToast();
            }
        }

        for (Toast toast : this.queued) {
            if (toastClass.isAssignableFrom(toast.getClass()) && toast.getToken().equals(token)) {
                return (T)toast;
            }
        }

        return null;
    }

    public void clear() {
        this.occupiedSlots.clear();
        this.visible.clear();
        this.queued.clear();
    }

    public void addToast(Toast toast) {
        if (net.neoforged.neoforge.client.ClientHooks.onToastAdd(toast)) return;
        this.queued.add(toast);
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public double getNotificationDisplayTimeMultiplier() {
        return this.minecraft.options.notificationDisplayTime().get();
    }

    @OnlyIn(Dist.CLIENT)
    class ToastInstance<T extends Toast> {
        private static final long ANIMATION_TIME = 600L;
        private final T toast;
        final int index;
        final int slotCount;
        private long animationTime = -1L;
        private long visibleTime = -1L;
        private Toast.Visibility visibility = Toast.Visibility.SHOW;

        ToastInstance(T toast, int index, int slotCount) {
            this.toast = toast;
            this.index = index;
            this.slotCount = slotCount;
        }

        public T getToast() {
            return this.toast;
        }

        private float getVisibility(long time) {
            float f = Mth.clamp((float)(time - this.animationTime) / 600.0F, 0.0F, 1.0F);
            f *= f;
            return this.visibility == Toast.Visibility.HIDE ? 1.0F - f : f;
        }

        public boolean render(int screenWidth, GuiGraphics guiGraphics) {
            long i = Util.getMillis();
            if (this.animationTime == -1L) {
                this.animationTime = i;
                this.visibility.playSound(ToastComponent.this.minecraft.getSoundManager());
            }

            if (this.visibility == Toast.Visibility.SHOW && i - this.animationTime <= 600L) {
                this.visibleTime = i;
            }

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate((float)screenWidth - (float)this.toast.width() * this.getVisibility(i), (float)(this.index * 32), 800.0F);
            Toast.Visibility toast$visibility = this.toast.render(guiGraphics, ToastComponent.this, i - this.visibleTime);
            guiGraphics.pose().popPose();
            if (toast$visibility != this.visibility) {
                this.animationTime = i - (long)((int)((1.0F - this.getVisibility(i)) * 600.0F));
                this.visibility = toast$visibility;
                this.visibility.playSound(ToastComponent.this.minecraft.getSoundManager());
            }

            return this.visibility == Toast.Visibility.HIDE && i - this.animationTime > 600L;
        }
    }
}
