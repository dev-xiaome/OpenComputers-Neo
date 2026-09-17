package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Holder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.{MobEffect, MobEffectInstance}
import net.minecraft.world.entity.player.Player
import net.minecraft.core.registries.BuiltInRegistries

import scala.collection.convert.ImplicitConversionsToScala._

object PotionProvider extends ScalaProvider("c29e4eec-5a46-479a-9b3d-ad0f06da784a") {
  // Lazy to give other mods a chance to register their potions.
  lazy val PotionWhitelist = filterPotions(Settings.get.nanomachinePotionWhitelist)

  def filterPotions[T](list: Iterable[T]) = {
    list.map {
      case name: String => Option(BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(name)))
      case loc: ResourceLocation => Option(BuiltInRegistries.MOB_EFFECT.get(loc))
      // 1.21.1：`MobEffect.byId` 已移除。数字 id 在 1.21 里仍然存在（只是 NBT 序列化改用名字），
      // 因此改从注册表按数字 id 取 Holder 再解包。
      case id: java.lang.Number => BuiltInRegistries.MOB_EFFECT.getHolder(id.intValue()).map(_.value()).toScala
      case _ => None
    }.collect {
      case Some(potion) => potion
    }.toSet
  }

  private def toScala[T](opt: java.util.Optional[T]): Option[T] = if (opt.isPresent) Some(opt.get) else None

  def isPotionEligible(potion: MobEffect) = potion != null && PotionWhitelist.contains(potion)

  override def createScalaBehaviors(player: Player) = {
    // 1.21.1：`Registry#getValues` 已移除，遍历注册表用 `stream()` / `iterator()`。
    // 状态效果现在以 `Holder[MobEffect]` 参与 API，所以顺带包一层 `wrapAsHolder`。
    BuiltInRegistries.MOB_EFFECT.stream().iterator().asScala.
      filter(isPotionEligible).
      map(potion => new PotionBehavior(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(potion), player))
  }

  override def writeBehaviorToNBT(behavior: Behavior, nbt: CompoundTag): Unit = {
    behavior match {
      case potionBehavior: PotionBehavior =>
        // 1.21.1：效果用 `Holder` 表示，取名字要经 `unwrapKey`。
        val key = potionBehavior.effect.unwrapKey().orElse(null)
        if (key != null) {
          nbt.putString("potionId", key.location().toString)
        } else {
          nbt.putString("potionId", "minecraft:empty")
        }
      case _ => // Shouldn't happen, ever.
    }
  }

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = {
    val potionId = nbt.getString("potionId")
    val location = ResourceLocation.tryParse(potionId)
    // 未知 / 非法 id 时保持与旧实现一致：给一个 null，让行为本身成为空操作。
    val holder: Holder[MobEffect] =
      if (location == null) null
      else BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null)
    new PotionBehavior(holder, player)
  }

  class PotionBehavior(val effect: Holder[MobEffect], player: Player) extends AbstractBehavior(player) {
    final val Duration = 600

    def amplifier(player: Player) = api.Nanomachines.getController(player).getInputCount(this) - 1

    override def getNameHint: String = effect.value().getDescriptionId.stripPrefix("effect.")

    override def onDisable(reason: DisableReason): Unit = {
      player.removeEffect(effect)
    }

    override def update(): Unit = {
      player.addEffect(new MobEffectInstance(effect, Duration, amplifier(player), true, Settings.get.enableNanomachinePfx))
    }
  }

}
