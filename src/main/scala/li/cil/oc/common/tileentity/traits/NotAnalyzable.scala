package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Node
import net.minecraft.world.entity.player.Player

/**
 * 不允许被分析器（Analyzer）读取节点的方块实体
 * （对应 1.7.10 的 `common.tileentity.traits.NotAnalyzable`）。
 *
 * 1.21.1 迁移：无变化，`api.network.Analyzable#onAnalyze` 的签名与 1.7.10 一致。
 */
trait NotAnalyzable extends Analyzable {
  // 注意：Scala 的自类型不会被继承，每个子 trait 都必须重新声明。
  self: net.minecraft.world.level.block.entity.BlockEntity =>

  override def onAnalyze(player: Player, side: Int, hitX: Float, hitY: Float, hitZ: Float): Array[Node] = null
}
