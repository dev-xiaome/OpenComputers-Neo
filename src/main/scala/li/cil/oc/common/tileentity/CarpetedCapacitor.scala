package li.cil.oc.common.tileentity

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.{Ocelot, Sheep}
import net.minecraft.world.level.block.state.BlockState

import scala.jdk.CollectionConverters._

/**
 * 地毯电容组（对应 1.7.10 的 `common.tileentity.CarpetedCapacitor`）。
 *
 * 在电容组上方「站了一群羊 / 豹猫」时把这些动物变成发电机：每 20 刻采样一次上方的
 * 生物，按种类累加发电量注入网络；同时按配置的概率对其中一只造成 1 点伤害并弹开
 * （「电击」，之后一分钟内不再触发）。
 *
 * 纹理：下 = CapacitorTop，上 = CarpetedCapacitorTop，其它四面 = CarpetedCapacitorSide。
 *
 * ==1.21.1 迁移要点==
 *  - 构造函数仍是 `(pos, state)`，直接传给父类 [[Capacitor]]。
 *  - `EntityLivingBase` → `LivingEntity`；`EntitySheep` → `Sheep`；`EntityOcelot` → `Ocelot`。
 *  - `world.getEntitiesWithinAABB(classOf[T], aabb)` → `world.getEntitiesOfClass(classOf[T], aabb)`
 *    （返回 `java.util.List`，需要 `.asScala`）。
 *  - `entity.isEntityAlive` → `entity.isAlive`。
 *  - `DamageSource.generic` → `world.damageSources().generic()`（1.21.1 的伤害源由 `Level` 提供）。
 *  - `entity.attackEntityFrom(src, n)` → `entity.hurt(src, n)`；
 *    `setRevengeTarget` → `setLastHurtByMob`；
 *    `knockBack(ent, force, ratioX, ratioZ)` 在 1.21.1 变成了 `knockback(strength, x, z)`，
 *    这里用 `knockback(0.25, 0, 0)` 近似其「电一下弹开」的语义。
 *  - `world.getTotalWorldTime` → `world.getGameTime`。
 *  - `updateEntity()` → [[traits.TileEntity.tick]]（覆写时先调 `super.tick()`）。
 */
class CarpetedCapacitor(pos: BlockPos, state: BlockState)
  extends Capacitor(pos, state) {

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Battery",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "CarpetedCapBank3x",
    DeviceAttribute.Capacity -> maxCapacity.toString
  )

  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  override def canUpdate: Boolean = true

  private val rng = scala.util.Random
  private val chance: Double = Settings.get.carpetDamageChance
  private var nextChanceTime: Long = 0

  private def energyFromGroup(entities: Set[LivingEntity], power: Double): Double = {
    if (entities.size < 2) return 0
    def tryDamageOne(): Unit = {
      for (ent <- entities) {
        if (rng.nextDouble() < chance) {
          ent.hurt(world.damageSources().generic(), 1f)
          ent.setLastHurtByMob(ent) // panic
          // 1.7.10：`ent.knockBack(ent, 0, .25, 0)`；1.21.1 的等价签名是
          // `knockback(strength, x, z)`，这里给一个向上的小弹跳来还原「被电到」的表现。
          ent.knockback(0.25, 0, 0)
          // wait a minute before the next possible shock
          nextChanceTime = world.getGameTime + (20 * 60)
          return
        }
      }
    }
    if (chance > 0 && nextChanceTime < world.getGameTime) {
      tryDamageOne()
    }
    power
  }

  override def tick(): Unit = {
    super.tick()
    if (node != null && (world.getGameTime + hashCode) % 20 == 0) {
      val entities = world.getEntitiesOfClass(classOf[LivingEntity], capacitorPowerBounds).
        asScala.
        filter(entity => entity.isAlive).
        toSet
      val sheepPower = energyFromGroup(entities.filter(_.isInstanceOf[Sheep]), Settings.get.sheepPower)
      val ocelotPower = energyFromGroup(entities.filter(_.isInstanceOf[Ocelot]), Settings.get.ocelotPower)
      val totalPower = sheepPower + ocelotPower
      if (totalPower > 0) {
        node.changeBuffer(totalPower)
      }
    }
  }

  private def capacitorPowerBounds = position.offset(Direction.UP).bounds
}
