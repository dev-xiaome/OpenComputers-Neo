package net.minecraft.client.gui.screens;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class WinScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/credits_vignette.png");
    private static final Component SECTION_HEADING = Component.literal("============").withStyle(ChatFormatting.WHITE);
    private static final String NAME_PREFIX = "           ";
    private static final String OBFUSCATE_TOKEN = "" + ChatFormatting.WHITE + ChatFormatting.OBFUSCATED + ChatFormatting.GREEN + ChatFormatting.AQUA;
    private static final float SPEEDUP_FACTOR = 5.0F;
    private static final float SPEEDUP_FACTOR_FAST = 15.0F;
    private static final ResourceLocation END_POEM_LOCATION = ResourceLocation.withDefaultNamespace("texts/end.txt");
    private static final ResourceLocation CREDITS_LOCATION = ResourceLocation.withDefaultNamespace("texts/credits.json");
    private static final ResourceLocation POSTCREDITS_LOCATION = ResourceLocation.withDefaultNamespace("texts/postcredits.txt");
    private final boolean poem;
    private final Runnable onFinished;
    private float scroll;
    private List<FormattedCharSequence> lines;
    private IntSet centeredLines;
    private int totalScrollLength;
    private boolean speedupActive;
    private final IntSet speedupModifiers = new IntOpenHashSet();
    private float scrollSpeed;
    private final float unmodifiedScrollSpeed;
    private int direction;
    private final LogoRenderer logoRenderer = new LogoRenderer(false);

    public WinScreen(boolean poem, Runnable onFinished) {
        super(GameNarrator.NO_TITLE);
        this.poem = poem;
        this.onFinished = onFinished;
        if (!poem) {
            this.unmodifiedScrollSpeed = 0.75F;
        } else {
            this.unmodifiedScrollSpeed = 0.5F;
        }

        this.direction = 1;
        this.scrollSpeed = this.unmodifiedScrollSpeed;
    }

    private float calculateScrollSpeed() {
        return this.speedupActive
            ? this.unmodifiedScrollSpeed * (5.0F + (float)this.speedupModifiers.size() * 15.0F) * (float)this.direction
            : this.unmodifiedScrollSpeed * (float)this.direction;
    }

    @Override
    public void tick() {
        this.minecraft.getMusicManager().tick();
        this.minecraft.getSoundManager().tick(false);
        float f = (float)(this.totalScrollLength + this.height + this.height + 24);
        if (this.scroll > f) {
            this.respawn();
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
        if (keyCode == 265) {
            this.direction = -1;
        } else if (keyCode == 341 || keyCode == 345) {
            this.speedupModifiers.add(keyCode);
        } else if (keyCode == 32) {
            this.speedupActive = true;
        }

        this.scrollSpeed = this.calculateScrollSpeed();
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (keyCode == 265) {
            this.direction = 1;
        }

        if (keyCode == 32) {
            this.speedupActive = false;
        } else if (keyCode == 341 || keyCode == 345) {
            this.speedupModifiers.remove(keyCode);
        }

        this.scrollSpeed = this.calculateScrollSpeed();
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.respawn();
    }

    private void respawn() {
        this.onFinished.run();
    }

    @Override
    protected void init() {
        if (this.lines == null) {
            this.lines = Lists.newArrayList();
            this.centeredLines = new IntOpenHashSet();
            if (this.poem) {
                this.wrapCreditsIO(END_POEM_LOCATION, this::addPoemFile);
            }

            this.wrapCreditsIO(CREDITS_LOCATION, this::addCreditsFile);
            if (this.poem) {
                this.wrapCreditsIO(POSTCREDITS_LOCATION, this::addPoemFile);
            }

            this.totalScrollLength = this.lines.size() * 12;
        }
    }

    private void wrapCreditsIO(ResourceLocation location, WinScreen.CreditsReader p_reader) {
        try (Reader reader = this.minecraft.getResourceManager().openAsReader(location)) {
            p_reader.read(reader);
        } catch (Exception exception) {
            LOGGER.error("Couldn't load credits from file {}", location, exception);
        }
    }

    private void addPoemFile(Reader reader) throws IOException {
        BufferedReader bufferedreader = new BufferedReader(reader);
        RandomSource randomsource = RandomSource.create(8124371L);

        String s;
        while ((s = bufferedreader.readLine()) != null) {
            s = s.replaceAll("PLAYERNAME", this.minecraft.getUser().getName());

            int i;
            while ((i = s.indexOf(OBFUSCATE_TOKEN)) != -1) {
                String s1 = s.substring(0, i);
                String s2 = s.substring(i + OBFUSCATE_TOKEN.length());
                s = s1 + ChatFormatting.WHITE + ChatFormatting.OBFUSCATED + "XXXXXXXX".substring(0, randomsource.nextInt(4) + 3) + s2;
            }

            this.addPoemLines(s);
            this.addEmptyLine();
        }

        for (int j = 0; j < 8; j++) {
            this.addEmptyLine();
        }
    }

    private void addCreditsFile(Reader reader) {
        for (JsonElement jsonelement : GsonHelper.parseArray(reader)) {
            JsonObject jsonobject = jsonelement.getAsJsonObject();
            String s = jsonobject.get("section").getAsString();
            this.addCreditsLine(SECTION_HEADING, true);
            this.addCreditsLine(Component.literal(s).withStyle(ChatFormatting.YELLOW), true);
            this.addCreditsLine(SECTION_HEADING, true);
            this.addEmptyLine();
            this.addEmptyLine();

            for (JsonElement jsonelement1 : jsonobject.getAsJsonArray("disciplines")) {
                JsonObject jsonobject1 = jsonelement1.getAsJsonObject();
                String s1 = jsonobject1.get("discipline").getAsString();
                if (StringUtils.isNotEmpty(s1)) {
                    this.addCreditsLine(Component.literal(s1).withStyle(ChatFormatting.YELLOW), true);
                    this.addEmptyLine();
                    this.addEmptyLine();
                }

                for (JsonElement jsonelement2 : jsonobject1.getAsJsonArray("titles")) {
                    JsonObject jsonobject2 = jsonelement2.getAsJsonObject();
                    String s2 = jsonobject2.get("title").getAsString();
                    JsonArray jsonarray = jsonobject2.getAsJsonArray("names");
                    this.addCreditsLine(Component.literal(s2).withStyle(ChatFormatting.GRAY), false);

                    for (JsonElement jsonelement3 : jsonarray) {
                        String s3 = jsonelement3.getAsString();
                        this.addCreditsLine(Component.literal("           ").append(s3).withStyle(ChatFormatting.WHITE), false);
                    }

                    this.addEmptyLine();
                    this.addEmptyLine();
                }
            }
        }
    }

    private void addEmptyLine() {
        this.lines.add(FormattedCharSequence.EMPTY);
    }

    private void addPoemLines(String text) {
        this.lines.addAll(this.minecraft.font.split(Component.literal(text), 256));
    }

    private void addCreditsLine(Component creditsLine, boolean centered) {
        if (centered) {
            this.centeredLines.add(this.lines.size());
        }

        this.lines.add(creditsLine.getVisualOrderText());
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
        this.renderVignette(guiGraphics);
        this.scroll = Math.max(0.0F, this.scroll + partialTick * this.scrollSpeed);
        int i = this.width / 2 - 128;
        int j = this.height + 50;
        float f = -this.scroll;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, f, 0.0F);
        this.logoRenderer.renderLogo(guiGraphics, this.width, 1.0F, j);
        int k = j + 100;

        for (int l = 0; l < this.lines.size(); l++) {
            if (l == this.lines.size() - 1) {
                float f1 = (float)k + f - (float)(this.height / 2 - 6);
                if (f1 < 0.0F) {
                    guiGraphics.pose().translate(0.0F, -f1, 0.0F);
                }
            }

            if ((float)k + f + 12.0F + 8.0F > 0.0F && (float)k + f < (float)this.height) {
                FormattedCharSequence formattedcharsequence = this.lines.get(l);
                if (this.centeredLines.contains(l)) {
                    guiGraphics.drawCenteredString(this.font, formattedcharsequence, i + 128, k, -1);
                } else {
                    guiGraphics.drawString(this.font, formattedcharsequence, i, k, -1);
                }
            }

            k += 12;
        }

        guiGraphics.pose().popPose();
    }

    private void renderVignette(GuiGraphics guiGraphics) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        guiGraphics.blit(VIGNETTE_LOCATION, 0, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.poem) {
            guiGraphics.fillRenderType(RenderType.endPortal(), 0, 0, this.width, this.height, 0);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderMenuBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        float f = this.scroll * 0.5F;
        Screen.renderMenuBackgroundTexture(guiGraphics, Screen.MENU_BACKGROUND, 0, 0, 0.0F, f, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return !this.poem;
    }

    @Override
    public void removed() {
        this.minecraft.getMusicManager().stopPlaying(Musics.CREDITS);
    }

    @Override
    public Music getBackgroundMusic() {
        return Musics.CREDITS;
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    interface CreditsReader {
        void read(Reader reader) throws IOException;
    }
}
