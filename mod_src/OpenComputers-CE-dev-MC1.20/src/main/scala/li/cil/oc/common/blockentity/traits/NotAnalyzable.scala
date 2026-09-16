package li.cil.oc.common.blockentity.traits

import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Node
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player

trait NotAnalyzable extends Analyzable {
  override def onAnalyze(player: Player, side: Direction, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = null
}
