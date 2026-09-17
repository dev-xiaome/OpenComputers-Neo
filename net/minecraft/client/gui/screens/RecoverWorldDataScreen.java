package net.minecraft.client.gui.screens;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.CommonLinks;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RecoverWorldDataScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCREEN_SIDE_MARGIN = 25;
    private static final Component TITLE = Component.translatable("recover_world.title").withStyle(ChatFormatting.BOLD);
    private static final Component BUGTRACKER_BUTTON = Component.translatable("recover_world.bug_tracker");
    private static final Component RESTORE_BUTTON = Component.translatable("recover_world.restore");
    private static final Component NO_FALLBACK_TOOLTIP = Component.translatable("recover_world.no_fallback");
    private static final Component DONE_TITLE = Component.translatable("recover_world.done.title");
    private static final Component DONE_SUCCESS = Component.translatable("recover_world.done.success");
    private static final Component DONE_FAILED = Component.translatable("recover_world.done.failed");
    private static final Component NO_ISSUES = Component.translatable("recover_world.issue.none").withStyle(ChatFormatting.GREEN);
    private static final Component MISSING_FILE = Component.translatable("recover_world.issue.missing_file").withStyle(ChatFormatting.RED);
    private final BooleanConsumer callback;
    private final LinearLayout layout = LinearLayout.vertical().spacing(8);
    private final Component message;
    private final MultiLineTextWidget messageWidget;
    private final MultiLineTextWidget issuesWidget;
    private final LevelStorageSource.LevelStorageAccess storageAccess;

    public RecoverWorldDataScreen(Minecraft minecraft, BooleanConsumer callback, LevelStorageSource.LevelStorageAccess storageAccess) {
        super(TITLE);
        this.callback = callback;
        this.message = Component.translatable("recover_world.message", Component.literal(storageAccess.getLevelId()).withStyle(ChatFormatting.GRAY));
        this.messageWidget = new MultiLineTextWidget(this.message, minecraft.font);
        this.storageAccess = storageAccess;
        Exception exception = this.collectIssue(storageAccess, false);
        Exception exception1 = this.collectIssue(storageAccess, true);
        Component component = Component.empty()
            .append(this.buildInfo(storageAccess, false, exception))
            .append("\n")
            .append(this.buildInfo(storageAccess, true, exception1));
        this.issuesWidget = new MultiLineTextWidget(component, minecraft.font);
        boolean flag = exception != null && exception1 == null;
        this.layout.defaultCellSetting().alignHorizontallyCenter();
        this.layout.addChild(new StringWidget(this.title, minecraft.font));
        this.layout.addChild(this.messageWidget.setCentered(true));
        this.layout.addChild(this.issuesWidget);
        LinearLayout linearlayout = LinearLayout.horizontal().spacing(5);
        linearlayout.addChild(Button.builder(BUGTRACKER_BUTTON, ConfirmLinkScreen.confirmLink(this, CommonLinks.SNAPSHOT_BUGS_FEEDBACK)).size(120, 20).build());
        linearlayout.addChild(
                Button.builder(RESTORE_BUTTON, p_307556_ -> this.attemptRestore(minecraft))
                    .size(120, 20)
                    .tooltip(flag ? null : Tooltip.create(NO_FALLBACK_TOOLTIP))
                    .build()
            )
            .active = flag;
        this.layout.addChild(linearlayout);
        this.layout.addChild(Button.builder(CommonComponents.GUI_BACK, p_307281_ -> this.onClose()).size(120, 20).build());
        this.layout.visitWidgets(this::addRenderableWidget);
    }

    private void attemptRestore(Minecraft minecraft) {
        Exception exception = this.collectIssue(this.storageAccess, false);
        Exception exception1 = this.collectIssue(this.storageAccess, true);
        if (exception != null && exception1 == null) {
            minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("recover_world.restoring")));
            EditWorldScreen.makeBackupAndShowToast(this.storageAccess);
            if (this.storageAccess.restoreLevelDataFromOld()) {
                minecraft.setScreen(new ConfirmScreen(this.callback, DONE_TITLE, DONE_SUCCESS, CommonComponents.GUI_CONTINUE, CommonComponents.GUI_BACK));
            } else {
                minecraft.setScreen(new AlertScreen(() -> this.callback.accept(false), DONE_TITLE, DONE_FAILED));
            }
        } else {
            LOGGER.error(
                "Failed to recover world, files not as expected. level.dat: {}, level.dat_old: {}",
                exception != null ? exception.getMessage() : "no issues",
                exception1 != null ? exception1.getMessage() : "no issues"
            );
            minecraft.setScreen(new AlertScreen(() -> this.callback.accept(false), DONE_TITLE, DONE_FAILED));
        }
    }

    private Component buildInfo(LevelStorageSource.LevelStorageAccess level, boolean useFallback, @Nullable Exception exception) {
        if (useFallback && exception instanceof FileNotFoundException) {
            return Component.empty();
        } else {
            MutableComponent mutablecomponent = Component.empty();
            Instant instant = level.getFileModificationTime(useFallback);
            MutableComponent mutablecomponent1 = instant != null
                ? Component.literal(WorldSelectionList.DATE_FORMAT.format(instant))
                : Component.translatable("recover_world.state_entry.unknown");
            mutablecomponent.append(Component.translatable("recover_world.state_entry", mutablecomponent1.withStyle(ChatFormatting.GRAY)));
            if (exception == null) {
                mutablecomponent.append(NO_ISSUES);
            } else if (exception instanceof FileNotFoundException) {
                mutablecomponent.append(MISSING_FILE);
            } else if (exception instanceof ReportedNbtException) {
                mutablecomponent.append(Component.literal(exception.getCause().toString()).withStyle(ChatFormatting.RED));
            } else {
                mutablecomponent.append(Component.literal(exception.toString()).withStyle(ChatFormatting.RED));
            }

            return mutablecomponent;
        }
    }

    @Nullable
    private Exception collectIssue(LevelStorageSource.LevelStorageAccess level, boolean useFallback) {
        try {
            if (!useFallback) {
                level.getSummary(level.getDataTag());
            } else {
                level.getSummary(level.getDataTagFallback());
            }

            return null;
        } catch (NbtException | ReportedNbtException | IOException ioexception) {
            return ioexception;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.issuesWidget.setMaxWidth(this.width - 50);
        this.messageWidget.setMaxWidth(this.width - 50);
        this.layout.arrangeElements();
        FrameLayout.centerInRectangle(this.layout, this.getRectangle());
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), this.message);
    }

    @Override
    public void onClose() {
        this.callback.accept(false);
    }
}
