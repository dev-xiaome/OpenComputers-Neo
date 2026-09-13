package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Microcontroller
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.Direction
import org.lwjgl.opengl.GL11

object MicrocontrollerRenderer extends TileEntitySpecialRenderer {
  override def renderTileEntityAt(tileEntity: BlockEntity, x: Double, y: Double, z: Double, f: Float): Unit = {
    RenderState.checkError(getClass.getName + ".renderTileEntityAt: entering (aka: wasntme)")

    val mcu = tileEntity.asInstanceOf[Microcontroller]
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

    RenderState.disableLighting()
    RenderState.makeItBlend()
    RenderState.setBlendAlpha(1)

    GL11.glPushMatrix()

    GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5)

    mcu.yaw match {
      case Direction.WEST => GL11.glRotatef(-90, 0, 1, 0)
      case Direction.NORTH => GL11.glRotatef(180, 0, 1, 0)
      case Direction.EAST => GL11.glRotatef(90, 0, 1, 0)
      case _ => // No yaw.
    }

    GL11.glTranslated(-0.5, 0.5, 0.505)
    GL11.glScalef(1, -1, 1)

    bindTexture(Textures.blockMicrocontrollerFrontLight)
    val t = Tessellator.instance
    t.startDrawingQuads()
    t.addVertexWithUV(0, 1, 0, 0, 1)
    t.addVertexWithUV(1, 1, 0, 1, 1)
    t.addVertexWithUV(1, 0, 0, 1, 0)
    t.addVertexWithUV(0, 0, 0, 0, 0)
    t.draw()

    if (mcu.isRunning) {
      renderFrontOverlay(Textures.blockMicrocontrollerFrontOn)
    }
    else if (mcu.hasErrored && RenderUtil.shouldShowErrorLight(mcu.hashCode)) {
      renderFrontOverlay(Textures.blockMicrocontrollerFrontError)
    }

    RenderState.enableLighting()

    GL11.glPopMatrix()
    GL11.glPopAttrib()

    RenderState.checkError(getClass.getName + ".renderTileEntityAt: leaving")
  }

  private def renderFrontOverlay(texture: ResourceLocation): Unit = {
    bindTexture(texture)
    val t = Tessellator.instance
    t.startDrawingQuads()
    t.addVertexWithUV(0, 1, 0, 0, 1)
    t.addVertexWithUV(1, 1, 0, 1, 1)
    t.addVertexWithUV(1, 0, 0, 1, 0)
    t.addVertexWithUV(0, 0, 0, 0, 0)
    t.draw()
  }
}