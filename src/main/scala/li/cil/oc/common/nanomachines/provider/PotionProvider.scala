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

import scala.collection.convert.ImplicitConversionsToScala._

object PotionProvider extends ScalaProvider("c29e4eec-5a46-479a-9b3d-ad0f06da784a") {
  // Lazy to give other mods a chance to register their potions.
  lazy val PotionWhitelist = filterPotions(Settings.get.nanomachinePotionWhitelist)

  def filterPotions[T](list: Iterable[T]) = {
    list.map {
      case name: String => Option(BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(name)))
      case loc: ResourceLocation => Option(BuiltInRegistries.MOB_EFFECT.get(loc))
      case id: java.lang.Number => Option(BuiltInRegistries.MOB_EFFECT.getHolder(id.intValue()).map(v => v.value()).get())
      case _ => None
    }.collect {
      case Some(potion) => potion
    }.toSet
  }

  def isPotionEligible(potion: MobEffect) = potion != null && PotionWhitelist.contains(potion)

  override def createScalaBehaviors(player: Player) = {
    BuiltInRegistries.MOB_EFFECT.asLookup().filterElements(isPotionEligible).listElements().map(new PotionBehavior(_, player)).toList
  }

  override def writeBehaviorToNBT(behavior: Behavior, nbt: CompoundTag): Unit = {
    behavior match {
      case potionBehavior: PotionBehavior =>
        val key = potionBehavior.effect.key()
        if (key != null) {
          nbt.putString("potionId", key.toString)
        } else {
          nbt.putString("potionId", "minecraft:empty")
        }
      case _ => // Shouldn't happen, ever.
    }
  }

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = {
    val potionId = nbt.getString("potionId")
    new PotionBehavior(BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.tryParse(potionId)).orElseThrow(), player)
  }

  class PotionBehavior(val effect: Holder.Reference[MobEffect], player: Player) extends AbstractBehavior(player) {
    final val Duration = 600

    def amplifier(player: Player) = api.Nanomachines.getController(player).getInputCount(this) - 1

    override def getNameHint: String = effect.value.getDescriptionId.stripPrefix("effect.")

    override def onDisable(reason: DisableReason): Unit = {
      player.removeEffect(effect)
    }

    override def update(): Unit = {
      player.addEffect(new MobEffectInstance(effect, Duration, amplifier(player), true, Settings.get.enableNanomachinePfx))
    }
  }

}
