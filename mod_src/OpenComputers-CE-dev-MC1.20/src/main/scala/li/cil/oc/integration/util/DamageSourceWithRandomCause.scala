package li.cil.oc.integration.util

import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.damagesource.{DamageSource, DamageType}
import net.minecraft.world.level.Level

class DamageSourceWithRandomCause(val key: ResourceKey[DamageType], val numCauses: Int, level: Level)
  extends DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key)) {

  override def getLocalizedDeathMessage(damagee: LivingEntity): Component = {
    val damager = damagee.getKillCredit
    val msgId = this.`type`().msgId()
    val randomIndex = damagee.getRandom.nextInt(numCauses) + 1

    val format = s"death.attack.$msgId.$randomIndex"
    val withCauseFormat = s"$format.player"

    if (damager != null) {
      Component.translatable(withCauseFormat, damagee.getDisplayName, damager.getDisplayName)
    } else {
      Component.translatable(format, damagee.getDisplayName)
    }
  }
}