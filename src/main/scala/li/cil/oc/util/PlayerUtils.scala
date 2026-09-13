package li.cil.oc.util

import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag

object PlayerUtils {
  def persistedData(player: Player): CompoundTag = {
    val nbt = player.getEntityData
    if (!nbt.contains(Player.PERSISTED_NBT_TAG)) {
      nbt.put(Player.PERSISTED_NBT_TAG, new CompoundTag())
    }
    nbt.getCompound(Player.PERSISTED_NBT_TAG)
  }

  def spawnParticleAround(player: Player, effectName: String, chance: Double = 1.0): Unit = {
    val rng = player.getEntityWorld.rand
    if (chance >= 1 || rng.nextDouble() < chance) {
      val bounds = player.boundingBox
      val x = bounds.minX + (bounds.maxX - bounds.minX) * rng.nextDouble() * 1.5
      val y = bounds.minY + (bounds.maxY - bounds.minY) * rng.nextDouble() * 0.5
      val z = bounds.minZ + (bounds.maxZ - bounds.minZ) * rng.nextDouble() * 1.5
      player.getEntityWorld.spawnParticle(effectName, x, y, z, 0, 0, 0)
    }
  }
}
