package li.cil.oc.server.component

import java.util

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab
import li.cil.oc.util.BlockPosition
import net.minecraft.world.level.biome.Biomes
import net.minecraft.core.Direction

import scala.jdk.CollectionConverters._

class UpgradeSolarGenerator(val host: EnvironmentHost) extends prefab.ManagedEnvironment with DeviceInfo {
  override val node = Network.newNode(this, Visibility.Network).
    withConnector().
    create()

  var ticksUntilCheck = 0

  var isSunShining = false

  private final lazy val deviceInfo = Map(
    DeviceAttribute.Class -> DeviceClass.Power,
    DeviceAttribute.Description -> "Solar panel",
    DeviceAttribute.Vendor -> Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product -> "Enligh10"
  )

  // 1.21.1：Scala `Map` → `java.util.Map` 需要显式 `asJava`。
  override def getDeviceInfo: util.Map[String, String] = deviceInfo.asJava

  // ----------------------------------------------------------------------- //

  override val canUpdate = true

  override def update(): Unit = {
    super.update()

    ticksUntilCheck -= 1
    if (ticksUntilCheck <= 0) {
      ticksUntilCheck = 100
      isSunShining = isSunVisible
    }
    if (isSunShining) {
      node.changeBuffer(Settings.get.solarGeneratorEfficiency)
    }
  }

  /**
   * 太阳是否可见（可以发电）。
   *
   * 1.21.1 迁移要点：
   *  - `Level#isDaytime` → `Level#isDay()`
   *  - `Level#provider.hasNoSky` → `Level#dimensionType().hasSkyLight()`
   *  - `Level#canBlockSeeTheSky(x, y, z)` → `LevelReader#canSeeSkyFromBelowWater(BlockPos)`
   *  - `getWorldChunkManager.getBiomeGenAt(x, z).isInstanceOf[BiomeGenDesert]` →
   *    `getBiome(pos).is(Biomes.DESERT)`（1.21.1 的生物群系是注册表项，只能按 `ResourceKey` 判定）
   */
  private def isSunVisible = {
    val blockPos = BlockPosition(host).offset(Direction.UP)
    val pos = blockPos.toChunkCoordinates
    val world = host.world
    world.isDay &&
      world.dimensionType().hasSkyLight &&
      world.canSeeSkyFromBelowWater(pos) &&
      (world.getBiome(pos).is(Biomes.DESERT) || (!world.isRaining && !world.isThundering))
  }
}
