package net.minecraft.client.gui.font;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FontSet implements AutoCloseable {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float LARGE_FORWARD_ADVANCE = 32.0F;
    private final TextureManager textureManager;
    private final ResourceLocation name;
    private BakedGlyph missingGlyph;
    private BakedGlyph whiteGlyph;
    private List<GlyphProvider.Conditional> allProviders = List.of();
    private List<GlyphProvider> activeProviders = List.of();
    private final CodepointMap<BakedGlyph> glyphs = new CodepointMap<>(BakedGlyph[]::new, BakedGlyph[][]::new);
    private final CodepointMap<FontSet.GlyphInfoFilter> glyphInfos = new CodepointMap<>(FontSet.GlyphInfoFilter[]::new, FontSet.GlyphInfoFilter[][]::new);
    private final Int2ObjectMap<IntList> glyphsByWidth = new Int2ObjectOpenHashMap<>();
    private final List<FontTexture> textures = Lists.newArrayList();

    public FontSet(TextureManager textureManager, ResourceLocation name) {
        this.textureManager = textureManager;
        this.name = name;
    }

    public void reload(List<GlyphProvider.Conditional> allProviders, Set<FontOption> options) {
        this.allProviders = allProviders;
        this.reload(options);
    }

    public void reload(Set<FontOption> options) {
        this.activeProviders = List.of();
        this.resetTextures();
        this.activeProviders = this.selectProviders(this.allProviders, options);
    }

    private void resetTextures() {
        this.closeTextures();
        this.glyphs.clear();
        this.glyphInfos.clear();
        this.glyphsByWidth.clear();
        this.missingGlyph = SpecialGlyphs.MISSING.bake(this::stitch);
        this.whiteGlyph = SpecialGlyphs.WHITE.bake(this::stitch);
    }

    private List<GlyphProvider> selectProviders(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
        IntSet intset = new IntOpenHashSet();
        List<GlyphProvider> list = new ArrayList<>();

        for (GlyphProvider.Conditional glyphprovider$conditional : providers) {
            if (glyphprovider$conditional.filter().apply(options)) {
                list.add(glyphprovider$conditional.provider());
                intset.addAll(glyphprovider$conditional.provider().getSupportedGlyphs());
            }
        }

        Set<GlyphProvider> set = Sets.newHashSet();
        intset.forEach(p_232561_ -> {
            for (GlyphProvider glyphprovider : list) {
                GlyphInfo glyphinfo = glyphprovider.getGlyph(p_232561_);
                if (glyphinfo != null) {
                    set.add(glyphprovider);
                    if (glyphinfo != SpecialGlyphs.MISSING) {
                        this.glyphsByWidth.computeIfAbsent(Mth.ceil(glyphinfo.getAdvance(false)), p_232567_ -> new IntArrayList()).add(p_232561_);
                    }
                    break;
                }
            }
        });
        return list.stream().filter(set::contains).toList();
    }

    @Override
    public void close() {
        this.closeTextures();
    }

    private void closeTextures() {
        for (FontTexture fonttexture : this.textures) {
            fonttexture.close();
        }

        this.textures.clear();
    }

    private static boolean hasFishyAdvance(GlyphInfo glyph) {
        float f = glyph.getAdvance(false);
        if (!(f < 0.0F) && !(f > 32.0F)) {
            float f1 = glyph.getAdvance(true);
            return f1 < 0.0F || f1 > 32.0F;
        } else {
            return true;
        }
    }

    private FontSet.GlyphInfoFilter computeGlyphInfo(int character) {
        GlyphInfo glyphinfo = null;

        for (GlyphProvider glyphprovider : this.activeProviders) {
            GlyphInfo glyphinfo1 = glyphprovider.getGlyph(character);
            if (glyphinfo1 != null) {
                if (glyphinfo == null) {
                    glyphinfo = glyphinfo1;
                }

                if (!hasFishyAdvance(glyphinfo1)) {
                    return new FontSet.GlyphInfoFilter(glyphinfo, glyphinfo1);
                }
            }
        }

        return glyphinfo != null ? new FontSet.GlyphInfoFilter(glyphinfo, SpecialGlyphs.MISSING) : FontSet.GlyphInfoFilter.MISSING;
    }

    public GlyphInfo getGlyphInfo(int character, boolean filterFishyGlyphs) {
        return this.glyphInfos.computeIfAbsent(character, this::computeGlyphInfo).select(filterFishyGlyphs);
    }

    private BakedGlyph computeBakedGlyph(int character) {
        for (GlyphProvider glyphprovider : this.activeProviders) {
            GlyphInfo glyphinfo = glyphprovider.getGlyph(character);
            if (glyphinfo != null) {
                return glyphinfo.bake(this::stitch);
            }
        }

        return this.missingGlyph;
    }

    public BakedGlyph getGlyph(int character) {
        return this.glyphs.computeIfAbsent(character, this::computeBakedGlyph);
    }

    private BakedGlyph stitch(SheetGlyphInfo glyphInfo) {
        for (FontTexture fonttexture : this.textures) {
            BakedGlyph bakedglyph = fonttexture.add(glyphInfo);
            if (bakedglyph != null) {
                return bakedglyph;
            }
        }

        ResourceLocation resourcelocation = this.name.withSuffix("/" + this.textures.size());
        boolean flag = glyphInfo.isColored();
        GlyphRenderTypes glyphrendertypes = flag
            ? GlyphRenderTypes.createForColorTexture(resourcelocation)
            : GlyphRenderTypes.createForIntensityTexture(resourcelocation);
        FontTexture fonttexture1 = new FontTexture(glyphrendertypes, flag);
        this.textures.add(fonttexture1);
        this.textureManager.register(resourcelocation, fonttexture1);
        BakedGlyph bakedglyph1 = fonttexture1.add(glyphInfo);
        return bakedglyph1 == null ? this.missingGlyph : bakedglyph1;
    }

    public BakedGlyph getRandomGlyph(GlyphInfo glyph) {
        IntList intlist = this.glyphsByWidth.get(Mth.ceil(glyph.getAdvance(false)));
        return intlist != null && !intlist.isEmpty() ? this.getGlyph(intlist.getInt(RANDOM.nextInt(intlist.size()))) : this.missingGlyph;
    }

    public ResourceLocation name() {
        return this.name;
    }

    public BakedGlyph whiteGlyph() {
        return this.whiteGlyph;
    }

    @OnlyIn(Dist.CLIENT)
    static record GlyphInfoFilter(GlyphInfo glyphInfo, GlyphInfo glyphInfoNotFishy) {
        static final FontSet.GlyphInfoFilter MISSING = new FontSet.GlyphInfoFilter(SpecialGlyphs.MISSING, SpecialGlyphs.MISSING);

        GlyphInfo select(boolean filterFishyGlyphs) {
            return filterFishyGlyphs ? this.glyphInfoNotFishy : this.glyphInfo;
        }
    }
}
