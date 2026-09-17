package net.minecraft.client.gui.font.providers;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.CodepointMap;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.FastBufferedInputStream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class UnihexProvider implements GlyphProvider {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int GLYPH_HEIGHT = 16;
    private static final int DIGITS_PER_BYTE = 2;
    private static final int DIGITS_FOR_WIDTH_8 = 32;
    private static final int DIGITS_FOR_WIDTH_16 = 64;
    private static final int DIGITS_FOR_WIDTH_24 = 96;
    private static final int DIGITS_FOR_WIDTH_32 = 128;
    private final CodepointMap<UnihexProvider.Glyph> glyphs;

    UnihexProvider(CodepointMap<UnihexProvider.Glyph> glyph) {
        this.glyphs = glyph;
    }

    @Nullable
    @Override
    public GlyphInfo getGlyph(int character) {
        return this.glyphs.get(character);
    }

    @Override
    public IntSet getSupportedGlyphs() {
        return this.glyphs.keySet();
    }

    @VisibleForTesting
    static void unpackBitsToBytes(IntBuffer buffer, int lineData, int left, int right) {
        int i = 32 - left - 1;
        int j = 32 - right - 1;

        for (int k = i; k >= j; k--) {
            if (k < 32 && k >= 0) {
                boolean flag = (lineData >> k & 1) != 0;
                buffer.put(flag ? -1 : 0);
            } else {
                buffer.put(0);
            }
        }
    }

    static void unpackBitsToBytes(IntBuffer buffer, UnihexProvider.LineData lineData, int left, int right) {
        for (int i = 0; i < 16; i++) {
            int j = lineData.line(i);
            unpackBitsToBytes(buffer, j, left, right);
        }
    }

    @VisibleForTesting
    static void readFromStream(InputStream stream, UnihexProvider.ReaderOutput output) throws IOException {
        int i = 0;
        ByteList bytelist = new ByteArrayList(128);

        while (true) {
            boolean flag = copyUntil(stream, bytelist, 58);
            int j = bytelist.size();
            if (j == 0 && !flag) {
                return;
            }

            if (!flag || j != 4 && j != 5 && j != 6) {
                throw new IllegalArgumentException("Invalid entry at line " + i + ": expected 4, 5 or 6 hex digits followed by a colon");
            }

            int k = 0;

            for (int l = 0; l < j; l++) {
                k = k << 4 | decodeHex(i, bytelist.getByte(l));
            }

            bytelist.clear();
            copyUntil(stream, bytelist, 10);
            int i1 = bytelist.size();

            UnihexProvider.LineData unihexprovider$linedata = switch (i1) {
                case 32 -> UnihexProvider.ByteContents.read(i, bytelist);
                case 64 -> UnihexProvider.ShortContents.read(i, bytelist);
                case 96 -> UnihexProvider.IntContents.read24(i, bytelist);
                case 128 -> UnihexProvider.IntContents.read32(i, bytelist);
                default -> throw new IllegalArgumentException(
                "Invalid entry at line " + i + ": expected hex number describing (8,16,24,32) x 16 bitmap, followed by a new line"
            );
            };
            output.accept(k, unihexprovider$linedata);
            i++;
            bytelist.clear();
        }
    }

    static int decodeHex(int lineNumber, ByteList byteList, int index) {
        return decodeHex(lineNumber, byteList.getByte(index));
    }

    private static int decodeHex(int lineNumber, byte data) {
        return switch (data) {
            case 48 -> 0;
            case 49 -> 1;
            case 50 -> 2;
            case 51 -> 3;
            case 52 -> 4;
            case 53 -> 5;
            case 54 -> 6;
            case 55 -> 7;
            case 56 -> 8;
            case 57 -> 9;
            default -> throw new IllegalArgumentException("Invalid entry at line " + lineNumber + ": expected hex digit, got " + (char)data);
            case 65 -> 10;
            case 66 -> 11;
            case 67 -> 12;
            case 68 -> 13;
            case 69 -> 14;
            case 70 -> 15;
        };
    }

    private static boolean copyUntil(InputStream stream, ByteList byteList, int value) throws IOException {
        while (true) {
            int i = stream.read();
            if (i == -1) {
                return false;
            }

            if (i == value) {
                return true;
            }

            byteList.add((byte)i);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record ByteContents(byte[] contents) implements UnihexProvider.LineData {
        @Override
        public int line(int index) {
            return this.contents[index] << 24;
        }

        static UnihexProvider.LineData read(int index, ByteList byteList) {
            byte[] abyte = new byte[16];
            int i = 0;

            for (int j = 0; j < 16; j++) {
                int k = UnihexProvider.decodeHex(index, byteList, i++);
                int l = UnihexProvider.decodeHex(index, byteList, i++);
                byte b0 = (byte)(k << 4 | l);
                abyte[j] = b0;
            }

            return new UnihexProvider.ByteContents(abyte);
        }

        @Override
        public int bitWidth() {
            return 8;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Definition implements GlyphProviderDefinition {
        public static final MapCodec<UnihexProvider.Definition> CODEC = RecordCodecBuilder.mapCodec(
            p_286579_ -> p_286579_.group(
                        ResourceLocation.CODEC.fieldOf("hex_file").forGetter(p_286591_ -> p_286591_.hexFile),
                        UnihexProvider.OverrideRange.CODEC.listOf().fieldOf("size_overrides").forGetter(p_286528_ -> p_286528_.sizeOverrides)
                    )
                    .apply(p_286579_, UnihexProvider.Definition::new)
        );
        private final ResourceLocation hexFile;
        private final List<UnihexProvider.OverrideRange> sizeOverrides;

        private Definition(ResourceLocation hexFile, List<UnihexProvider.OverrideRange> sizeOverrides) {
            this.hexFile = hexFile;
            this.sizeOverrides = sizeOverrides;
        }

        @Override
        public GlyphProviderType type() {
            return GlyphProviderType.UNIHEX;
        }

        @Override
        public Either<GlyphProviderDefinition.Loader, GlyphProviderDefinition.Reference> unpack() {
            return Either.left(this::load);
        }

        private GlyphProvider load(ResourceManager resourceManager) throws IOException {
            UnihexProvider unihexprovider;
            try (InputStream inputstream = resourceManager.open(this.hexFile)) {
                unihexprovider = this.loadData(inputstream);
            }

            return unihexprovider;
        }

        private UnihexProvider loadData(InputStream inputStream) throws IOException {
            CodepointMap<UnihexProvider.LineData> codepointmap = new CodepointMap<>(UnihexProvider.LineData[]::new, UnihexProvider.LineData[][]::new);
            UnihexProvider.ReaderOutput unihexprovider$readeroutput = codepointmap::put;

            UnihexProvider unihexprovider;
            try (ZipInputStream zipinputstream = new ZipInputStream(inputStream)) {
                ZipEntry zipentry;
                while ((zipentry = zipinputstream.getNextEntry()) != null) {
                    String s = zipentry.getName();
                    if (s.endsWith(".hex")) {
                        UnihexProvider.LOGGER.info("Found {}, loading", s);
                        UnihexProvider.readFromStream(new FastBufferedInputStream(zipinputstream), unihexprovider$readeroutput);
                    }
                }

                CodepointMap<UnihexProvider.Glyph> codepointmap1 = new CodepointMap<>(UnihexProvider.Glyph[]::new, UnihexProvider.Glyph[][]::new);

                for (UnihexProvider.OverrideRange unihexprovider$overriderange : this.sizeOverrides) {
                    int i = unihexprovider$overriderange.from;
                    int j = unihexprovider$overriderange.to;
                    UnihexProvider.Dimensions unihexprovider$dimensions = unihexprovider$overriderange.dimensions;

                    for (int k = i; k <= j; k++) {
                        UnihexProvider.LineData unihexprovider$linedata = codepointmap.remove(k);
                        if (unihexprovider$linedata != null) {
                            codepointmap1.put(
                                k, new UnihexProvider.Glyph(unihexprovider$linedata, unihexprovider$dimensions.left, unihexprovider$dimensions.right)
                            );
                        }
                    }
                }

                codepointmap.forEach((p_286721_, p_286722_) -> {
                    int l = p_286722_.calculateWidth();
                    int i1 = UnihexProvider.Dimensions.left(l);
                    int j1 = UnihexProvider.Dimensions.right(l);
                    codepointmap1.put(p_286721_, new UnihexProvider.Glyph(p_286722_, i1, j1));
                });
                unihexprovider = new UnihexProvider(codepointmap1);
            }

            return unihexprovider;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static record Dimensions(int left, int right) {
        public static final MapCodec<UnihexProvider.Dimensions> MAP_CODEC = RecordCodecBuilder.mapCodec(
            p_285497_ -> p_285497_.group(
                        Codec.INT.fieldOf("left").forGetter(UnihexProvider.Dimensions::left),
                        Codec.INT.fieldOf("right").forGetter(UnihexProvider.Dimensions::right)
                    )
                    .apply(p_285497_, UnihexProvider.Dimensions::new)
        );
        public static final Codec<UnihexProvider.Dimensions> CODEC = MAP_CODEC.codec();

        public int pack() {
            return pack(this.left, this.right);
        }

        public static int pack(int left, int right) {
            return (left & 0xFF) << 8 | right & 0xFF;
        }

        public static int left(int packedDimensions) {
            return (byte)(packedDimensions >> 8);
        }

        public static int right(int packedDimensions) {
            return (byte)packedDimensions;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record Glyph(UnihexProvider.LineData contents, int left, int right) implements GlyphInfo {
        public int width() {
            return this.right - this.left + 1;
        }

        @Override
        public float getAdvance() {
            return (float)(this.width() / 2 + 1);
        }

        @Override
        public float getShadowOffset() {
            return 0.5F;
        }

        @Override
        public float getBoldOffset() {
            return 0.5F;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> p_285377_) {
            return p_285377_.apply(new SheetGlyphInfo() {
                @Override
                public float getOversample() {
                    return 2.0F;
                }

                @Override
                public int getPixelWidth() {
                    return Glyph.this.width();
                }

                @Override
                public int getPixelHeight() {
                    return 16;
                }

                @Override
                public void upload(int p_285473_, int p_285510_) {
                    IntBuffer intbuffer = MemoryUtil.memAllocInt(Glyph.this.width() * 16);
                    UnihexProvider.unpackBitsToBytes(intbuffer, Glyph.this.contents, Glyph.this.left, Glyph.this.right);
                    intbuffer.rewind();
                    GlStateManager.upload(0, p_285473_, p_285510_, Glyph.this.width(), 16, NativeImage.Format.RGBA, intbuffer, MemoryUtil::memFree);
                }

                @Override
                public boolean isColored() {
                    return true;
                }
            });
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record IntContents(int[] contents, int bitWidth) implements UnihexProvider.LineData {
        private static final int SIZE_24 = 24;

        @Override
        public int line(int index) {
            return this.contents[index];
        }

        static UnihexProvider.LineData read24(int index, ByteList byteList) {
            int[] aint = new int[16];
            int i = 0;
            int j = 0;

            for (int k = 0; k < 16; k++) {
                int l = UnihexProvider.decodeHex(index, byteList, j++);
                int i1 = UnihexProvider.decodeHex(index, byteList, j++);
                int j1 = UnihexProvider.decodeHex(index, byteList, j++);
                int k1 = UnihexProvider.decodeHex(index, byteList, j++);
                int l1 = UnihexProvider.decodeHex(index, byteList, j++);
                int i2 = UnihexProvider.decodeHex(index, byteList, j++);
                int j2 = l << 20 | i1 << 16 | j1 << 12 | k1 << 8 | l1 << 4 | i2;
                aint[k] = j2 << 8;
                i |= j2;
            }

            return new UnihexProvider.IntContents(aint, 24);
        }

        public static UnihexProvider.LineData read32(int index, ByteList byteList) {
            int[] aint = new int[16];
            int i = 0;
            int j = 0;

            for (int k = 0; k < 16; k++) {
                int l = UnihexProvider.decodeHex(index, byteList, j++);
                int i1 = UnihexProvider.decodeHex(index, byteList, j++);
                int j1 = UnihexProvider.decodeHex(index, byteList, j++);
                int k1 = UnihexProvider.decodeHex(index, byteList, j++);
                int l1 = UnihexProvider.decodeHex(index, byteList, j++);
                int i2 = UnihexProvider.decodeHex(index, byteList, j++);
                int j2 = UnihexProvider.decodeHex(index, byteList, j++);
                int k2 = UnihexProvider.decodeHex(index, byteList, j++);
                int l2 = l << 28 | i1 << 24 | j1 << 20 | k1 << 16 | l1 << 12 | i2 << 8 | j2 << 4 | k2;
                aint[k] = l2;
                i |= l2;
            }

            return new UnihexProvider.IntContents(aint, 32);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public interface LineData {
        int line(int index);

        int bitWidth();

        default int mask() {
            int i = 0;

            for (int j = 0; j < 16; j++) {
                i |= this.line(j);
            }

            return i;
        }

        default int calculateWidth() {
            int i = this.mask();
            int j = this.bitWidth();
            int k;
            int l;
            if (i == 0) {
                k = 0;
                l = j;
            } else {
                k = Integer.numberOfLeadingZeros(i);
                l = 32 - Integer.numberOfTrailingZeros(i) - 1;
            }

            return UnihexProvider.Dimensions.pack(k, l);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static record OverrideRange(int from, int to, UnihexProvider.Dimensions dimensions) {
        private static final Codec<UnihexProvider.OverrideRange> RAW_CODEC = RecordCodecBuilder.create(
            p_285088_ -> p_285088_.group(
                        ExtraCodecs.CODEPOINT.fieldOf("from").forGetter(UnihexProvider.OverrideRange::from),
                        ExtraCodecs.CODEPOINT.fieldOf("to").forGetter(UnihexProvider.OverrideRange::to),
                        UnihexProvider.Dimensions.MAP_CODEC.forGetter(UnihexProvider.OverrideRange::dimensions)
                    )
                    .apply(p_285088_, UnihexProvider.OverrideRange::new)
        );
        public static final Codec<UnihexProvider.OverrideRange> CODEC = RAW_CODEC.validate(
            p_285215_ -> p_285215_.from >= p_285215_.to
                    ? DataResult.error(() -> "Invalid range: [" + p_285215_.from + ";" + p_285215_.to + "]")
                    : DataResult.success(p_285215_)
        );
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface ReaderOutput {
        void accept(int p_285139_, UnihexProvider.LineData p_284982_);
    }

    @OnlyIn(Dist.CLIENT)
    static record ShortContents(short[] contents) implements UnihexProvider.LineData {
        @Override
        public int line(int index) {
            return this.contents[index] << 16;
        }

        static UnihexProvider.LineData read(int index, ByteList byteList) {
            short[] ashort = new short[16];
            int i = 0;

            for (int j = 0; j < 16; j++) {
                int k = UnihexProvider.decodeHex(index, byteList, i++);
                int l = UnihexProvider.decodeHex(index, byteList, i++);
                int i1 = UnihexProvider.decodeHex(index, byteList, i++);
                int j1 = UnihexProvider.decodeHex(index, byteList, i++);
                short short1 = (short)(k << 12 | l << 8 | i1 << 4 | j1);
                ashort[j] = short1;
            }

            return new UnihexProvider.ShortContents(ashort);
        }

        @Override
        public int bitWidth() {
            return 16;
        }
    }
}
