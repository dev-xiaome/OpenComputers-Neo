package li.cil.oc.common.block

import li.cil.oc.OpenComputers
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

class Waypoint extends RedstoneAware {
  override protected def customTextures = Array(
    None,
    Some("WaypointTop"),
    Some("WaypointBack"),
    Some("WaypointFront"),
    Some("WaypointSide"),
    Some("WaypointSide")
  )

  // ----------------------------------------------------------------------- //

  override def createTileEntity(world: Level, metadata: Int) = new tileentity.Waypoint()

  // ----------------------------------------------------------------------- //

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (!player.isSneaking) {
      if (world.isRemote) {
        player.openGui(OpenComputers, GuiType.Waypoint.id, world, x, y, z)
      }
      true
    }
    else super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)
  }

  override def getValidRotations(world: Level, x: Int, y: Int, z: Int) =
    world.getTileEntity(x, y, z) match {
      case waypoint: tileentity.Waypoint =>
        Direction.VALID_DIRECTIONS.filter {
          d => d != waypoint.facing && d != waypoint.facing.getOpposite
        }
      case _ => super.getValidRotations(world, x, y, z)
    }
}
