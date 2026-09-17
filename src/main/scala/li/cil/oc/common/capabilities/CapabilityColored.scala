package li.cil.oc.common.capabilities

import li.cil.oc.api.internal.Colored
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * `Colored` 是本模组自己的接口，NeoForge 1.21.1 下没有对应的能力，
 * 查询时直接做类型判断即可（1.20.1 的 `Provider` 只是原样转发给方块实体）。
 */
object CapabilityColored {

  /** 返回方块实体的 [[Colored]] 视图，没有则返回 `null`。 */
  def get(tileEntity: BlockEntity): Colored = tileEntity match {
    case colored: Colored => colored
    case _ => null
  }

  /** [[get]] 的 `Option` 版本，方便 Scala 侧调用。 */
  def apply(tileEntity: BlockEntity): Option[Colored] = Option(get(tileEntity))

  /** 不控制连接颜色的默认实现。 */
  class DefaultImpl extends Colored {
    var color = 0

    override def getColor = color

    override def setColor(value: Int): Unit = color = value

    override def controlsConnectivity = false
  }
}
