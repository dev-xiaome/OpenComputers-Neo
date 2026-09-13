package li.cil.oc.server

import li.cil.oc.common.{GuiHandler => CommonGuiHandler}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

object GuiHandler extends CommonGuiHandler {
  override def getClientGuiElement(id: Int, player: Player, world: Level, x: Int, y: Int, z: Int) = null
}
