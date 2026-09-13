package li.cil.oc.integration.util

import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.chat.Component
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.network.chat.Component
import net.minecraft.util.StatCollector

class DamageSourceWithRandomCause(name: String, numCauses: Int) extends DamageSource(name) {
  override def func_151519_b(damagee: LivingEntity): Component = {
    val damager = damagee.func_94060_bK
    val format = "death.attack." + damageType + "." + (damagee.worldObj.rand.nextInt(numCauses) + 1)
    val withCauseFormat = format + ".player"
    if (damager != null && StatCollector.canTranslate(withCauseFormat))
      new ChatComponentTranslation(withCauseFormat, damagee.func_145748_c_, damager.func_145748_c_)
    else
      new ChatComponentTranslation(format, damagee.func_145748_c_)
  }
}
