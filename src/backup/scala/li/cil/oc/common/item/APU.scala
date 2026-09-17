package li.cil.oc.common.item

import li.cil.oc.common.Tier
import li.cil.oc.util.Rarity
import net.minecraft.world.item.Item

import scala.language.existentials

/**
 * 「APU」（原 `li.cil.oc.common.item.APU`）：CPU 与显卡的合体组件。
 *
 * 1.21.1 迁移要点：
 *  - 删除 `parent: Delegator`；`unlocalizedName` 由基类按 `tier` 自动拼出。
 *  - `override val unlocalizedName = super[Delegate].unlocalizedName + tier` 这种
 *    「先取父实现再拼后缀」的写法在 1.21.1 已不需要（会重复拼接），直接删掉。
 *  - 品质在注册期由 [[APU.tier]] 工厂固定；创造版（`Tier.Three`）按 `Tier.Four` 显示。
 */
class APU(props: Item.Properties, override val tier: Int)
  extends Item(props) with traits.Delegate with traits.ItemTier with traits.CPULike with traits.GPULike {

  override def cpuTier: Int = math.min(Tier.Three, tier + 1)

  override def gpuTier: Int = tier

  override protected def tooltipName: Option[String] = Option(super.unlocalizedName)

  override protected def tooltipData: Seq[Any] = {
    super[CPULike].tooltipData ++ super[GPULike].tooltipData
  }
}

object APU {
  /** 按等级创建物品（`Tier.Three` 为创造版，显示为 T4 品质）。 */
  def tier(t: Int): APU = {
    val rarity = if (t == Tier.Three) Rarity.byTier(Tier.Four) else Rarity.byTier(t)
    new APU(new Item.Properties().rarity(rarity), t)
  }
}
