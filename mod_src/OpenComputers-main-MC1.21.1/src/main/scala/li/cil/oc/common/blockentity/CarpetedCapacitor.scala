package li.cil.oc.common.blockentity

import java.util
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Cat
import net.minecraft.world.entity.animal.Ocelot
import net.minecraft.world.entity.animal.Sheep
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension

import scala.jdk.CollectionConverters._

class CarpetedCapacitor(pos: BlockPos, state: BlockState) 
  extends Capacitor(BlockEntityTypes.CARPETED_CAPACITOR.get(), pos, state) with traits.Tickable with IBlockEntityExtension {
  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Battery",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "CarpetedCapBank3x",
    DeviceAttribute.Capacity -> maxCapacity.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  private def _level: Level = getLevel
  private val rng = scala.util.Random
  private val chance: Double = Settings.get.carpetDamageChance
  private var nextChanceTime: Long = 0

  private def energyFromGroup(entities: Set[LivingEntity], power: Double): Double = {
    if (entities.size < 2) return 0
    def tryDamageOne(): Unit = {
      for (ent <- entities) {
        if (rng.nextDouble() < chance) {
          ent.hurt(level.damageSources().generic(), 1)
          ent.setLastHurtByMob(ent) // panic
          ent.knockback(0, .25, 0)
          // wait a minute before the next possible shock
          nextChanceTime = _level.getGameTime + (20 * 60)
          return
        }
      }
    }
    if (chance > 0 && nextChanceTime < _level.getGameTime) {
      tryDamageOne()
    }
    power
  }

  override def updateEntity(): Unit = {
    if (node != null && (_level.getGameTime + hashCode) % 20 == 0) {
      val entities = _level.getEntitiesOfClass(classOf[LivingEntity], capacitorPowerBounds)
        .asScala
        .filter(_.isAlive)
        .toSet
      val sheepPower = energyFromGroup(entities.filter(_.isInstanceOf[Sheep]), Settings.get.sheepPower)
      val ocelotPower = energyFromGroup(entities.filter(e => e.isInstanceOf[Ocelot] || e.isInstanceOf[Cat]), Settings.get.ocelotPower)
      val totalPower = sheepPower + ocelotPower
      if (totalPower > 0) {
        node.changeBuffer(totalPower)
      }
    }
  }

  private def capacitorPowerBounds = position.offset(Direction.UP).bounds
}
