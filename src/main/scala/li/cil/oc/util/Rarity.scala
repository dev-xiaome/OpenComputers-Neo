package li.cil.oc.util

import net.minecraft.world.item.{Rarity => MCRarity}

object Rarity {
  import MCRarity._
  private val lookup = Array(MCRarity.COMMON, MCRarity.UNCOMMON, MCRarity.RARE, MCRarity.EPIC)

  // 移植降级说明（请保留）：
  // Forge 1.20 的 Rarity.create("legendary", ChatFormatting.GOLD) 会动态造出一个新的
  // 枚举取值。NeoForge 1.21.1 把 Rarity 变成了封闭枚举（实现 IExtensibleEnum），新增取值
  // 必须走 META-INF/enumextensions.json + EnumProxy 的 ASM 扩展机制，而且该机制要求调用点
  // 持有的是 EnumProxy[Rarity] 而不是 Rarity 本身 —— 这会改变 common/** 里几十处
  // `OCRarity.LEGENDARY` 的静态类型（那部分不在本次移植范围内，不能改）。
  // 因此这里暂时退化为 EPIC：只影响物品 tooltip 名称的颜色（原本是金色），
  // 不影响任何功能与存档数据。等 enumextensions.json 方案落地后再恢复独有颜色。
  val LEGENDARY: MCRarity = MCRarity.EPIC

  def byTier(tier: Int) = lookup(tier max 0 min (lookup.length - 1))
}
