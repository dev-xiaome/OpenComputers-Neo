package li.cil.oc.common.capabilities

import li.cil.oc.api.network.{Environment, Node, SidedComponent, SidedEnvironment}
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * `SidedComponent` 只是一个“是否允许在此面连接节点”的判定接口，本身不是
 * [[SidedEnvironment]]。1.20.1 时代通过一个额外的能力注册把它伪装成
 * `SidedEnvironment`，NeoForge 1.21.1 没有自定义能力注册的必要，这里直接
 * 在查询时做类型判断并用 [[Provider]] 适配。
 */
object CapabilitySidedComponent {

  /** 返回方块实体对应的 [[SidedEnvironment]] 视图，没有则返回 `null`。 */
  def get(tileEntity: BlockEntity): SidedEnvironment = tileEntity match {
    case component: Environment with SidedComponent => new Provider(component)
    case _ => null
  }

  /** [[get]] 的 `Option` 版本，方便 Scala 侧调用。 */
  def apply(tileEntity: BlockEntity): Option[SidedEnvironment] = Option(get(tileEntity))

  /** 把 `Environment with SidedComponent` 适配成 [[SidedEnvironment]]。 */
  class Provider(val tileEntity: Environment with SidedComponent) extends SidedEnvironment {
    override def sidedNode(side: Direction): Node = if (tileEntity.canConnectNode(side)) tileEntity.node else null

    override def canConnect(side: Direction): Boolean = tileEntity.canConnectNode(side)
  }
}
