package li.cil.oc.common.capabilities

import li.cil.oc.api.audio.AudioReceiver
import net.minecraft.world.level.block.entity.BlockEntity

/**
 * `AudioReceiver` 是本模组自己的接口，NeoForge 1.21.1 下没有对应的能力，
 * 查询时直接做类型判断即可（1.20.1 的 `Provider` 只是原样转发给方块实体）。
 */
object CapabilityAudioReceiver {

  /** 返回方块实体的 [[AudioReceiver]] 视图，没有则返回 `null`。 */
  def get(tileEntity: BlockEntity): AudioReceiver = tileEntity match {
    case receiver: AudioReceiver => receiver
    case _ => null
  }

  /** [[get]] 的 `Option` 版本，方便 Scala 侧调用。 */
  def apply(tileEntity: BlockEntity): Option[AudioReceiver] = Option(get(tileEntity))
}
