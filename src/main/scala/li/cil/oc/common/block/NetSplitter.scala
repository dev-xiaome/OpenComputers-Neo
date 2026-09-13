package li.cil.oc.common.block

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.BlockPosition
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class NetSplitter extends RedstoneAware {
  override protected def customTextures = Array(
    Some("NetSplitterTop"),
    Some("NetSplitterTop"),
    Some("NetSplitterSide"),
    Some("NetSplitterSide"),
    Some("NetSplitterSide"),
    Some("NetSplitterSide")
  )

  @SideOnly(Dist.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.NetSplitter.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":NetSplitterOn")
  }

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: Direction): Boolean = false

  // ----------------------------------------------------------------------- //

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.NetSplitter()

  // ----------------------------------------------------------------------- //

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))) {
      val sideToToggle = if (player.isSneaking) side.getOpposite else side
      world.getTileEntity(x, y, z) match {
        case splitter: tileentity.NetSplitter =>
          if (!world.isRemote) {
            val oldValue = splitter.openSides(sideToToggle.ordinal())
            splitter.setSideOpen(sideToToggle, !oldValue)
          }
          true
        case _ => false
      }
    }
    else super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)
  }
}
