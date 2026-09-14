package li.cil.oc.common.nanomachines

import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.damagesource.{DamageSource, DamageType, DamageTypes}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level

/**
 * 带「随机死因」的伤害来源。
 *
 * 1.7.10 的 `li.cil.oc.integration.util.DamageSourceWithRandomCause` 直接继承
 * `DamageSource(name)` 并覆写 `func_151519_b`（死亡消息）。1.21.1 的伤害来源
 * 改为数据驱动的 [[DamageType]]，构造 [[DamageSource]] 必须拿到 `Holder[DamageType]`，
 * 因此这里要求调用方传入「伤害发生所在的世界」，从中取原版 `minecraft:generic`：
 *
 *  - `getLocalizedDeathMessage` 按 `death.attack.<msgId>.<n>`（旧版命名规则）随机挑一条
 *    消息键；带攻击者时先试 `...player`。语言文件里没有对应键时退回伤害类型自带消息。
 *
 * 注意：1.21.1 已没有静态的「伤害类型注册表」单例
 * （`BuiltInRegistries` 里不存在 `DAMAGE_TYPE`），所以**不能**用 `null` 世界构造；
 * 由于伤害类型是同步到客户端的数据包对象，这里统一在服务端按玩家所在世界构造。
 *
 * 由于 `minecraft:generic` 本身已经无视护甲与吸收，旧版的
 * `setDamageBypassesArmor()` / `setDamageIsAbsolute()` 只是保留成空的链式调用。
 */
class DamageSourceWithRandomCause(level: Level, val legacyName: String, numCauses: Int) extends DamageSource(
  DamageSourceWithRandomCause.typeFor(level)) {

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
   * 解析伤害类型 Holder：始终从传入世界的注册表里取 `minecraft:generic`。
   *
   * 传 `null` 世界会退化为「用当前服务端主世界的注册表」；
   * 若连服务端都没有（例如单元测试），则退回 `DamageTypes.GENERIC` 的直接 Holder，
   * 保证不会在这里抛异常。
   */
  private def typeFor(level: Level): Holder[DamageType] = {
    val resolved = if (level != null) level else {
      val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer
      if (server == null) null else server.overworld()
    }
    if (resolved != null) {
      resolved.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC)
    }
    else {
      // 没有世界可用：用原版伤害类型的静态 Holder（`DamageTypes.GENERIC` 自带 ResourceKey）。
      Holder.direct(new DamageType("generic", 0f))
    }
  }

  /** 便捷入口；`name` 只用于日志。 */
  def apply(level: Level, name: String, numCauses: Int): DamageSourceWithRandomCause = {
    if (!warned.contains(name)) {
      warned += name
      li.cil.oc.OpenComputers.log.debug(
        s"DamageSourceWithRandomCause($name, $numCauses) 在 1.21.1 使用原版 minecraft:generic 伤害类型。")
    }
    new DamageSourceWithRandomCause(level, name, numCauses)
  }

  private var warned = Set.empty[String]
}
