package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MipmapGenerator {
    private static final int ALPHA_CUTOUT_CUTOFF = 96;
    private static final float[] POW22 = Util.make(new float[256], p_118058_ -> {
        for (int i = 0; i < p_118058_.length; i++) {
            p_118058_[i] = (float)Math.pow((double)((float)i / 255.0F), 2.2);
        }
    });

    private MipmapGenerator() {
    }

    public static NativeImage[] generateMipLevels(NativeImage[] images, int mipLevel) {
        if (mipLevel + 1 <= images.length) {
            return images;
        } else {
            NativeImage[] anativeimage = new NativeImage[mipLevel + 1];
            anativeimage[0] = images[0];
            boolean flag = hasTransparentPixel(anativeimage[0]);

            int maxMipmapLevel = net.neoforged.neoforge.client.ClientHooks.getMaxMipmapLevel(anativeimage[0].getWidth(), anativeimage[0].getHeight());
            for (int i = 1; i <= mipLevel; i++) {
                if (i < images.length) {
                    anativeimage[i] = images[i];
                } else {
                    NativeImage nativeimage = anativeimage[i - 1];
                    // Forge: Guard against invalid texture size, because we allow generating mipmaps regardless of texture sizes
                    NativeImage nativeimage1 = new NativeImage(Math.max(1, nativeimage.getWidth() >> 1), Math.max(1, nativeimage.getHeight() >> 1), false);
                    if (i <= maxMipmapLevel) {
                    int j = nativeimage1.getWidth();
                    int k = nativeimage1.getHeight();

                    for (int l = 0; l < j; l++) {
                        for (int i1 = 0; i1 < k; i1++) {
                            nativeimage1.setPixelRGBA(
                                l,
                                i1,
                                alphaBlend(
                                    nativeimage.getPixelRGBA(l * 2 + 0, i1 * 2 + 0),
                                    nativeimage.getPixelRGBA(l * 2 + 1, i1 * 2 + 0),
                                    nativeimage.getPixelRGBA(l * 2 + 0, i1 * 2 + 1),
                                    nativeimage.getPixelRGBA(l * 2 + 1, i1 * 2 + 1),
                                    flag
                                )
                            );
                        }
                    }
                    }

                    anativeimage[i] = nativeimage1;
                }
            }

            return anativeimage;
        }
    }

    private static boolean hasTransparentPixel(NativeImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                if (image.getPixelRGBA(i, j) >> 24 == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int alphaBlend(int col0, int col1, int col2, int col3, boolean transparent) {
        if (transparent) {
            float f = 0.0F;
            float f1 = 0.0F;
            float f2 = 0.0F;
            float f3 = 0.0F;
            if (col0 >> 24 != 0) {
                f += getPow22(col0 >> 24);
                f1 += getPow22(col0 >> 16);
                f2 += getPow22(col0 >> 8);
                f3 += getPow22(col0 >> 0);
            }

            if (col1 >> 24 != 0) {
                f += getPow22(col1 >> 24);
                f1 += getPow22(col1 >> 16);
                f2 += getPow22(col1 >> 8);
                f3 += getPow22(col1 >> 0);
            }

            if (col2 >> 24 != 0) {
                f += getPow22(col2 >> 24);
                f1 += getPow22(col2 >> 16);
                f2 += getPow22(col2 >> 8);
                f3 += getPow22(col2 >> 0);
            }

            if (col3 >> 24 != 0) {
                f += getPow22(col3 >> 24);
                f1 += getPow22(col3 >> 16);
                f2 += getPow22(col3 >> 8);
                f3 += getPow22(col3 >> 0);
            }

            f /= 4.0F;
            f1 /= 4.0F;
            f2 /= 4.0F;
            f3 /= 4.0F;
            int i1 = (int)(Math.pow((double)f, 0.45454545454545453) * 255.0);
            int j1 = (int)(Math.pow((double)f1, 0.45454545454545453) * 255.0);
            int k1 = (int)(Math.pow((double)f2, 0.45454545454545453) * 255.0);
            int l1 = (int)(Math.pow((double)f3, 0.45454545454545453) * 255.0);
            if (i1 < 96) {
                i1 = 0;
            }

            return i1 << 24 | j1 << 16 | k1 << 8 | l1;
        } else {
            int i = gammaBlend(col0, col1, col2, col3, 24);
            int j = gammaBlend(col0, col1, col2, col3, 16);
            int k = gammaBlend(col0, col1, col2, col3, 8);
            int l = gammaBlend(col0, col1, col2, col3, 0);
            return i << 24 | j << 16 | k << 8 | l;
        }
    }

    private static int gammaBlend(int col0, int col1, int col2, int col3, int bitOffset) {
        float f = getPow22(col0 >> bitOffset);
        float f1 = getPow22(col1 >> bitOffset);
        float f2 = getPow22(col2 >> bitOffset);
        float f3 = getPow22(col3 >> bitOffset);
        float f4 = (float)((double)((float)Math.pow((double)(f + f1 + f2 + f3) * 0.25, 0.45454545454545453)));
        return (int)((double)f4 * 255.0);
    }

    private static float getPow22(int value) {
        return POW22[value & 0xFF];
    }
}
