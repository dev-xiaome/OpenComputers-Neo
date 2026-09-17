package li.cil.oc.client.renderer.font;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@code font.hex} 资源里解析等宽位图字形的字形提供者。
 * <p>
 * 与 1.7.10 版的区别：
 * <ul>
 *     <li>字形表从 {@code gnu.trove} 的 {@code TIntObjectHashMap} 换成 JDK 的
 *     {@link HashMap}（本文件因此不再需要 trove 依赖）。</li>
 *     <li>资源枚举从 {@code IResourceManager#getAllResources} 换成
 *     {@code ResourceManager#getResourceStack}，逐个用 {@link Resource#open()} 读取。</li>
 *     <li>{@code new ResourceLocation(ns, path)} 换成
 *     {@link ResourceLocation#fromNamespaceAndPath(String, String)}。</li>
 *     <li>本文件是 Java 源码，而 {@code build.gradle} 里
 *     {@code compileScala} 依赖 {@code compileJava}（Java 先编译），
 *     因此这里<b>不能</b>引用 Scala 对象。原本用到的
 *     {@code Settings.resourceDomain} / {@code Settings#logHexFontErrors} /
 *     {@code FontUtils.wcwidth} 三个 Scala 依赖按下面的方式各自替换：
 *     <ul>
 *         <li>命名空间：与 {@code li.cil.oc.common.DataComponents} /
 *         {@code li.cil.oc.api.IMC} 一样直接硬编码 mod id。</li>
 *         <li>{@code logHexFontErrors}：Java 侧读不到 Scala 配置，
 *         改用同名的系统属性，默认关闭（与 application.conf 的默认值一致）。</li>
 *         <li>{@code FontUtils.wcwidth}：改为按字形数据长度判定宽度
 *         （font.hex 的数据长度本身就是 {@code FontUtils} 双宽表的来源，
 *         见 {@code FontUtils} 读取 font.hex 时 64 / 32 两个分支），
 *         控制字符则用码点范围直接过滤。</li>
 *     </ul>
 *     </li>
 * </ul>
 */
public class FontParserHex implements IGlyphProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 资源命名空间。Java 侧不能引用 Scala 常量，与其它 Java 类一样硬编码。 */
    private static final String RESOURCE_DOMAIN = "opencomputers_neo";

    /** 对应 Scala 配置 {@code debug.logHexFontErrors}，Java 侧改用同名系统属性。 */
    private static final boolean LOG_HEX_FONT_ERRORS = Boolean.getBoolean("opencomputers.logHexFontErrors");

    /** 理论上的 Unicode 码点上限。 */
    private static final int CODEPOINT_LIMIT = 0x110000;

    private static final byte[] OPAQUE = {(byte) 255, (byte) 255, (byte) 255, (byte) 255};
    private static final byte[] TRANSPARENT = {0, 0, 0, 0};

    private final Map<Integer, byte[]> glyphs = new HashMap<>();

    /** 返回十六进制字符对应的数值，非法字符返回 -1。 */
    private static int hex2int(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'F') {
            return c - ('A' - 10);
        } else if (c >= 'a' && c <= 'f') {
            return c - ('a' - 10);
        } else {
            return -1;
        }
    }

    @Override
    public void initialize() {
        try {
            glyphs.clear();

            LOGGER.info("Loading Unicode glyphs...");
            long time = System.currentTimeMillis();
            int glyphCount = 0;

            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(RESOURCE_DOMAIN, "font.hex");
            final Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                LOGGER.warn("Minecraft instance not available, skipping glyph loading.");
                return;
            }
            final List<Resource> resources = mc.getResourceManager().getResourceStack(loc);
            for (Resource resource : resources) {
                try (InputStream font = resource.open()) {
                    final BufferedReader input = new BufferedReader(new InputStreamReader(font, StandardCharsets.UTF_8));
                    String line;
                    while ((line = input.readLine()) != null) {
                        final int separator = line.indexOf(':');
                        if (separator <= 0) {
                            continue; // 空行或没有分隔符。
                        }
                        final String info = line.substring(0, separator);
                        final int charCode;
                        try {
                            charCode = Integer.parseInt(info, 16);
                        } catch (NumberFormatException ex) {
                            continue; // 不是合法的码点，跳过。
                        }
                        if (charCode < 0 || charCode >= CODEPOINT_LIMIT) {
                            LOGGER.warn(String.format("Unicode font contained unexpected glyph: U+%04X, ignoring", charCode));
                            continue; // Out of bounds.
                        }
                        // 控制字符没有可渲染的字形，等价于旧版的 wcwidth < 1 分支。
                        if (charCode < 0x20 || charCode == 0x7F) {
                            continue;
                        }
                        // 每两个十六进制字符表示一行八个像素。
                        final int glyphStrOfs = info.length() + 1;
                        final int dataLength = line.length() - glyphStrOfs;
                        if (dataLength <= 0 || (dataLength & 1) != 0) {
                            continue; // 数据段长度必须是偶数。
                        }
                        final byte[] glyph = new byte[dataLength >> 1];
                        final int glyphWidth = glyph.length / getGlyphHeight();
                        // 旧版用 FontUtils.wcwidth 校验宽度；这里改为只接受 1 倍宽与 2 倍宽，
                        // 与 font.hex 的数据长度定义一致。
                        if (glyphWidth != 1 && glyphWidth != 2) {
                            if (LOG_HEX_FONT_ERRORS) {
                                LOGGER.warn(String.format(
                                        "Size of glyph for code point U+%04X (%s) in font (%d) is not one or two columns wide, ignoring.",
                                        charCode, String.valueOf((char) charCode), glyphWidth));
                            }
                            continue;
                        }
                        boolean valid = true;
                        int ofs = glyphStrOfs;
                        for (int i = 0; i < glyph.length; i++, ofs += 2) {
                            final int high = hex2int(line.charAt(ofs));
                            final int low = hex2int(line.charAt(ofs + 1));
                            if (high < 0 || low < 0) {
                                valid = false;
                                break;
                            }
                            glyph[i] = (byte) ((high << 4) | low);
                        }
                        if (!valid) {
                            if (LOG_HEX_FONT_ERRORS) {
                                LOGGER.warn(String.format("Malformed glyph data for code point U+%04X, ignoring.", charCode));
                            }
                            continue;
                        }
                        if (glyphs.put(charCode, glyph) == null) {
                            glyphCount++;
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.warn("Error parsing font.", ex);
                }
            }

            LOGGER.info("Loaded {} glyphs in {} milliseconds.", glyphCount, System.currentTimeMillis() - time);
        } catch (Throwable t) {
            // 1.21.1 的资源管理器可能尚未就绪；这里一律降级为日志，不抛出。
            LOGGER.warn("Failed loading glyphs.", t);
        }
    }

    @Override
    public ByteBuffer getGlyph(int charCode) {
        final byte[] glyph = glyphs.get(charCode);
        if (glyph == null || glyph.length == 0) {
            return null;
        }
        final ByteBuffer buffer = BufferUtils.createByteBuffer(glyph.length * getGlyphWidth() * 4);
        for (byte aGlyph : glyph) {
            int c = ((int) aGlyph) & 0xFF;
            // Grab all bits by grabbing the leftmost one then shifting.
            for (int j = 0; j < 8; j++) {
                final boolean isBitSet = (c & 0x80) > 0;
                if (isBitSet) {
                    buffer.put(OPAQUE);
                } else {
                    buffer.put(TRANSPARENT);
                }
                c <<= 1;
            }
        }
        buffer.rewind();
        return buffer;
    }

    @Override
    public int getGlyphWidth() {
        return 8;
    }

    @Override
    public int getGlyphHeight() {
        return 16;
    }
}
