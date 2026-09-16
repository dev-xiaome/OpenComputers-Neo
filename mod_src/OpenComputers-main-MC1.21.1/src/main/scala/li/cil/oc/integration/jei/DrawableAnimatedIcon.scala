package li.cil.oc.integration.jei

import mezz.jei.api.gui.ITickTimer
import mezz.jei.api.gui.drawable.IDrawableAnimated
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.{Dist, OnlyIn}

/** Used to simulate an animated texture. */
class DrawableAnimatedIcon(resourceLocation: ResourceLocation, u: Int, v: Int, width: Int, height: Int, textureWidth: Int, textureHeight: Int,
                           tickTimer: ITickTimer, uOffset: Int, vOffset: Int,
                           paddingTop: Int = 0, paddingBottom: Int = 0, paddingLeft: Int = 0, paddingRight: Int = 0) extends IDrawableAnimated {

  override def getWidth: Int = width + paddingLeft + paddingRight
  override def getHeight: Int = height + paddingTop + paddingBottom

  @OnlyIn(Dist.CLIENT)
  override def draw(graphics: GuiGraphics, xOffset: Int, yOffset: Int): Unit = {
    val animationValue = tickTimer.getValue
    val x = xOffset + paddingLeft
    val y = yOffset + paddingTop
    val animatedU = u + uOffset * animationValue
    val animatedV = v + vOffset * animationValue
    graphics.blit(resourceLocation, x, y, animatedU, animatedV, width, height, textureWidth, textureHeight)
  }
}
