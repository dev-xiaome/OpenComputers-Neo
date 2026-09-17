package li.cil.oc.common.capabilities

import li.cil.oc.api
import li.cil.oc.api.network.{Environment, Message, Node, Visibility}
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * `Environment` 是本模组自己的接口，在 NeoForge 1.21.1 里没有对应的能力
 * （`Capability` / `CapabilityManager` / `ICapabilityProvider` 已被移除）。
 *
 * 1.20.1 时代这里会用一个 `Provider` 包一层再注册成能力，但那个包装类只是
 * 把调用原样转发给方块实体本身，所以现在退化为直接的类型判断：方块实体自己
 * 实现了 [[Environment]] 就返回它自己，否则返回 `null`（等价于“没有该能力”）。
 */
object CapabilityEnvironment {

  /** 返回方块实体的 [[Environment]] 视图，没有则返回 `null`。 */
  def get(tileEntity: BlockEntity): Environment = tileEntity match {
    case environment: Environment => environment
    case _ => null
  }

  /** [[get]] 的 `Option` 版本，方便 Scala 侧调用。 */
  def apply(tileEntity: BlockEntity): Option[Environment] = Option(get(tileEntity))

  /** 一个什么都不做的空实现，等价于 1.20.1 里注册的 `DefaultImpl`。 */
  class DefaultImpl extends Environment {
    override val node = api.Network.newNode(this, Visibility.None).create()

    override def onMessage(message: Message): Unit = {}

    override def onConnect(node: Node): Unit = {}

    override def onDisconnect(node: Node): Unit = {}
  }
}
