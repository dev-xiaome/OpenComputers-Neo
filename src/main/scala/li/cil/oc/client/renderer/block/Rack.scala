package li.cil.oc.client.renderer.block

import li.cil.oc.api.event.RackMountableRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.common
import net.minecraft.client.renderer.RenderBlocks
import net.neoforged.neoforge.common.NeoForge
import net.minecraft.core.Direction

object Rack {
  def render(rack: common.tileentity.Rack, x: Int, y: Int, z: Int, block: common.block.Rack, renderer: RenderBlocks): Unit = {
    val previousRenderAllFaces = renderer.renderAllFaces
    val u1 = 1 / 16f
    val u2 = 15 / 16f
    val v1 = 2 / 16f
    val v2 = 14 / 16f
    val fs = 3 / 16f

    // Top and bottom.
    renderer.renderAllFaces = true
    renderer.setRenderBounds(0, 0, 0, 1, v1, 1)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(0, v2, 0, 1, 1, 1)
    renderer.renderStandardBlock(block, x, y, z)

    // Sides.
    val front = rack.facing
    def renderSide(side: Direction, lx: Double, lz: Double, hx: Double, hz: Double): Unit = {
      if (side == front) {
        for (i <- 0 until 4 if rack.getStackInSlot(i) != null) {
          side match {
            case Direction.WEST =>
              renderer.setRenderBounds(lx + 1 / 16f, v2 - (i + 1) * fs, lz + u1, u2, v2 - i * fs, hz - u1)
            case Direction.EAST =>
              renderer.setRenderBounds(u1, v2 - (i + 1) * fs, lz + u1, hx - 1 / 16f, v2 - i * fs, hz - u1)
            case Direction.NORTH =>
              renderer.setRenderBounds(lx + u1, v2 - (i + 1) * fs, lz + 1 / 16f, hx - u1, v2 - i * fs, u2)
            case Direction.SOUTH =>
              renderer.setRenderBounds(lx + u1, v2 - (i + 1) * fs, u1, hx - u1, v2 - i * fs, hz - 1 / 16f)
            case _ =>
          }

          val event = new RackMountableRenderEvent.Block(rack, i, rack.lastData(i), side, renderer)
          MinecraftForge.EVENT_BUS.post(event)
          if (!event.isCanceled) {
            block.frontOverride = event.getFrontTextureOverride
            renderer.renderStandardBlock(block, x, y, z)
            block.frontOverride = null
          }
        }
      }
      else {
        val isBack = front == side.getOpposite
        if (isBack) {
          renderer.setOverrideBlockTexture(Textures.Rack.icons(Direction.NORTH.ordinal))
        }
        renderer.setRenderBounds(lx, v1, lz, hx, v2, hz)
        renderer.renderStandardBlock(block, x, y, z)
        renderer.clearOverrideBlockTexture()
      }
    }

    renderSide(Direction.WEST, 0, 0, u1, 1)
    renderSide(Direction.EAST, u2, 0, 1, 1)
    renderSide(Direction.NORTH, 0, 0, 1, u1)
    renderSide(Direction.SOUTH, 0, u2, 1, 1)

    renderer.renderAllFaces = previousRenderAllFaces
  }
}
