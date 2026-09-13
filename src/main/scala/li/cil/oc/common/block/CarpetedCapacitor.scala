package li.cil.oc.common.block

import li.cil.oc.common.tileentity
import net.minecraft.world.level.Level

class CarpetedCapacitor extends Capacitor {
  override def createTileEntity(world: Level, metadata: Int) = new tileentity.CarpetedCapacitor()

  override protected def customTextures = Array(
    None,
    Some("CarpetCapacitorTop"),
    Some("CapacitorSide"),
    Some("CapacitorSide"),
    Some("CapacitorSide"),
    Some("CapacitorSide")
  )
}
