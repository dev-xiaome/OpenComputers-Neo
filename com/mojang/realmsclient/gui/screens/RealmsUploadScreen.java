package com.mojang.realmsclient.gui.screens;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.Unit;
import com.mojang.realmsclient.client.FileUpload;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.client.UploadStatus;
import com.mojang.realmsclient.dto.UploadInfo;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.exception.RetryCallException;
import com.mojang.realmsclient.util.UploadTokenCache;
import com.mojang.realmsclient.util.task.LongRunningTask;
import com.mojang.realmsclient.util.task.RealmCreationTask;
import com.mojang.realmsclient.util.task.SwitchSlotTask;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmsUploadScreen extends RealmsScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ReentrantLock UPLOAD_LOCK = new ReentrantLock();
    private static final int BAR_WIDTH = 200;
    private static final int BAR_TOP = 80;
    private static final int BAR_BOTTOM = 95;
    private static final int BAR_BORDER = 1;
    private static final String[] DOTS = new String[]{"", ".", ". .", ". . ."};
    private static final Component VERIFYING_TEXT = Component.translatable("mco.upload.verifying");
    private final RealmsResetWorldScreen lastScreen;
    private final LevelSummary selectedLevel;
    @Nullable
    private final RealmCreationTask realmCreationTask;
    private final long realmId;
    private final int slotId;
    private final UploadStatus uploadStatus;
    private final RateLimiter narrationRateLimiter;
    @Nullable
    private volatile Component[] errorMessage;
    private volatile Component status = Component.translatable("mco.upload.preparing");
    @Nullable
    private volatile String progress;
    private volatile boolean cancelled;
    private volatile boolean uploadFinished;
    private volatile boolean showDots = true;
    private volatile boolean uploadStarted;
    @Nullable
    private Button backButton;
    @Nullable
    private Button cancelButton;
    private int tickCount;
    @Nullable
    private Long previousWrittenBytes;
    @Nullable
    private Long previousTimeSnapshot;
    private long bytesPersSecond;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public RealmsUploadScreen(@Nullable RealmCreationTask realmCreationTask, long realmId, int slotId, RealmsResetWorldScreen lastScreen, LevelSummary selectedLevel) {
        super(GameNarrator.NO_TITLE);
        this.realmCreationTask = realmCreationTask;
        this.realmId = realmId;
        this.slotId = slotId;
        this.lastScreen = lastScreen;
        this.selectedLevel = selectedLevel;
        this.uploadStatus = new UploadStatus();
        this.narrationRateLimiter = RateLimiter.create(0.1F);
    }

    @Override
    public void init() {
        this.backButton = this.layout.addToFooter(Button.builder(CommonComponents.GUI_BACK, p_90118_ -> this.onBack()).build());
        this.backButton.visible = false;
        this.cancelButton = this.layout.addToFooter(Button.builder(CommonComponents.GUI_CANCEL, p_90104_ -> this.onCancel()).build());
        if (!this.uploadStarted) {
            if (this.lastScreen.slot == -1) {
                this.uploadStarted = true;
                this.upload();
            } else {
                List<LongRunningTask> list = new ArrayList<>();
                if (this.realmCreationTask != null) {
                    list.add(this.realmCreationTask);
                }

                list.add(new SwitchSlotTask(this.realmId, this.lastScreen.slot, () -> {
                    if (!this.uploadStarted) {
                        this.uploadStarted = true;
                        this.minecraft.execute(() -> {
                            this.minecraft.setScreen(this);
                            this.upload();
                        });
                    }
                }));
                this.minecraft.setScreen(new RealmsLongRunningMcoTaskScreen(this.lastScreen, list.toArray(new LongRunningTask[0])));
            }
        }

        this.layout.visitWidgets(p_321350_ -> {
            AbstractWidget abstractwidget = this.addRenderableWidget(p_321350_);
        });
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    private void onBack() {
        this.minecraft.setScreen(new RealmsConfigureWorldScreen(new RealmsMainScreen(new TitleScreen()), this.realmId));
    }

    private void onCancel() {
        this.cancelled = true;
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (this.showDots) {
                this.onCancel();
            } else {
                this.onBack();
            }

            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (!this.uploadFinished
            && this.uploadStatus.bytesWritten != 0L
            && this.uploadStatus.bytesWritten == this.uploadStatus.totalBytes
            && this.cancelButton != null) {
            this.status = VERIFYING_TEXT;
            this.cancelButton.active = false;
        }

        guiGraphics.drawCenteredString(this.font, this.status, this.width / 2, 50, -1);
        if (this.showDots) {
            guiGraphics.drawString(this.font, DOTS[this.tickCount / 10 % DOTS.length], this.width / 2 + this.font.width(this.status) / 2 + 5, 50, -1, false);
        }

        if (this.uploadStatus.bytesWritten != 0L && !this.cancelled) {
            this.drawProgressBar(guiGraphics);
            this.drawUploadSpeed(guiGraphics);
        }

        Component[] acomponent = this.errorMessage;
        if (acomponent != null) {
            for (int i = 0; i < acomponent.length; i++) {
                guiGraphics.drawCenteredString(this.font, acomponent[i], this.width / 2, 110 + 12 * i, -65536);
            }
        }
    }

    private void drawProgressBar(GuiGraphics guiGraphics) {
        double d0 = Math.min((double)this.uploadStatus.bytesWritten / (double)this.uploadStatus.totalBytes, 1.0);
        this.progress = String.format(Locale.ROOT, "%.1f", d0 * 100.0);
        int i = (this.width - 200) / 2;
        int j = i + (int)Math.round(200.0 * d0);
        guiGraphics.fill(i - 1, 79, j + 1, 96, -1);
        guiGraphics.fill(i, 80, j, 95, -8355712);
        guiGraphics.drawCenteredString(this.font, Component.translatable("mco.upload.percent", this.progress), this.width / 2, 84, -1);
    }

    private void drawUploadSpeed(GuiGraphics guiGraphics) {
        if (this.tickCount % 20 == 0) {
            if (this.previousWrittenBytes != null && this.previousTimeSnapshot != null) {
                long i = Util.getMillis() - this.previousTimeSnapshot;
                if (i == 0L) {
                    i = 1L;
                }

                this.bytesPersSecond = 1000L * (this.uploadStatus.bytesWritten - this.previousWrittenBytes) / i;
                this.drawUploadSpeed0(guiGraphics, this.bytesPersSecond);
            }

            this.previousWrittenBytes = this.uploadStatus.bytesWritten;
            this.previousTimeSnapshot = Util.getMillis();
        } else {
            this.drawUploadSpeed0(guiGraphics, this.bytesPersSecond);
        }
    }

    private void drawUploadSpeed0(GuiGraphics guiGraphics, long bytesPerSecond) {
        String s = this.progress;
        if (bytesPerSecond > 0L && s != null) {
            int i = this.font.width(s);
            String s1 = "(" + Unit.humanReadable(bytesPerSecond) + "/s)";
            guiGraphics.drawString(this.font, s1, this.width / 2 + i / 2 + 15, 84, -1, false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;
        if (this.narrationRateLimiter.tryAcquire(1)) {
            Component component = this.createProgressNarrationMessage();
            this.minecraft.getNarrator().sayNow(component);
        }
    }

    private Component createProgressNarrationMessage() {
        List<Component> list = Lists.newArrayList();
        list.add(this.status);
        if (this.progress != null) {
            list.add(Component.translatable("mco.upload.percent", this.progress));
        }

        Component[] acomponent = this.errorMessage;
        if (acomponent != null) {
            list.addAll(Arrays.asList(acomponent));
        }

        return CommonComponents.joinLines(list);
    }

    private void upload() {
        new Thread(
                () -> {
                    File file1 = null;
                    RealmsClient realmsclient = RealmsClient.create();

                    try {
                        if (!UPLOAD_LOCK.tryLock(1L, TimeUnit.SECONDS)) {
                            this.status = Component.translatable("mco.upload.close.failure");
                        } else {
                            UploadInfo uploadinfo = null;

                            for (int i = 0; i < 20; i++) {
                                try {
                                    if (this.cancelled) {
                                        this.uploadCancelled();
                                        return;
                                    }

                                    uploadinfo = realmsclient.requestUploadInfo(this.realmId, UploadTokenCache.get(this.realmId));
                                    if (uploadinfo != null) {
                                        break;
                                    }
                                } catch (RetryCallException retrycallexception) {
                                    Thread.sleep((long)(retrycallexception.delaySeconds * 1000));
                                }
                            }

                            if (uploadinfo == null) {
                                this.status = Component.translatable("mco.upload.close.failure");
                            } else {
                                UploadTokenCache.put(this.realmId, uploadinfo.getToken());
                                if (!uploadinfo.isWorldClosed()) {
                                    this.status = Component.translatable("mco.upload.close.failure");
                                } else if (this.cancelled) {
                                    this.uploadCancelled();
                                } else {
                                    File file2 = new File(this.minecraft.gameDirectory.getAbsolutePath(), "saves");
                                    file1 = this.tarGzipArchive(new File(file2, this.selectedLevel.getLevelId()));
                                    if (this.cancelled) {
                                        this.uploadCancelled();
                                    } else if (this.verify(file1)) {
                                        this.status = Component.translatable("mco.upload.uploading", this.selectedLevel.getLevelName());
                                        FileUpload fileupload = new FileUpload(
                                            file1,
                                            this.realmId,
                                            this.slotId,
                                            uploadinfo,
                                            this.minecraft.getUser(),
                                            SharedConstants.getCurrentVersion().getName(),
                                            this.selectedLevel.levelVersion().minecraftVersionName(),
                                            this.uploadStatus
                                        );
                                        fileupload.upload(p_319370_ -> {
                                            if (p_319370_.statusCode >= 200 && p_319370_.statusCode < 300) {
                                                this.uploadFinished = true;
                                                this.status = Component.translatable("mco.upload.done");
                                                if (this.backButton != null) {
                                                    this.backButton.setMessage(CommonComponents.GUI_DONE);
                                                }

                                                UploadTokenCache.invalidate(this.realmId);
                                            } else if (p_319370_.statusCode == 400 && p_319370_.errorMessage != null) {
                                                this.setErrorMessage(Component.translatable("mco.upload.failed", p_319370_.errorMessage));
                                            } else {
                                                this.setErrorMessage(Component.translatable("mco.upload.failed", p_319370_.statusCode));
                                            }
                                        });

                                        while (!fileupload.isFinished()) {
                                            if (this.cancelled) {
                                                fileupload.cancel();
                                                this.uploadCancelled();
                                                return;
                                            }

                                            try {
                                                Thread.sleep(500L);
                                            } catch (InterruptedException interruptedexception) {
                                                LOGGER.error("Failed to check Realms file upload status");
                                            }
                                        }
                                    } else {
                                        long j = file1.length();
                                        Unit unit = Unit.getLargest(j);
                                        Unit unit1 = Unit.getLargest(5368709120L);
                                        if (Unit.humanReadable(j, unit).equals(Unit.humanReadable(5368709120L, unit1)) && unit != Unit.B) {
                                            Unit unit2 = Unit.values()[unit.ordinal() - 1];
                                            this.setErrorMessage(
                                                Component.translatable("mco.upload.size.failure.line1", this.selectedLevel.getLevelName()),
                                                Component.translatable(
                                                    "mco.upload.size.failure.line2", Unit.humanReadable(j, unit2), Unit.humanReadable(5368709120L, unit2)
                                                )
                                            );
                                        } else {
                                            this.setErrorMessage(
                                                Component.translatable("mco.upload.size.failure.line1", this.selectedLevel.getLevelName()),
                                                Component.translatable(
                                                    "mco.upload.size.failure.line2", Unit.humanReadable(j, unit), Unit.humanReadable(5368709120L, unit1)
                                                )
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ioexception) {
                        this.setErrorMessage(Component.translatable("mco.upload.failed", ioexception.getMessage()));
                    } catch (RealmsServiceException realmsserviceexception) {
                        this.setErrorMessage(Component.translatable("mco.upload.failed", realmsserviceexception.realmsError.errorMessage()));
                    } catch (InterruptedException interruptedexception1) {
                        LOGGER.error("Could not acquire upload lock");
                    } finally {
                        this.uploadFinished = true;
                        if (UPLOAD_LOCK.isHeldByCurrentThread()) {
                            UPLOAD_LOCK.unlock();
                            this.showDots = false;
                            if (this.backButton != null) {
                                this.backButton.visible = true;
                            }

                            if (this.cancelButton != null) {
                                this.cancelButton.visible = false;
                            }

                            if (file1 != null) {
                                LOGGER.debug("Deleting file {}", file1.getAbsolutePath());
                                file1.delete();
                            }
                        } else {
                            return;
                        }
                    }
                }
            )
            .start();
    }

    private void setErrorMessage(Component... errorMessage) {
        this.errorMessage = errorMessage;
    }

    private void uploadCancelled() {
        this.status = Component.translatable("mco.upload.cancelled");
        LOGGER.debug("Upload was cancelled");
    }

    private boolean verify(File file) {
        return file.length() < 5368709120L;
    }

    private File tarGzipArchive(File file) throws IOException {
        TarArchiveOutputStream tararchiveoutputstream = null;

        File file2;
        try {
            File file1 = File.createTempFile("realms-upload-file", ".tar.gz");
            tararchiveoutputstream = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(file1)));
            tararchiveoutputstream.setLongFileMode(3);
            this.addFileToTarGz(tararchiveoutputstream, file.getAbsolutePath(), "world", true);
            tararchiveoutputstream.finish();
            file2 = file1;
        } finally {
            if (tararchiveoutputstream != null) {
                tararchiveoutputstream.close();
            }
        }

        return file2;
    }

    private void addFileToTarGz(TarArchiveOutputStream tarArchiveOutputStream, String pathname, String name, boolean rootDirectory) throws IOException {
        if (!this.cancelled) {
            File file1 = new File(pathname);
            String s = rootDirectory ? name : name + file1.getName();
            TarArchiveEntry tararchiveentry = new TarArchiveEntry(file1, s);
            tarArchiveOutputStream.putArchiveEntry(tararchiveentry);
            if (file1.isFile()) {
                try (InputStream inputstream = new FileInputStream(file1)) {
                    inputstream.transferTo(tarArchiveOutputStream);
                }

                tarArchiveOutputStream.closeArchiveEntry();
            } else {
                tarArchiveOutputStream.closeArchiveEntry();
                File[] afile = file1.listFiles();
                if (afile != null) {
                    for (File file2 : afile) {
                        this.addFileToTarGz(tarArchiveOutputStream, file2.getAbsolutePath(), s + "/", false);
                    }
                }
            }
        }
    }
}
