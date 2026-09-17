package li.cil.oc.util

import li.cil.oc.Settings
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/**
 * 机器人经验升级的数值计算（对应 OCCE 的 `li.cil.oc.util.UpgradeExperience`）。
 *
 * 1.7.10 里这套逻辑直接写在 `common.item.UpgradeExperience` 的 tooltip 里，OCCE 把它抽成了
 * 独立工具对象，这样 `server.component.Robot` 与物品 tooltip 能用同一份公式。本文件按 OCCE
 * 原样移植。
 *
 * 注意：1.21.1 的 `ItemStack` 没有 `hasTag`/`getTag`，这里用的是本项目补回的隐式扩展
 * （[[ItemStackNBTExtensions]]，底层是自定义数据组件 `opencomputers_neo:nbt`）。
 */
object UpgradeExperience {

  final val XpTag = Settings.namespace + "xp"

  def getExperience(nbt: CompoundTag): Double = nbt.getDouble(XpTag) max 0

  def getExperience(stack: ItemStack): Double =
    if (stack == null || !stack.hasTag()) 0 else getExperience(stack.getTag())

  def setExperience(nbt: CompoundTag, experience: Double): Unit = nbt.putDouble(XpTag, experience)

  def xpForLevel(level: Int): Double =
    if (level == 0) 0
    else Settings.get.baseXpToLevel + Math.pow(level * Settings.get.constantXpGrowth, Settings.get.exponentialXpGrowth)

  def calculateExperienceLevel(level: Int, experience: Double): Double = {
    val xpNeeded = xpForLevel(level + 1) - xpForLevel(level)
    val xpProgress = math.max(0, experience - xpForLevel(level))
    level + xpProgress / xpNeeded
  }

  def calculateLevelFromExperience(experience: Double): Int =
    math.min((Math.pow(experience - Settings.get.baseXpToLevel, 1 / Settings.get.exponentialXpGrowth) / Settings.get.constantXpGrowth).toInt, 30)
}
