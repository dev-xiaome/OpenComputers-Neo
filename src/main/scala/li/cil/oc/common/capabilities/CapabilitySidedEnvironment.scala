package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Environment, Node, SidedComponent, SidedEnvironment}
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * NeoForge 1.21.1 里 `SidedEnvironment` 不再是能力，查询时直接做类型判断。
 *
 * 注意优先级与 1.20.1 的注册顺序保持一致：`Environment with SidedComponent`
 * 先于普通的 `SidedEnvironment` 被匹配。
 */
object CapabilitySidedEnvironment {

  /** 返回方块实体的 [[SidedEnvironment]] 视图，没有则返回 `null`。 */
  def get(tileEntity: BlockEntity): SidedEnvironment = tileEntity match {
    case _: Environment with SidedComponent => CapabilitySidedComponent.get(tileEntity)
    case sided: SidedEnvironment => sided
    case _ => null
  }

  /** [[get]] 的 `Option` 版本，方便 Scala 侧调用。 */
  def apply(tileEntity: BlockEntity): Option[SidedEnvironment] = Option(get(tileEntity))

  /** 任何一面都不可连接的默认实现。 */
  class DefaultImpl extends SidedEnvironment {
    override def sidedNode(side: Direction): Node = null

    override def canConnect(side: Direction): Boolean = false
  }
}
