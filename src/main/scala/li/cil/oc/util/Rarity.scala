package li.cil.oc.util

/**
 * 品质（rarity）映射。
 *
 * 1.21.1 迁移要点：
 *  - `net.minecraft.item.EnumRarity` → `net.minecraft.world.item.Rarity`，
 *    枚举常量变为 `COMMON/UNCOMMON/RARE/EPIC`
 *  - 本文件所在包内已经有一个 `object Rarity`，若 `import net.minecraft.world.item.Rarity`
 *    会被同名定义永久遮蔽，因此改用类型别名引用原版枚举
 */
object Rarity {
  // 注意：不能写 `import net.minecraft.world.item.Rarity`（会被本 object 永久遮蔽），
  // 也不能用 `val rarity = net.minecraft.world.item.Rarity`（Java 枚举不是值），
  // 因此这里写全限定名。
  private val lookup: Array[net.minecraft.world.item.Rarity] = Array(
    net.minecraft.world.item.Rarity.COMMON,
    net.minecraft.world.item.Rarity.UNCOMMON,
    net.minecraft.world.item.Rarity.RARE,
    net.minecraft.world.item.Rarity.EPIC)

  def byTier(tier: Int): net.minecraft.world.item.Rarity =
    lookup(tier max 0 min (lookup.length - 1))
}
