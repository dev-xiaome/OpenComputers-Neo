package li.cil.oc.common.nanomachines

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.damagesource.{DamageSource, DamageType, DamageTypes}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level

/**
 * 带「随机死因」的伤害来源。
 *
 * 1.7.10 的 `li.cil.oc.integration.util.DamageSourceWithRandomCause` 直接继承
 * `DamageSource(name)` 并覆写 `func_151519_b`（死亡消息）。1.21.1 的伤害来源
 * 改为数据驱动的 [[DamageType]]，构造 [[DamageSource]] 需要 `Holder[DamageType]`，
 * 因此这里改为从给定 [[Level]] 的注册表里取原版 `minecraft:generic`：
 *
 *  - `getLocalizedDeathMessage` 按 `death.attack.<msgId>.<n>`（旧版命名规则）随机挑一条
 *    消息键；带攻击者时先试 `...player`。语言文件里没有对应键时退回伤害类型自带消息。
 *
 * 由于 `minecraft:generic` 本身已经「无视护甲、无视吸收」，旧版的
 * `setDamageBypassesArmor()` / `setDamageIsAbsolute()` 只是保留成空的链式调用。
 */
class DamageSourceWithRandomCause(level: Level, numCauses: Int) extends DamageSource(
  DamageSourceWithRandomCause.typeFor(level)) {

  /** 用于日志的诊断名称（旧版是伤害类型的名字）。 */
  val legacyName: String = "opencomputers_neo:nanomachines"

  override def getLocalizedDeathMessage(entity: LivingEntity): Component = {
    val random = entity.getRandom
    val msgId = getMsgId
    val format = "death.attack." + msgId + "." + (random.nextInt(math.max(1, numCauses)) + 1)
    val withCauseFormat = format + ".player"

    val attacker = getEntity
    if (attacker != null && net.minecraft.locale.Language.getInstance.has(withCauseFormat)) {
      Component.translatable(withCauseFormat, entity.getDisplayName, attacker.getDisplayName)
    }
    else if (net.minecraft.locale.Language.getInstance.has(format)) {
      Component.translatable(format, entity.getDisplayName)
    }
    else {
      // 语言文件里没有我们的随机死因键，交给原版默认文案。
      super.getLocalizedDeathMessage(entity)
    }
  }

  /** 旧 API 兼容：1.21.1 由伤害类型标签决定是否无视护甲，这里仅返回自身以便链式调用。 */
  def setDamageBypassesArmor(): DamageSourceWithRandomCause = this

  /** 旧 API 兼容：见 [[setDamageBypassesArmor]]。 */
  def setDamageIsAbsolute(): DamageSourceWithRandomCause = this
}

object DamageSourceWithRandomCause {
  /**
   * 解析伤害类型 Holder。
   *
   * 1.21.1 的 `DamageSource` 构造器必须拿到 `Holder[DamageType]`；正常路径是从
   * 当前世界的注册表里取。若传进来的 `level` 为 `null`（例如 `object` 初始化期
   * 还没有世界），退回原版静态注册表的通用伤害类型，保证不会 NPE。
   */
  private def typeFor(level: Level): net.minecraft.core.Holder[DamageType] = {
    if (level != null) {
      level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC)
    }
    else {
      net.minecraft.core.registries.BuiltInRegistries.DAMAGE_TYPE.getHolder(DamageTypes.GENERIC).orElseThrow()
    }
  }

  /** 保留旧签名的便捷入口；`name` 只用于日志。 */
  def apply(level: Level, name: String, numCauses: Int): DamageSourceWithRandomCause = {
    if (!warned.contains(name)) {
      warned += name
      li.cil.oc.OpenComputers.log.debug(
        s"DamageSourceWithRandomCause($name, $numCauses) 在 1.21.1 使用原版 minecraft:generic 伤害类型。")
    }
    new DamageSourceWithRandomCause(level, numCauses)
  }

  private var warned = Set.empty[String]
}
