package li.cil.oc.common.block

import java.util.Random

import li.cil.oc.common.tileentity
import li.cil.oc.integration.coloredlights.ModColoredLights
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.Level

class Capacitor extends SimpleBlock {
  ModColoredLights.setLightLevel(this, 5, 5, 5)

  setTickRandomly(true)

  override protected def customTextures = Array(
    None,
    Some("CapacitorTop"),
    Some("CapacitorSide"),
    Some("CapacitorSide"),
    Some("CapacitorSide"),
    Some("CapacitorSide")
  )

  // ----------------------------------------------------------------------- //

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Capacitor()

  // ----------------------------------------------------------------------- //

  override def hasComparatorInputOverride = true

  override def getComparatorInputOverride(world: Level, x: Int, y: Int, z: Int, side: Int) =
    world.getTileEntity(x, y, z) match {
      case capacitor: tileentity.Capacitor if !world.isRemote =>
        math.round(15 * capacitor.node.localBuffer / capacitor.node.localBufferSize).toInt
      case _ => 0
    }

  override def updateTick(world: Level, x: Int, y: Int, z: Int, rng: Random): Unit = {
    world.notifyBlocksOfNeighborChange(x, y, z, this)
  }

  override def tickRate(world : Level) = 1

  override def onNeighborBlockChange(world: Level, x: Int, y: Int, z: Int, block: Block) =
    world.getTileEntity(x, y, z) match {
      case capacitor: tileentity.Capacitor => capacitor.recomputeCapacity()
      case _ =>
    }
}
