package li.cil.oc.client.renderer.font

import com.mojang.blaze3d.platform.NativeImage
import li.cil.oc.Settings
import li.cil.oc.client.renderer.font.TextureFontRenderer.Glyph
import li.cil.oc.util.FontUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation

import scala.collection.mutable

/**
 * 用 hex 字形数据（[[IGlyphProvider]]）在运行时生成字形图集的字体渲染器。
 *
 * ==1.7.10 到 1.21.1 的变化==
 * 旧版直接 `GL11.glGenTextures` / `glTexImage2D` 建纹理、`glTexSubImage2D` 逐个上传字形，
 * 并用 `IResourceManagerReloadListener` 监听资源重载。1.21.1 改成：
 *  - 用 [[com.mojang.blaze3d.platform.NativeImage]] 当 CPU 侧图集，
 *    字形像素用 `NativeImage#setPixelRGBA` 写入；
 *  - 交给 [[net.minecraft.client.renderer.texture.DynamicTexture]] 管理 GL 纹理，
 *    由 `TextureManager#register(String, DynamicTexture)` 分配贴图名；
 *  - 一帧里所有新字形只在 [flushPendingUploads] 里上传一次，避免每加一个字形就重传整页；
 *  - 资源重载改由外部入口 [initialize] 触发（由 `client.Proxy` 调用
 *    [[li.cil.oc.client.renderer.TextBufferRenderCache.initialize]]）。
 */
class DynamicFontRenderer extends TextureFontRenderer {
  private val glyphProvider: IGlyphProvider = new FontParserHex()

  private val pages = mutable.ArrayBuffer.empty[DynamicFontRenderer.GlyphPage]

  /** 码点 -> 字形矩形；值为 null 表示该码点确认没有字形（缺字回退到问号也算命中）。 */
  private val glyphs = mutable.Map.empty[Int, Glyph]

  initialize()

  // ----------------------------------------------------------------------- //
  // 生命周期
  // ----------------------------------------------------------------------- //

  override def initialize(): Unit = {
    releasePages()
    glyphs.clear()
    // 必须先重新解析字形数据，再预生成基本字符：否则基本字符会被缓存成「缺字」，
    // 之后即使数据到位也不会再重试。
    // 1.7.10 靠 `IReloadableResourceManager#registerReloadListener` 的立即回调触发这一步，
    // 1.21.1 没有该回调，只能由外部入口（client.Proxy）显式调用。
    glyphProvider.initialize()
    newPage()
    generateChars(basicChars.toCharArray)
  }

  override protected def charWidth: Int = glyphProvider.getGlyphWidth

  override protected def charHeight: Int = glyphProvider.getGlyphHeight

  override protected def textureSize: Int = DynamicFontRenderer.PageSize

  override protected def textureLocation(page: Int): ResourceLocation = {
    if (pages.isEmpty) null
    else pages(math.max(0, math.min(pages.length - 1, page))).location
  }

  /** 把这一帧里攒下的字形改动一次性上传。 */
  override protected def flushPendingUploads(): Unit = {
    var i = 0
    while (i < pages.length) {
      val page = pages(i)
      if (page.dirty) {
        page.texture.upload()
        page.dirty = false
      }
      i += 1
    }
  }

  // ----------------------------------------------------------------------- //
  // 字形
  // ----------------------------------------------------------------------- //

  override protected def glyph(char: Int): Glyph = {
    if (glyphs.contains(char)) glyphs(char)
    else {
      val created = createGlyph(char)
      glyphs.put(char, created)
      created
    }
  }

  private def createGlyph(char: Int): Glyph = {
    // 控制字符没有字形，等价于旧版的 wcwidth < 1 分支。
    if (FontUtils.wcwidth(char) < 1) return null

    val data = glyphProvider.getGlyph(char)
    if (data == null) {
      // 缺字回退到问号；问号本身也缺失时只能放弃。
      if (char == '?') return null
      return glyph('?')
    }

    val width = charWidth * FontUtils.wcwidth(char)
    val height = charHeight
    if (width <= 0 || height <= 0) return null
    // 防御性检查：字形字节数不足时不能按 width * height 取像素。
    if (data.capacity() < width * height * 4) return null

    var index = pages.length - 1
    var slot = pages(index).allocate(width, height)
    if (slot.isEmpty) {
      newPage()
      index = pages.length - 1
      slot = pages(index).allocate(width, height)
    }

    slot match {
      case Some((x, y)) =>
        val page = pages(index)
        var py = 0
        while (py < height) {
          var px = 0
          while (px < width) {
            // 字形数据每像素 4 字节 RGBA，且只可能是「白色不透明」或「全透明」，
            // 因此只看 alpha 通道即可（0xFFFFFFFF 在 RGBA 布局下就是白色不透明）。
            if (data.get((py * width + px) * 4 + 3) != 0) {
              page.image.setPixelRGBA(x + px, y + py, 0xFFFFFFFF)
            }
            px += 1
          }
          py += 1
        }
        page.dirty = true
        Glyph(page.index, x, y, width, height)
      case None =>
        // 图集页排满又开不了新页（实际不会发生），放弃这个字形。
        null
    }
  }

  // ----------------------------------------------------------------------- //
  // 纹理页
  // ----------------------------------------------------------------------- //

  private def newPage(): Unit = {
    pages += new DynamicFontRenderer.GlyphPage(pages.length)
  }

  private def releasePages(): Unit = {
    var i = 0
    while (i < pages.length) {
      pages(i).close()
      i += 1
    }
    pages.clear()
  }
}

object DynamicFontRenderer {
  /** 图集页边长（像素）。旧版同样是 256。 */
  val PageSize: Int = 256

  /**
   * 一页字形图集。
   *
   * 字形按行排布，每个字形四周留 1 像素空隙，避免开启线性过滤时相邻字形互相渗色。
   * 像素用 `NativeImage#setPixelRGBA` 写入，攒够一帧再统一 `DynamicTexture#upload`。
   */
  class GlyphPage(val index: Int) {
    val image: NativeImage = new NativeImage(NativeImage.Format.RGBA, PageSize, PageSize, true)

    val texture: DynamicTexture = new DynamicTexture(image)

    /** 贴图名由 TextureManager 生成，形如 `minecraft:dynamic/oc_font_1`。 */
    val location: ResourceLocation = Minecraft.getInstance.getTextureManager.register("oc_font", texture)

    var dirty: Boolean = false

    private val padding = 1
    private var cursorX = padding
    private var cursorY = padding
    private var rowHeight = 0

    // 旧版对 MIN_FILTER 用 LINEAR/NEAREST、MAG_FILTER 固定 NEAREST；
    // 1.21.1 的 AbstractTexture#setFilter 会同时设置这两个，这里按配置选择。
    if (Settings.get.textLinearFiltering) texture.setFilter(true, false)
    else texture.setFilter(false, false)

    /**
     * 在本页申请一块 width x height 的区域。
     *
     * 当前行放不下就换行；换行后仍放不下说明本页已满，返回 None。
     */
    def allocate(width: Int, height: Int): Option[(Int, Int)] = {
      if (cursorX + width + padding > PageSize) {
        cursorX = padding
        cursorY += rowHeight + padding
        rowHeight = 0
      }
      if (cursorY + height + padding > PageSize) None
      else {
        val x = cursorX
        val y = cursorY
        cursorX += width + padding
        rowHeight = math.max(rowHeight, height)
        Some((x, y))
      }
    }

    /** 释放贴图；之后本对象不可再使用。 */
    def close(): Unit = {
      val mc = Minecraft.getInstance
      if (mc != null) mc.getTextureManager.release(location)
    }
  }
}
