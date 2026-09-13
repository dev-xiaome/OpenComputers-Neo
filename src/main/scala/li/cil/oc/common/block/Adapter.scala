package li.cil.oc.common.block

import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.BlockPosition
import net.minecraft.world.level.block.Block
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Adapter extends SimpleBlock with traits.GUI {
  override protected def customTextures = Array(
    Some("AdapterTop"),
    Some("AdapterTop"),
    Some("AdapterSide"),
    Some("AdapterSide"),
    Some("AdapterSide"),
    Some("AdapterSide")
  )

  @SideOnly(Dist.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Adapter.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":AdapterOn")
  }

  // ----------------------------------------------------------------------- //

  override def guiType = GuiType.Adapter

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Adapter()

  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(world: Level, x: Int, y: Int, z: Int, block: Block) =
    world.getTileEntity(x, y, z) match {
      case adapter: tileentity.Adapter => adapter.neighborChanged()
      case _ => // Ignore.
    }

  override def onNeighborChange(world: IBlockAccess, x: Int, y: Int, z: Int, tileX: Int, tileY: Int, tileZ: Int) =
    world.getTileEntity(x, y, z) match {
      case adapter: tileentity.Adapter =>
        val (dx, dy, dz) = (tileX - x, tileY - y, tileZ - z)
        val index = 3 + dx + dy + dy + dz + dz + dz
        if (index >= 0 && index < sides.length) {
          adapter.neighborChanged(sides(index))
        }
      case _ => // Ignore.
    }

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))) {
      val sideToToggle = if (player.isSneaking) side.getOpposite else side
      world.getTileEntity(x, y, z) match {
        case adapter: tileentity.Adapter =>
          if (!world.isRemote) {
            val oldValue = adapter.openSides(sideToToggle.ordinal())
            adapter.setSideOpen(sideToToggle, !oldValue)
          }
          true
        case _ => false
      }
    }
    else super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)
  }

  private val sides = Array(
    Direction.NORTH,
    Direction.DOWN,
    Direction.WEST,
    Direction.UNKNOWN,
    Direction.EAST,
    Direction.UP,
    Direction.SOUTH)
}
