package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Printer extends SimpleBlock with traits.SpecialBlock with traits.StateAware with traits.GUI {
  override protected def customTextures = Array(
    None,
    Some("PrinterTop"),
    Some("PrinterSide"),
    Some("PrinterSide"),
    Some("PrinterSide"),
    Some("PrinterSide")
  )

  override def isBlockSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = side == Direction.DOWN

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction) = side == Direction.DOWN

  // ----------------------------------------------------------------------- //

  override def guiType = GuiType.Printer

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Printer()
}
