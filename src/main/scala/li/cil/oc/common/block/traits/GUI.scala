package li.cil.oc.common.block.traits

import li.cil.oc.OpenComputers
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.SimpleBlock
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.core.Direction

trait GUI extends SimpleBlock {
  def guiType: GuiType.EnumVal

  override def onBlockActivated(world: Level, x: Int, y: Int, z: Int, player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float) = {
    if (!player.isSneaking) {
      if (!world.isRemote) {
        player.openGui(OpenComputers, guiType.id, world, x, y, z)
      }
      true
    }
    else super.onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)
  }
}
