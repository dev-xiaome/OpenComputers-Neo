package net.minecraft.client;

import com.mojang.logging.LogUtils;
import com.mojang.text2speech.Narrator;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.main.SilentInitException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class GameNarrator {
    public static final Component NO_TITLE = CommonComponents.EMPTY;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    private final Narrator narrator = Narrator.getNarrator();

    public GameNarrator(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void sayChat(Component message) {
        if (this.getStatus().shouldNarrateChat()) {
            String s = message.getString();
            this.logNarratedMessage(s);
            this.narrator.say(s, false);
        }
    }

    public void say(Component message) {
        String s = message.getString();
        if (this.getStatus().shouldNarrateSystem() && !s.isEmpty()) {
            this.logNarratedMessage(s);
            this.narrator.say(s, false);
        }
    }

    public void sayNow(Component message) {
        this.sayNow(message.getString());
    }

    public void sayNow(String message) {
        if (this.getStatus().shouldNarrateSystem() && !message.isEmpty()) {
            this.logNarratedMessage(message);
            if (this.narrator.active()) {
                this.narrator.clear();
                this.narrator.say(message, true);
            }
        }
    }

    private NarratorStatus getStatus() {
        return this.minecraft.options.narrator().get();
    }

    private void logNarratedMessage(String message) {
        if (SharedConstants.IS_RUNNING_IN_IDE && false) {
            LOGGER.debug("Narrating: {}", message.replaceAll("\n", "\\\\n"));
        }
    }

    public void updateNarratorStatus(NarratorStatus status) {
        this.clear();
        this.narrator.say(Component.translatable("options.narrator").append(" : ").append(status.getName()).getString(), true);
        ToastComponent toastcomponent = Minecraft.getInstance().getToasts();
        if (this.narrator.active()) {
            if (status == NarratorStatus.OFF) {
                SystemToast.addOrUpdate(toastcomponent, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.translatable("narrator.toast.disabled"), null);
            } else {
                SystemToast.addOrUpdate(
                    toastcomponent, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.translatable("narrator.toast.enabled"), status.getName()
                );
            }
        } else {
            SystemToast.addOrUpdate(
                toastcomponent,
                SystemToast.SystemToastId.NARRATOR_TOGGLE,
                Component.translatable("narrator.toast.disabled"),
                Component.translatable("options.narrator.notavailable")
            );
        }
    }

    public boolean isActive() {
        return this.narrator.active();
    }

    public void clear() {
        if (this.getStatus() != NarratorStatus.OFF && this.narrator.active()) {
            this.narrator.clear();
        }
    }

    public void destroy() {
        this.narrator.destroy();
    }

    public void checkStatus(boolean narratorEnabled) {
        if (narratorEnabled
            && !this.isActive()
            && !TinyFileDialogs.tinyfd_messageBox(
                "Minecraft",
                "Failed to initialize text-to-speech library. Do you want to continue?\nIf this problem persists, please report it at bugs.mojang.com",
                "yesno",
                "error",
                true
            )) {
            throw new GameNarrator.NarratorInitException("Narrator library is not active");
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class NarratorInitException extends SilentInitException {
        public NarratorInitException(String p_288985_) {
            super(p_288985_);
        }
    }
}
