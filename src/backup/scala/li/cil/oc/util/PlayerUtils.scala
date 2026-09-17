package li.cil.oc.util

import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

/**
 * 玩家相关工具。
 *
 * 1.21.1 迁移要点：
 *  - 持久化数据：`player.getEntityData` → `player.getPersistentData()`
 *  - `getEntityWorld` → `level()`
 *  - `world.spawnParticle(name, ...)`（字符串粒子名）→ `world.addParticle(ParticleOptions, ...)`，
 *    粒子类型改为注册表对象，见下方 TODO(渲染)
 */
object PlayerUtils {
  def persistedData(player: Player): CompoundTag = {
    val nbt = player.getPersistentData
    if (!nbt.contains(Player.PERSISTED_NBT_TAG)) {
      nbt.put(Player.PERSISTED_NBT_TAG, new CompoundTag())
    }
    nbt.getCompound(Player.PERSISTED_NBT_TAG)
  }

  def spawnParticleAround(player: Player, effectName: String, chance: Double = 1.0): Unit = {
    val rng = player.getRandom
    if (chance >= 1 || rng.nextDouble() < chance) {
      val bounds = player.getBoundingBox
      val x = bounds.minX + (bounds.maxX - bounds.minX) * rng.nextDouble() * 1.5
      val y = bounds.minY + (bounds.maxY - bounds.minY) * rng.nextDouble() * 0.5
      val z = bounds.minZ + (bounds.maxZ - bounds.minZ) * rng.nextDouble() * 1.5
      particleByName(effectName) match {
        case Some(particle) => player.level().addParticle(particle, x, y, z, 0, 0, 0)
        case _ => // 未知粒子名，忽略。
      }
    }
  }

  /**
   * TODO(渲染): 1.21.1 的粒子是注册表对象（`ParticleOptions`），不再按字符串名查找。
   * 这里按旧名（大驼峰 → 下划线小写）在注册表中做一次宽松匹配，未命中则返回 `None`。
   */
  private def particleByName(name: String): Option[net.minecraft.core.particles.ParticleOptions] = {
    val path = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)
    Option(ResourceLocation.tryParse("minecraft:" + path)).flatMap { key =>
      Option(BuiltInRegistries.PARTICLE_TYPE.get(key)).collect {
        case particle: SimpleParticleType => particle
      }
    }
  }
}
