package net.minecraft.client.renderer.texture;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class SpriteContents implements Stitcher.Entry, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ResourceLocation name;
    final int width;
    final int height;
    private final NativeImage originalImage;
    public NativeImage[] byMipLevel;
    @Nullable
    final SpriteContents.AnimatedTexture animatedTexture;
    private final ResourceMetadata metadata;

    public SpriteContents(ResourceLocation name, FrameSize frameSize, NativeImage originalImage, ResourceMetadata metadata) {
        this.name = name;
        this.width = frameSize.width();
        this.height = frameSize.height();
        this.metadata = metadata;
        AnimationMetadataSection animationmetadatasection = metadata.getSection(AnimationMetadataSection.SERIALIZER).orElse(AnimationMetadataSection.EMPTY);
        this.animatedTexture = this.createAnimatedTexture(frameSize, originalImage.getWidth(), originalImage.getHeight(), animationmetadatasection);
        this.originalImage = originalImage;
        this.byMipLevel = new NativeImage[]{this.originalImage};
    }

    public NativeImage getOriginalImage() {
        return this.originalImage;
    }

    public void increaseMipLevel(int mipLevel) {
        try {
            this.byMipLevel = MipmapGenerator.generateMipLevels(this.byMipLevel, mipLevel);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Generating mipmaps for frame");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Sprite being mipmapped");
            crashreportcategory.setDetail("First frame", () -> {
                StringBuilder stringbuilder = new StringBuilder();
                if (stringbuilder.length() > 0) {
                    stringbuilder.append(", ");
                }

                stringbuilder.append(this.originalImage.getWidth()).append("x").append(this.originalImage.getHeight());
                return stringbuilder.toString();
            });
            CrashReportCategory crashreportcategory1 = crashreport.addCategory("Frame being iterated");
            crashreportcategory1.setDetail("Sprite name", this.name);
            crashreportcategory1.setDetail("Sprite size", () -> this.width + " x " + this.height);
            crashreportcategory1.setDetail("Sprite frames", () -> this.getFrameCount() + " frames");
            crashreportcategory1.setDetail("Mipmap levels", mipLevel);
            throw new ReportedException(crashreport);
        }
    }

    private int getFrameCount() {
        return this.animatedTexture != null ? this.animatedTexture.frames.size() : 1;
    }

    @Nullable
    private SpriteContents.AnimatedTexture createAnimatedTexture(FrameSize frameSize, int width, int height, AnimationMetadataSection metadata) {
        int i = width / frameSize.width();
        int j = height / frameSize.height();
        int k = i * j;
        List<SpriteContents.FrameInfo> list = new ArrayList<>();
        metadata.forEachFrame((p_251291_, p_251837_) -> list.add(new SpriteContents.FrameInfo(p_251291_, p_251837_)));
        if (list.isEmpty()) {
            for (int l = 0; l < k; l++) {
                list.add(new SpriteContents.FrameInfo(l, metadata.getDefaultFrameTime()));
            }
        } else {
            int i1 = 0;
            IntSet intset = new IntOpenHashSet();

            for (Iterator<SpriteContents.FrameInfo> iterator = list.iterator(); iterator.hasNext(); i1++) {
                SpriteContents.FrameInfo spritecontents$frameinfo = iterator.next();
                boolean flag = true;
                if (spritecontents$frameinfo.time <= 0) {
                    LOGGER.warn("Invalid frame duration on sprite {} frame {}: {}", this.name, i1, spritecontents$frameinfo.time);
                    flag = false;
                }

                if (spritecontents$frameinfo.index < 0 || spritecontents$frameinfo.index >= k) {
                    LOGGER.warn("Invalid frame index on sprite {} frame {}: {}", this.name, i1, spritecontents$frameinfo.index);
                    flag = false;
                }

                if (flag) {
                    intset.add(spritecontents$frameinfo.index);
                } else {
                    iterator.remove();
                }
            }

            int[] aint = IntStream.range(0, k).filter(p_251185_ -> !intset.contains(p_251185_)).toArray();
            if (aint.length > 0) {
                LOGGER.warn("Unused frames in sprite {}: {}", this.name, Arrays.toString(aint));
            }
        }

        return list.size() <= 1 ? null : new SpriteContents.AnimatedTexture(ImmutableList.copyOf(list), i, metadata.isInterpolatedFrames());
    }

    void upload(int x, int y, int frameX, int frameY, NativeImage[] atlasData) {
        for (int i = 0; i < this.byMipLevel.length; i++) {
            // Forge: Skip uploading if the texture would be made invalid by mip level
            if ((this.width >> i) <= 0 || (this.height >> i) <= 0)
                break;

            atlasData[i]
                .upload(i, x >> i, y >> i, frameX >> i, frameY >> i, this.width >> i, this.height >> i, this.byMipLevel.length > 1, false);
        }
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public ResourceLocation name() {
        return this.name;
    }

    public IntStream getUniqueFrames() {
        return this.animatedTexture != null ? this.animatedTexture.getUniqueFrames() : IntStream.of(1);
    }

    @Nullable
    public SpriteTicker createTicker() {
        return this.animatedTexture != null ? this.animatedTexture.createTicker() : null;
    }

    public ResourceMetadata metadata() {
        return this.metadata;
    }

    @Override
    public void close() {
        for (NativeImage nativeimage : this.byMipLevel) {
            nativeimage.close();
        }
    }

    @Override
    public String toString() {
        return "SpriteContents{name=" + this.name + ", frameCount=" + this.getFrameCount() + ", height=" + this.height + ", width=" + this.width + "}";
    }

    public boolean isTransparent(int frame, int x, int y) {
        int i = x;
        int j = y;
        if (this.animatedTexture != null) {
            i = x + this.animatedTexture.getFrameX(frame) * this.width;
            j = y + this.animatedTexture.getFrameY(frame) * this.height;
        }

        return (this.originalImage.getPixelRGBA(i, j) >> 24 & 0xFF) == 0;
    }

    public void uploadFirstFrame(int x, int y) {
        if (this.animatedTexture != null) {
            this.animatedTexture.uploadFirstFrame(x, y);
        } else {
            this.upload(x, y, 0, 0, this.byMipLevel);
        }
    }

    @OnlyIn(Dist.CLIENT)
    class AnimatedTexture {
        final List<SpriteContents.FrameInfo> frames;
        private final int frameRowSize;
        private final boolean interpolateFrames;

        AnimatedTexture(List<SpriteContents.FrameInfo> frames, int frameRowSize, boolean interpolateFrames) {
            this.frames = frames;
            this.frameRowSize = frameRowSize;
            this.interpolateFrames = interpolateFrames;
        }

        int getFrameX(int frameIndex) {
            return frameIndex % this.frameRowSize;
        }

        int getFrameY(int frameIndex) {
            return frameIndex / this.frameRowSize;
        }

        void uploadFrame(int x, int y, int frameIndex) {
            int i = this.getFrameX(frameIndex) * SpriteContents.this.width;
            int j = this.getFrameY(frameIndex) * SpriteContents.this.height;
            SpriteContents.this.upload(x, y, i, j, SpriteContents.this.byMipLevel);
        }

        public SpriteTicker createTicker() {
            return SpriteContents.this.new Ticker(this, this.interpolateFrames ? SpriteContents.this.new InterpolationData() : null);
        }

        public void uploadFirstFrame(int x, int y) {
            this.uploadFrame(x, y, this.frames.get(0).index);
        }

        public IntStream getUniqueFrames() {
            return this.frames.stream().mapToInt(p_249981_ -> p_249981_.index).distinct();
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class FrameInfo {
        final int index;
        final int time;

        FrameInfo(int index, int time) {
            this.index = index;
            this.time = time;
        }
    }

    @OnlyIn(Dist.CLIENT)
    final class InterpolationData implements AutoCloseable {
        private final NativeImage[] activeFrame = new NativeImage[SpriteContents.this.byMipLevel.length];

        InterpolationData() {
            for (int i = 0; i < this.activeFrame.length; i++) {
                int j = SpriteContents.this.width >> i;
                int k = SpriteContents.this.height >> i;
                // Forge: Guard against invalid texture size, because we allow generating mipmaps regardless of texture sizes
                this.activeFrame[i] = new NativeImage(Math.max(1, j), Math.max(1, k), false);
            }
        }

        void uploadInterpolatedFrame(int x, int y, SpriteContents.Ticker ticker) {
            SpriteContents.AnimatedTexture spritecontents$animatedtexture = ticker.animationInfo;
            List<SpriteContents.FrameInfo> list = spritecontents$animatedtexture.frames;
            SpriteContents.FrameInfo spritecontents$frameinfo = list.get(ticker.frame);
            double d0 = 1.0 - (double)ticker.subFrame / (double)spritecontents$frameinfo.time;
            int i = spritecontents$frameinfo.index;
            int j = list.get((ticker.frame + 1) % list.size()).index;
            if (i != j) {
                for (int k = 0; k < this.activeFrame.length; k++) {
                    int l = SpriteContents.this.width >> k;
                    int i1 = SpriteContents.this.height >> k;
                    // Forge: Guard against invalid texture size, because we allow generating mipmaps regardless of texture sizes
                    if (l < 1 || i1 < 1)
                        continue;

                    for (int j1 = 0; j1 < i1; j1++) {
                        for (int k1 = 0; k1 < l; k1++) {
                            int l1 = this.getPixel(spritecontents$animatedtexture, i, k, k1, j1);
                            int i2 = this.getPixel(spritecontents$animatedtexture, j, k, k1, j1);
                            int j2 = this.mix(d0, l1 >> 16 & 0xFF, i2 >> 16 & 0xFF);
                            int k2 = this.mix(d0, l1 >> 8 & 0xFF, i2 >> 8 & 0xFF);
                            int l2 = this.mix(d0, l1 & 0xFF, i2 & 0xFF);
                            this.activeFrame[k].setPixelRGBA(k1, j1, l1 & 0xFF000000 | j2 << 16 | k2 << 8 | l2);
                        }
                    }
                }

                SpriteContents.this.upload(x, y, 0, 0, this.activeFrame);
            }
        }

        private int getPixel(SpriteContents.AnimatedTexture animatedTexture, int frameIndex, int mipLevel, int x, int y) {
            return SpriteContents.this.byMipLevel[mipLevel]
                .getPixelRGBA(
                    x + (animatedTexture.getFrameX(frameIndex) * SpriteContents.this.width >> mipLevel),
                    y + (animatedTexture.getFrameY(frameIndex) * SpriteContents.this.height >> mipLevel)
                );
        }

        private int mix(double delta, int color1, int color2) {
            return (int)(delta * (double)color1 + (1.0 - delta) * (double)color2);
        }

        @Override
        public void close() {
            for (NativeImage nativeimage : this.activeFrame) {
                nativeimage.close();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    class Ticker implements SpriteTicker {
        int frame;
        int subFrame;
        final SpriteContents.AnimatedTexture animationInfo;
        @Nullable
        private final SpriteContents.InterpolationData interpolationData;

        Ticker(SpriteContents.AnimatedTexture animationInfo, @Nullable SpriteContents.InterpolationData interpolationData) {
            this.animationInfo = animationInfo;
            this.interpolationData = interpolationData;
        }

        @Override
        public void tickAndUpload(int x, int y) {
            this.subFrame++;
            SpriteContents.FrameInfo spritecontents$frameinfo = this.animationInfo.frames.get(this.frame);
            if (this.subFrame >= spritecontents$frameinfo.time) {
                int i = spritecontents$frameinfo.index;
                this.frame = (this.frame + 1) % this.animationInfo.frames.size();
                this.subFrame = 0;
                int j = this.animationInfo.frames.get(this.frame).index;
                if (i != j) {
                    this.animationInfo.uploadFrame(x, y, j);
                }
            } else if (this.interpolationData != null) {
                if (!RenderSystem.isOnRenderThread()) {
                    RenderSystem.recordRenderCall(() -> this.interpolationData.uploadInterpolatedFrame(x, y, this));
                } else {
                    this.interpolationData.uploadInterpolatedFrame(x, y, this);
                }
            }
        }

        @Override
        public void close() {
            if (this.interpolationData != null) {
                this.interpolationData.close();
            }
        }
    }
}
