package li.cil.oc.common.item.traits

import li.cil.oc.Settings
import li.cil.oc.util.PackedColor

/**
 * 显卡类物品的提示数据（原 1.7.10 的 `GPULike`）。
 *
 * 1.7.10 用 `Vec2` 承载分辨率，1.21.1 的 [[li.cil.oc.Settings.screenResolutionsByTier]]
 * 是 `Array[(Int, Int)]`，因此这里用元组解构。
 */
trait GPULike extends Delegate {

  /** 显卡等级（0 起）。 */
  def gpuTier: Int

  override protected def tooltipData: Seq[Any] = {
    val (w, h) = Settings.screenResolutionsByTier(gpuTier)
    val depth = PackedColor.Depth.bits(Settings.screenDepthsByTier(gpuTier))
    Seq(w, h, depth,
      gpuTier match {
        case 0 => "1/1/4/2/2"
        case 1 => "2/4/8/4/4"
        case 2 => "4/8/16/8/8"
        case _ => "4/8/16/8/8"
      })
  }
}
