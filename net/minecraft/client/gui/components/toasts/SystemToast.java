package net.minecraft.client.gui.components.toasts;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SystemToast implements Toast {
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/system");
    private static final int MAX_LINE_SIZE = 200;
    private static final int LINE_SPACING = 12;
    private static final int MARGIN = 10;
    private final SystemToast.SystemToastId id;
    private Component title;
    private List<FormattedCharSequence> messageLines;
    private long lastChanged;
    private boolean changed;
    private final int width;
    private boolean forceHide;

    public SystemToast(SystemToast.SystemToastId id, Component title, @Nullable Component message) {
        this(
            id,
            title,
            nullToEmpty(message),
            Math.max(160, 30 + Math.max(Minecraft.getInstance().font.width(title), message == null ? 0 : Minecraft.getInstance().font.width(message)))
        );
    }

    public static SystemToast multiline(Minecraft minecraft, SystemToast.SystemToastId id, Component title, Component message) {
        Font font = minecraft.font;
        List<FormattedCharSequence> list = font.split(message, 200);
        int i = Math.max(200, list.stream().mapToInt(font::width).max().orElse(200));
        return new SystemToast(id, title, list, i + 30);
    }

    private SystemToast(SystemToast.SystemToastId id, Component title, List<FormattedCharSequence> messageLines, int width) {
        this.id = id;
        this.title = title;
        this.messageLines = messageLines;
        this.width = width;
    }

    private static ImmutableList<FormattedCharSequence> nullToEmpty(@Nullable Component message) {
        return message == null ? ImmutableList.of() : ImmutableList.of(message.getVisualOrderText());
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return 20 + Math.max(this.messageLines.size(), 1) * 12;
    }

    public void forceHide() {
        this.forceHide = true;
    }

    @Override
    public Toast.Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        if (this.changed) {
            this.lastChanged = timeSinceLastVisible;
            this.changed = false;
        }

        int i = this.width();
        if (i == 160 && this.messageLines.size() <= 1) {
            guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, i, this.height());
        } else {
            int j = this.height();
            int k = 28;
            int l = Math.min(4, j - 28);
            this.renderBackgroundRow(guiGraphics, i, 0, 0, 28);

            for (int i1 = 28; i1 < j - l; i1 += 10) {
                this.renderBackgroundRow(guiGraphics, i, 16, i1, Math.min(16, j - i1 - l));
            }

            this.renderBackgroundRow(guiGraphics, i, 32 - l, j - l, l);
        }

        if (this.messageLines.isEmpty()) {
            guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 18, 12, -256, false);
        } else {
            guiGraphics.drawString(toastComponent.getMinecraft().font, this.title, 18, 7, -256, false);

            for (int j1 = 0; j1 < this.messageLines.size(); j1++) {
                guiGraphics.drawString(toastComponent.getMinecraft().font, this.messageLines.get(j1), 18, 18 + j1 * 12, -1, false);
            }
        }

        double d0 = (double)this.id.displayTime * toastComponent.getNotificationDisplayTimeMultiplier();
        long k1 = timeSinceLastVisible - this.lastChanged;
        return !this.forceHide && (double)k1 < d0 ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    private void renderBackgroundRow(GuiGraphics guiGraphics, int width, int vOffset, int y, int height) {
        int i = vOffset == 0 ? 20 : 5;
        int j = Math.min(60, width - i);
        ResourceLocation resourcelocation = BACKGROUND_SPRITE;
        guiGraphics.blitSprite(resourcelocation, 160, 32, 0, vOffset, 0, y, i, height);

        for (int k = i; k < width - j; k += 64) {
            guiGraphics.blitSprite(resourcelocation, 160, 32, 32, vOffset, k, y, Math.min(64, width - k - j), height);
        }

        guiGraphics.blitSprite(resourcelocation, 160, 32, 160 - j, vOffset, width - j, y, j, height);
    }

    public void reset(Component title, @Nullable Component message) {
        this.title = title;
        this.messageLines = nullToEmpty(message);
        this.changed = true;
    }

    public SystemToast.SystemToastId getToken() {
        return this.id;
    }

    public static void add(ToastComponent toastComponent, SystemToast.SystemToastId id, Component title, @Nullable Component message) {
        toastComponent.addToast(new SystemToast(id, title, message));
    }

    public static void addOrUpdate(ToastComponent toastComponent, SystemToast.SystemToastId id, Component title, @Nullable Component message) {
        SystemToast systemtoast = toastComponent.getToast(SystemToast.class, id);
        if (systemtoast == null) {
            add(toastComponent, id, title, message);
        } else {
            systemtoast.reset(title, message);
        }
    }

    public static void forceHide(ToastComponent toastComponent, SystemToast.SystemToastId id) {
        SystemToast systemtoast = toastComponent.getToast(SystemToast.class, id);
        if (systemtoast != null) {
            systemtoast.forceHide();
        }
    }

    public static void onWorldAccessFailure(Minecraft minecraft, String message) {
        add(
            minecraft.getToasts(),
            SystemToast.SystemToastId.WORLD_ACCESS_FAILURE,
            Component.translatable("selectWorld.access_failure"),
            Component.literal(message)
        );
    }

    public static void onWorldDeleteFailure(Minecraft minecraft, String message) {
        add(
            minecraft.getToasts(),
            SystemToast.SystemToastId.WORLD_ACCESS_FAILURE,
            Component.translatable("selectWorld.delete_failure"),
            Component.literal(message)
        );
    }

    public static void onPackCopyFailure(Minecraft minecraft, String message) {
        add(minecraft.getToasts(), SystemToast.SystemToastId.PACK_COPY_FAILURE, Component.translatable("pack.copyFailure"), Component.literal(message));
    }

    public static void onFileDropFailure(Minecraft minecraft, int failedFileCount) {
        add(
            minecraft.getToasts(),
            SystemToast.SystemToastId.FILE_DROP_FAILURE,
            Component.translatable("gui.fileDropFailure.title"),
            Component.translatable("gui.fileDropFailure.detail", failedFileCount)
        );
    }

    public static void onLowDiskSpace(Minecraft minecraft) {
        addOrUpdate(
            minecraft.getToasts(),
            SystemToast.SystemToastId.LOW_DISK_SPACE,
            Component.translatable("chunk.toast.lowDiskSpace"),
            Component.translatable("chunk.toast.lowDiskSpace.description")
        );
    }

    public static void onChunkLoadFailure(Minecraft minecraft, ChunkPos chunkPos) {
        addOrUpdate(
            minecraft.getToasts(),
            SystemToast.SystemToastId.CHUNK_LOAD_FAILURE,
            Component.translatable("chunk.toast.loadFailure", Component.translationArg(chunkPos)).withStyle(ChatFormatting.RED),
            Component.translatable("chunk.toast.checkLog")
        );
    }

    public static void onChunkSaveFailure(Minecraft minecraft, ChunkPos chunkPos) {
        addOrUpdate(
            minecraft.getToasts(),
            SystemToast.SystemToastId.CHUNK_SAVE_FAILURE,
            Component.translatable("chunk.toast.saveFailure", Component.translationArg(chunkPos)).withStyle(ChatFormatting.RED),
            Component.translatable("chunk.toast.checkLog")
        );
    }

    @OnlyIn(Dist.CLIENT)
    public static class SystemToastId {
        public static final SystemToast.SystemToastId NARRATOR_TOGGLE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId WORLD_BACKUP = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId PACK_LOAD_FAILURE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId WORLD_ACCESS_FAILURE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId PACK_COPY_FAILURE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId FILE_DROP_FAILURE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId PERIODIC_NOTIFICATION = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId LOW_DISK_SPACE = new SystemToast.SystemToastId(10000L);
        public static final SystemToast.SystemToastId CHUNK_LOAD_FAILURE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId CHUNK_SAVE_FAILURE = new SystemToast.SystemToastId();
        public static final SystemToast.SystemToastId UNSECURE_SERVER_WARNING = new SystemToast.SystemToastId(10000L);
        final long displayTime;

        public SystemToastId(long displayTime) {
            this.displayTime = displayTime;
        }

        public SystemToastId() {
            this(5000L);
        }
    }
}
