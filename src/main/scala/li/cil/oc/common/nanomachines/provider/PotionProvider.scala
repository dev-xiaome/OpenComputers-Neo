package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.{MobEffect, MobEffectInstance}
import net.minecraft.world.entity.player.Player

import scala.jdk.CollectionConverters._

/**
 * 状态效果（旧称「药水」）行为提供者。
 *
 * 1.21.1 迁移要点（这里改动较大，因为底层概念变了）：
 *  - 1.7.10：`Potion.potionTypes`（定长数组 + 数字 id）→ 1.21.1：`MobEffect` 是**注册表对象**
 *    （`BuiltInRegistries.MOB_EFFECT`），效果以 `Holder[MobEffect]` 形式使用；
 *  - `PotionEffect(id, duration, amplifier)` → `MobEffectInstance(Holder[MobEffect], duration, amplifier)`；
 *  - `player.addPotionEffect` → `player.addEffect`；`removePotionEffect(id)` → `removeEffect(holder)`；
 *  - 名称：旧版是翻译键（`potion.moveSpeed`），1.21.1 用注册名（`minecraft:speed`）。
 *    白名单（`Settings.get.nanomachinePotionWhitelist`）因此做一次归一化匹配：
 *    既接受新式 `minecraft:speed`，也接受旧式 `potion.moveSpeed`（只按旧名的后缀做宽松匹配）。
 *  - 序列化：旧版写 `potionId`（数字），1.21.1 改为写 `effect`（注册名），
 *    并保留读取旧 `potionId` 的能力（迁移期兼容，见 [[readBehaviorFromNBT]]）。
 */
object PotionProvider extends ScalaProvider("c29e4eec-5a46-479a-9b3d-ad0f06da784a") {
  // 延迟求值，给其它模组注册效果的机会。
  lazy val PotionWhitelist = filterPotions(Settings.get.nanomachinePotionWhitelist.asScala.toSeq)

  /**
   * 旧版名称归一化：`potion.moveSpeed` → `movespeed`。
   *
   * 1.21.1 的注册名（`minecraft:speed` 等）与旧翻译键并不是一一对应的字符串，
   * 因此这里只做「去掉命名空间与 `potion.` 前缀、去掉下划线、转小写」的宽松比较，
   * 命中不了的效果会在下方 [[legacyAliases]] 里补齐。
   */
  private def normalize(name: Any): Option[String] = name match {
    case s: String if s.nonEmpty =>
      val trimmed = s.trim.toLowerCase(java.util.Locale.ROOT)
      val withoutNamespace = if (trimmed.contains(':')) trimmed.substring(trimmed.indexOf(':') + 1) else trimmed
      val withoutPrefix = if (withoutNamespace.startsWith("potion.")) withoutNamespace.substring("potion.".length) else withoutNamespace
      Some(withoutPrefix.replace("_", ""))
    case _ => None
  }

  /** 旧翻译键后缀 → 1.21.1 注册路径。 */
  private val legacyAliases: Map[String, String] = Map(
    "movespeed" -> "speed",
    "digspeed" -> "haste",
    "digslowdown" -> "mining_fatigue",
    "damageboost" -> "strength",
    "moveslowdown" -> "slowness",
    "confusion" -> "nausea",
    "harm" -> "instant_damage",
    "heal" -> "instant_health",
    "jump" -> "jump_boost",
    "resistance" -> "resistance",
    "fireresistance" -> "fire_resistance",
    "waterbreathing" -> "water_breathing",
    "nightvision" -> "night_vision",
    "absorption" -> "absorption",
    "blindness" -> "blindness",
    "hunger" -> "hunger",
    "poison" -> "poison",
    "weakness" -> "weakness",
    "wither" -> "wither",
    "regeneration" -> "regeneration",
    "invisibility" -> "invisibility",
    "slowfalling" -> "slow_falling",
    "healthboost" -> "health_boost",
    "saturation" -> "saturation",
    "luck" -> "luck",
    "unluck" -> "unluck",
    "levitation" -> "levitation",
    "glowing" -> "glowing",
    "conduitpower" -> "conduit_power",
    "dolphinsgrace" -> "dolphins_grace",
    "badomen" -> "bad_omen",
    "heroofthevillage" -> "hero_of_the_village",
    "darkness" -> "darkness"
  )

  /** 把白名单条目（名称或旧数字 id）解析为注册表中的效果集合。 */
  def filterPotions(list: Iterable[Any]): Set[Holder[MobEffect]] = {
    val entries = list.flatMap {
      case s: String => normalize(s)
      case n: java.lang.Number => Some("#" + n.intValue()) // 旧数字 id，下面按注册表索引匹配。
      case _ => None
    }.toSet

    BuiltInRegistries.MOB_EFFECT.holders().iterator().asScala.filter { holder =>
      val key = holder.unwrapKey().orElse(null)
      if (key == null) false
      else {
        val path = key.getPath
        val id = BuiltInRegistries.MOB_EFFECT.getId(holder.value())
        entries.contains(path.replace("_", "")) ||
          entries.contains(key.toString) ||
          entries.contains("#" + id) ||
          legacyAliases.get(path).exists(alias => entries.contains(alias.replace("_", "")))
      }
    }.toSet
  }

  def isPotionEligible(potion: Holder[MobEffect]): Boolean =
    potion != null && PotionWhitelist.contains(potion)

  override def createScalaBehaviors(player: Player) = {
    PotionWhitelist.toSeq.map(new PotionBehavior(_, player))
  }

  override def writeBehaviorToNBT(behavior: Behavior, nbt: CompoundTag): Unit = {
    behavior match {
      case potionBehavior: PotionBehavior =>
        potionBehavior.potion.unwrapKey().ifPresent(key => nbt.putString("effect", key.location().toString))
      case _ => // 不应发生。
    }
  }

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag): Behavior = {
    // 新格式优先。
    if (nbt.contains("effect")) {
      val key = ResourceLocation.tryParse(nbt.getString("effect"))
      val holder = if (key == null) null else BuiltInRegistries.MOB_EFFECT.getHolder(key).orElse(null)
      if (holder != null) return new PotionBehavior(holder, player)
    }
    // 旧格式兼容：`potionId` 是 1.7.10 的 `Potion.potionTypes` 数组下标。
    if (nbt.contains("potionId")) {
      val legacyId = nbt.getInt("potionId")
      BuiltInRegistries.MOB_EFFECT.holders().iterator().asScala.find(h => BuiltInRegistries.MOB_EFFECT.getId(h.value()) == legacyId) match {
        case Some(holder) => return new PotionBehavior(holder, player)
        case _ =>
      }
    }
    null
  }

  class PotionBehavior(val potion: Holder[MobEffect], player: Player) extends AbstractBehavior(player) {
    final val Duration = 600

    def amplifier(player: Player) = api.Nanomachines.getController(player).getInputCount(this) - 1

    override def getNameHint: String = {
      val key = potion.unwrapKey().orElse(null)
      if (key == null) "" else key.location().toString
    }

    override def onDisable(reason: DisableReason): Unit = {
      player.removeEffect(potion)
    }

    override def update(): Unit = {
      player.addEffect(new MobEffectInstance(potion, Duration, if (Settings.get.enableNanomachinePfx) amplifier(player) else -1))
    }
  }

}
