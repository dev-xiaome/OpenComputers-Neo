package li.cil.oc.common.block

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Transposer extends SimpleBlock {
  override protected def customTextures = Array(
    Some("TransposerTop"),
    Some("TransposerTop"),
    Some("TransposerSide"),
    Some("TransposerSide"),
    Some("TransposerSide"),
    Some("TransposerSide")
  )

  @SideOnly(Dist.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Transposer.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":TransposerOn")
  }

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction): Boolean = false

  // ----------------------------------------------------------------------- //

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Transposer()
}
