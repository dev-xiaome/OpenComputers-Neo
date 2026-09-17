package li.cil.oc.common.item

import java.util

import li.cil.oc.Localization
import li.cil.oc.util.{UpgradeExperience => ExperienceUtil}
// `hasTag()` / `getTag()` 是补回 1.7.10 `ItemStack` NBT 访问的隐式扩展；
// Scala 2.13 不会把 `li.cil.oc` 包对象里的隐式类暴露给子包，因此必须显式引入。
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「经验升级」物品（对应 OCCE 的 `li.cil.oc.common.item.UpgradeExperience`）。
 *
 * 1.7.10 的这个类没有 tooltip；OCCE 补上了「当前经验等级」的提示，这里按 OCCE 移植。
 * 等级换算统一走 [[li.cil.oc.util.UpgradeExperience]]，与 `server.component.Robot`
 * 用的是同一份公式。
 */
class UpgradeExperience(props: Item.Properties) extends Item(props) with traits.Delegate with traits.ItemTier {

  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[String]): Unit = {
    if (stack != null && stack.hasTag()) {
      // 组件数据统一挂在 `<namespace>data` 下，与物品栏 / 组件层保持同一处读取。
      val nbt = li.cil.oc.integration.opencomputers.Item.dataTag(stack)
      val experience = ExperienceUtil.getExperience(nbt)
      val level = ExperienceUtil.calculateLevelFromExperience(experience)
      val reportedLevel = ExperienceUtil.calculateExperienceLevel(level, experience)
      tooltip.add(Localization.Tooltip.ExperienceLevel(reportedLevel))
    }
  }
}
