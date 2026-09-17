package li.cil.oc.integration.util

import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity

/**
 * 「随机死因」死亡消息构造器（对应 1.7.10 的 `DamageSourceWithRandomCause`）。
 *
 * ==1.21.1 迁移说明==
 * 1.7.10 直接继承 `DamageSource` 并覆写 `func_151519_b`（对应 1.21.1 的
 * `DamageSource#getLocalizedDeathMessage`）。1.21.1 的 `DamageSource` 必须用
 * `Holder[DamageType]` 构造、无法这样临时派生，因此改为一个**独立的消息构造器**：
 * 调用方把「死因基名 + 变体数量」交给它，它按同样的规则挑选并本地化
 * `death.attack.<name>.<n>[.player]`。
 *
 * 其他映射：
 *  - `StatCollector.canTranslate(s)` → `Language.getInstance.has(s)`；
 *  - `damagee.func_94060_bK`（AI 目标 / 攻击者）→ `LivingEntity#getKillCredit`；
 *  - `damagee.func_145748_c_`（显示名）→ `Entity#getDisplayName`；
 *  - `damagee.worldObj.rand` → `damagee.level().getRandom`。
 *
 * TODO(port): 恢复纳米机器的「分解」死因时，调用方需要把这里的 `Component` 通过
 * 自定义 `DamageType` 的消息键（或 `LivingEntity#setDeathMessage`）交给原版。
 */
class DamageSourceWithRandomCause(val name: String, val numCauses: Int) {

  /** 按攻击者是否存在，生成 `death.attack.<name>.<n>[.player]` 对应的本地化消息。 */
  def message(damagee: LivingEntity): Component = {
    val killer = damagee.getKillCredit
    val index = damagee.level().getRandom.nextInt(numCauses) + 1
    val format = "death.attack." + name + "." + index
    val withCauseFormat = format + ".player"
    val language = Language.getInstance
    if (killer != null && language.has(withCauseFormat))
      Component.translatable(withCauseFormat, damagee.getDisplayName, killer.getDisplayName)
    else
      Component.translatable(format, damagee.getDisplayName)
  }
}
