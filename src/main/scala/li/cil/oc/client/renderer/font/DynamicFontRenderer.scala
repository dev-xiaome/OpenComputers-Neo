package li.cil.oc.client.renderer.font

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix4f
import li.cil.oc.Settings
import li.cil.oc.client.renderer.RenderTypes
import li.cil.oc.util.FontUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.server.packs.resources.ReloadableResourceManager
import scala.collection.mutable

class DynamicFontRenderer extends TextureFontRenderer with ResourceManagerReloadListener {
  private val glyphProvider: IGlyphProvider = new FontParserHex()
  private val textures = mutable.ArrayBuffer.empty[DynamicFontRenderer.CharTexture]
  private val charMap = mutable.Map.empty[Int, DynamicFontRenderer.CharIcon]
  private var activeTextureIndex: Int = 0

  initialize()

  Minecraft.getInstance.getResourceManager match {
    case reloadable: ReloadableResourceManager => reloadable.registerReloadListener(this)
    case _ =>
  }

  def initialize(): Unit = {
    textures.foreach(_.delete())
    textures.clear()
    charMap.clear()
    glyphProvider.initialize()
    textures += new DynamicFontRenderer.CharTexture(this)
    generateChars(basicChars.toCharArray)
  }

  override def onResourceManagerReload(manager: ResourceManager): Unit = initialize()

  override protected def charWidth = glyphProvider.getGlyphWidth
  override protected def charHeight = glyphProvider.getGlyphHeight
  override protected def textureCount = textures.length
  override protected def selectType(index: Int): RenderType = {
    activeTextureIndex = index
    textures(index).getType
  }

  override protected def generateChar(char: Int): Unit = {
    charMap.getOrElseUpdate(char, createCharIcon(char))
  }

  override protected def drawChar(builder: VertexConsumer, matrix: Matrix4f, color: Int, tx: Float, ty: Float, char: Int): Unit = {
    charMap.get(char).foreach { icon =>
      if (icon.texture == textures(activeTextureIndex)) {
        icon.draw(builder, matrix, color, tx, ty)
      }
    }
  }

  private def createCharIcon(char: Int): DynamicFontRenderer.CharIcon = {
    if (FontUtils.wcwidth(char) < 1 || glyphProvider.getGlyph(char) == null) {
      if (char == '?') null else charMap.getOrElseUpdate('?', createCharIcon('?'))
    } else {
      if (textures.last.isFull(char)) textures += new DynamicFontRenderer.CharTexture(this)
      textures.last.add(char)
    }
  }
}

object DynamicFontRenderer {
  private val size = 256

  class CharTexture(val owner: DynamicFontRenderer) {
    private val texture = new DynamicTexture(size, size, false)
    private val name = "oc_font_cache_" + System.nanoTime()
    private val location = Minecraft.getInstance.getTextureManager.register(name, texture)
    private val rt = RenderTypes.createFontTex(name, location, Settings.get.textAntiAlias)

    private val cellWidth = owner.charWidth + 2
    private val cellHeight = owner.charHeight + 2
    private val cols = size / cellWidth
    private val rows = size / cellHeight
    private val uStep = cellWidth / size.toFloat
    private val vStep = cellHeight / size.toFloat
    private val pad = 1f / size
    private val capacity = cols * rows
    private var chars = 0

    def delete(): Unit = {
      texture.close()
      Minecraft.getInstance.getTextureManager.release(location)
    }

    def getType = rt
    def isFull(char: Int) = chars + FontUtils.wcwidth(char) > capacity

    def add(char: Int): CharIcon = {
      val glyphWidth = FontUtils.wcwidth(char)
      val w = owner.charWidth * glyphWidth
      val h = owner.charHeight
      if (chars % cols + glyphWidth > cols) chars += (cols - (chars % cols))
      val x = chars % cols
      val y = chars / cols

      val glyphData = owner.glyphProvider.getGlyph(char)
      val pixels = texture.getPixels
      for (gy <- 0 until h; gx <- 0 until w) {
        val i = (gx + gy * w) * 4
        val r = glyphData.get(i) & 0xFF
        val g = glyphData.get(i + 1) & 0xFF
        val b = glyphData.get(i + 2) & 0xFF
        val a = glyphData.get(i + 3) & 0xFF
        val abgr = (a << 24) | (b << 16) | (g << 8) | r
        pixels.setPixelRGBA(1 + x * cellWidth + gx, 1 + y * cellHeight + gy, abgr)
      }
      texture.upload()

      val icon = new CharIcon(this, w, h, pad + x * uStep, pad + y * vStep, (x + glyphWidth) * uStep - pad, (y + 1) * vStep - pad)
      chars += glyphWidth
      icon
    }
  }

  class CharIcon(val texture: CharTexture, val w: Int, val h: Int, val u1: Float, val v1: Float, val u2: Float, val v2: Float) {
    def draw(builder: VertexConsumer, matrix: Matrix4f, color: Int, tx: Float, ty: Float): Unit = {
      val r = (color >> 16) & 0xFF
      val g = (color >> 8) & 0xFF
      val b = color & 0xFF
      builder.addVertex(matrix, tx, ty + h, 0).setColor(r, g, b, 255).setUv(u1, v2)
      builder.addVertex(matrix, tx + w, ty + h, 0).setColor(r, g, b, 255).setUv(u2, v2)
      builder.addVertex(matrix, tx + w, ty, 0).setColor(r, g, b, 255).setUv(u2, v1)
      builder.addVertex(matrix, tx, ty, 0).setColor(r, g, b, 255).setUv(u1, v1)
    }
  }
}