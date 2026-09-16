package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.util.PlayerUtils
import net.minecraft.core.Registry
import net.minecraft.core.particles.{ParticleType, ParticleTypes, SimpleParticleType}
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player

object ParticleProvider extends ScalaProvider("b48c4bbd-51bb-4915-9367-16cff3220e4b") {
  final val ParticleTypeList: Array[SimpleParticleType] = Array(
    ParticleTypes.FIREWORK,
    ParticleTypes.SMOKE,
    ParticleTypes.WITCH,
    ParticleTypes.NOTE,
    ParticleTypes.ENCHANT,
    ParticleTypes.FLAME,
    ParticleTypes.LAVA,
    ParticleTypes.SPLASH,
    ParticleTypes.ITEM_SLIME,
    ParticleTypes.HEART,
    ParticleTypes.HAPPY_VILLAGER
  )

  override def createScalaBehaviors(player: Player): Iterable[Behavior] = ParticleTypeList.map(new ParticleBehavior(_, player))

  // TODO: replace NBT particle ids with ResourceLocation, needs additional work to migrate old saves

  override def writeBehaviorToNBT(behavior: Behavior, nbt: CompoundTag): Unit = {
    behavior match {
      case particles: ParticleBehavior =>
        nbt.putInt("effectName", BuiltInRegistries.PARTICLE_TYPE.asInstanceOf[Registry[ParticleType[_]]].getId(particles.effectType))
      case _ => // Wat.
    }
  }

  override def readBehaviorFromNBT(player: Player, nbt: CompoundTag): Behavior = {
    val effectType = BuiltInRegistries.PARTICLE_TYPE.asInstanceOf[Registry[ParticleType[_]]].getHolder(nbt.getInt("effectName")).orElseThrow().value()
    new ParticleBehavior(effectType.asInstanceOf[SimpleParticleType], player)
  }

  class ParticleBehavior(var effectType: SimpleParticleType, player: Player) extends AbstractBehavior(player) {
    override def getNameHint = "particles." + BuiltInRegistries.PARTICLE_TYPE.getKey(effectType).getPath

    override def update(): Unit = {
      val world = player.level
      if (world.isClientSide && Settings.get.enableNanomachinePfx) {
        PlayerUtils.spawnParticleAround(player, effectType, api.Nanomachines.getController(player).getInputCount(this) * 0.25)
      }
    }
  }
}
