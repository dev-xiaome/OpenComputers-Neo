package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance

import scala.jdk.CollectionConverters._

object PotionProvider extends ScalaProvider("c29e4eec-5a46-479a-9b3d-ad0f06da784a") {
  // Lazy to give other mods a chance to register their potions.
  lazy val PotionWhitelist = filterPotions(Settings.get.nanomachinePotionWhitelist)

  def filterPotions[T](list: Iterable[T]) = {
    list.map {
      case name: String => Potion.potionTypes.find(p => p != null && p.getName == name)
      case id: java.lang.Number if id.intValue() >= 0 && id.intValue() < Potion.potionTypes.length => Option(Potion.potionTypes(id.intValue()))
      case _ => None
    }.collect {
      case Some(potion) => potion
    }.toSet
  }

  def isPotionEligible(potion: Potion) = potion != null && PotionWhitelist.contains(potion)

  override def createScalaBehaviors(player: Player) = {
    Potion.potionTypes.filter(isPotionEligible).map(new PotionBehavior(_, player))
  }

  override def writeBehaviorToNBT(behavior: Behavior, nbt: CompoundTag): Unit = {
    behavior match {
      case potionBehavior: PotionBehavior =>
        nbt.putInt("potionId", potionBehavior.potion.id)
      case _ => // Shouldn't happen, ever.
    }
  }

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag) = {
    val potionId = nbt.getInteger("potionId")
    new PotionBehavior(Potion.potionTypes(potionId), player)
  }

  class PotionBehavior(val potion: Potion, player: Player) extends AbstractBehavior(player) {
    final val Duration = 600

    def amplifier(player: Player) = api.Nanomachines.getController(player).getInputCount(this) - 1

    override def getNameHint: String = potion.getName.stripPrefix("potion.")

    override def onDisable(reason: DisableReason): Unit = {
      player.removePotionEffect(potion.id)
    }

    override def update(): Unit = {
      player.addPotionEffect(new PotionEffect(potion.id, Duration, if (Settings.get.enableNanomachinePfx) amplifier(player) else -1))
    }
  }

}
