package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.integration.util.DamageSourceWithRandomCause
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.world.damagesource.{DamageSource, DamageType}
import net.minecraft.world.level.Level

object HungryProvider extends ScalaProvider("d697c24a-014c-4773-a288-23084a59e9e8") {
  final val FillCount = 10 // Create a bunch of these to have a higher chance of one being picked / available.

  val HungryDamageKey: ResourceKey[DamageType] = ResourceKey.create(
    Registries.DAMAGE_TYPE,
    ResourceLocation.fromNamespaceAndPath("opencomputers", "nanomachines_hungry")
  )

  override def createScalaBehaviors(player: Player): Iterable[Behavior] = Iterable.fill(FillCount)(new HungryBehavior(player))

  override protected def readBehaviorFromNBT(player: Player, nbt: CompoundTag): Behavior = new HungryBehavior(player)

  class HungryBehavior(player: Player) extends AbstractBehavior(player) {
    override def onDisable(reason: DisableReason): Unit = {
      if (reason == DisableReason.OutOfEnergy) {
        player.hurt(new DamageSourceWithRandomCause(HungryDamageKey, 3, player.level()), Settings.get.nanomachinesHungryDamage)
        api.Nanomachines.getController(player).changeBuffer(Settings.get.nanomachinesHungryEnergyRestored)
      }
    }
  }
}
