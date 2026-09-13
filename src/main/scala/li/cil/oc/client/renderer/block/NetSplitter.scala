package li.cil.oc.client.renderer.block

import net.minecraft.world.level.block.Block
import net.minecraft.client.renderer.RenderBlocks
import net.minecraft.core.Direction

object NetSplitter {
  def render(openSides: Array[Boolean], block: Block, x: Int, y: Int, z: Int, renderer: RenderBlocks): Unit = {
    val previousRenderAllFaces = renderer.renderAllFaces
    renderer.renderAllFaces = true

    // Bottom.
    renderer.setRenderBounds(0 / 16f, 0 / 16f, 5 / 16f, 5 / 16f, 5 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(11 / 16f, 0 / 16f, 5 / 16f, 16 / 16f, 5 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(5 / 16f, 0 / 16f, 0 / 16f, 11 / 16f, 5 / 16f, 5 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(5 / 16f, 0 / 16f, 11 / 16f, 11 / 16f, 5 / 16f, 16 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    // Corners.
    renderer.setRenderBounds(0 / 16f, 0 / 16f, 0 / 16f, 5 / 16f, 16 / 16f, 5 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(11 / 16f, 0 / 16f, 0 / 16f, 16 / 16f, 16 / 16f, 5 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(0 / 16f, 0 / 16f, 11 / 16f, 5 / 16f, 16 / 16f, 16 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(11 / 16f, 0 / 16f, 11 / 16f, 16 / 16f, 16 / 16f, 16 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    // Top.
    renderer.setRenderBounds(0 / 16f, 11 / 16f, 5 / 16f, 5 / 16f, 16 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(11 / 16f, 11 / 16f, 5 / 16f, 16 / 16f, 16 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(5 / 16f, 11 / 16f, 0 / 16f, 11 / 16f, 16 / 16f, 5 / 16f)
    renderer.renderStandardBlock(block, x, y, z)
    renderer.setRenderBounds(5 / 16f, 11 / 16f, 11 / 16f, 11 / 16f, 16 / 16f, 16 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    // Sides.
    val down = openSides(Direction.DOWN.ordinal())
    renderer.setRenderBounds(5 / 16f, if (down) 0 / 16f else 2 / 16f, 5 / 16f, 11 / 16f, 5 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    val up = openSides(Direction.UP.ordinal())
    renderer.setRenderBounds(5 / 16f, 11 / 16f, 5 / 16f, 11 / 16f, if (up) 16 / 16f else 14f / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    val north = openSides(Direction.NORTH.ordinal())
    renderer.setRenderBounds(5 / 16f, 5 / 16f, if (north) 0 / 16f else 2 / 16f, 11 / 16f, 11 / 16f, 5 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    val south = openSides(Direction.SOUTH.ordinal())
    renderer.setRenderBounds(5 / 16f, 5 / 16f, 11 / 16f, 11 / 16f, 11 / 16f, if (south) 16 / 16f else 14 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    val west = openSides(Direction.WEST.ordinal())
    renderer.setRenderBounds(if (west) 0 / 16f else 2 / 16f, 5 / 16f, 5 / 16f, 5 / 16f, 11 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    val east = openSides(Direction.EAST.ordinal())
    renderer.setRenderBounds(11 / 16f, 5 / 16f, 5 / 16f, if (east) 16 / 16f else 14 / 16f, 11 / 16f, 11 / 16f)
    renderer.renderStandardBlock(block, x, y, z)

    renderer.renderAllFaces = previousRenderAllFaces
  }

  def render(block: Block, metadata: Int, renderer: RenderBlocks): Unit = {
    // Bottom.
    renderer.setRenderBounds(0 / 16f, 0 / 16f, 5 / 16f, 5 / 16f, 5 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(11 / 16f, 0 / 16f, 5 / 16f, 16 / 16f, 5 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 0 / 16f, 0 / 16f, 11 / 16f, 5 / 16f, 5 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 0 / 16f, 11 / 16f, 11 / 16f, 5 / 16f, 16 / 16f)
    renderAllFaces(block, metadata, renderer)
    // Corners.
    renderer.setRenderBounds(0 / 16f, 0 / 16f, 0 / 16f, 5 / 16f, 16 / 16f, 5 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(11 / 16f, 0 / 16f, 0 / 16f, 16 / 16f, 16 / 16f, 5 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(0 / 16f, 0 / 16f, 11 / 16f, 5 / 16f, 16 / 16f, 16 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(11 / 16f, 0 / 16f, 11 / 16f, 16 / 16f, 16 / 16f, 16 / 16f)
    renderAllFaces(block, metadata, renderer)
    // Top.
    renderer.setRenderBounds(0 / 16f, 11 / 16f, 5 / 16f, 5 / 16f, 16 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(11 / 16f, 11 / 16f, 5 / 16f, 16 / 16f, 16 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 11 / 16f, 0 / 16f, 11 / 16f, 16 / 16f, 5 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 11 / 16f, 11 / 16f, 11 / 16f, 16 / 16f, 16 / 16f)
    renderAllFaces(block, metadata, renderer)

    // Sides.
    renderer.setRenderBounds(5 / 16f, 2 / 16f, 5 / 16f, 11 / 16f, 5 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 11 / 16f, 5 / 16f, 11 / 16f, 14 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 5 / 16f, 2 / 16f, 11 / 16f, 11 / 16f, 5 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(5 / 16f, 5 / 16f, 11 / 16f, 11 / 16f, 11 / 16f, 14 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(2 / 16f, 5 / 16f, 5 / 16f, 5 / 16f, 11 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
    renderer.setRenderBounds(11 / 16f, 5 / 16f, 5 / 16f, 14 / 16f, 11 / 16f, 11 / 16f)
    renderAllFaces(block, metadata, renderer)
  }

  private def renderAllFaces(block: Block, metadata: Int, renderer: RenderBlocks): Unit = {
    BlockRenderer.renderFaceYPos(block, metadata, renderer)
    BlockRenderer.renderFaceYNeg(block, metadata, renderer)
    BlockRenderer.renderFaceXPos(block, metadata, renderer)
    BlockRenderer.renderFaceXNeg(block, metadata, renderer)
    BlockRenderer.renderFaceZPos(block, metadata, renderer)
    BlockRenderer.renderFaceZNeg(block, metadata, renderer)
  }
}
