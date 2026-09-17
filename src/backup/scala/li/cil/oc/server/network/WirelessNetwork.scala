package li.cil.oc.server.network

import li.cil.oc.Settings
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.util.RTree
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 无线网络（按维度维护一棵 R 树，用于按信号强度做范围查询与遮挡判定）。
 *
 * ==1.21.1 移植要点==
 *  - `WorldEvent` → [[net.neoforged.neoforge.event.level.LevelEvent]]；
 *    `e.world.isRemote` → `e.getLevel.isClientSide`。
 *  - 1.21.1 没有数字维度 id，`provider.dimensionId` → `dimension().location()`，键类型改 [[ResourceLocation]]。
 *  - `Chunk#chunkTileEntityMap` → `LevelChunk#getBlockEntities()`。
 *  - 向量：`Vec3.createVectorHelper(x, y, z)` → `new Vec3(x, y, z)`；`xCoord/yCoord/zCoord` → `x/y/z`；
 *    `subtract` / `crossProduct` / `normalize` 改为 [[Vec3]] 自带的 `subtract` / `cross` / `normalize`。
 *  - `world.rand` → `world.random`；`world.blockExists(x, y, z)` → `world.isLoaded(BlockPos)`
 *    （等价于原 `blockExists(x, y, z)`：区块已加载且该坐标的方块实体可用）。
 *  - 方块硬度：`block.getBlockHardness(world, x, y, z)` →
 *    `world.getBlockState(pos).getDestroySpeed(world, pos)`。
 *  - `var hardness += ...` 之类的「对局部 var 做 `+=`」在 Scala 2.13 里成立，
 *    但原写法把 `hardness` 写成了被闭包捕获的 var，这里改为显式累加赋值。
 *
 * 事件注册：本对象上的 `@SubscribeEvent` 在 NeoForge 下对 Scala `object` 不可靠，
 * 因此统一由 `integration/opencomputers/ModOpenComputers` 用
 * `NeoForge.EVENT_BUS.addListener` 显式挂上（`onWorldLoad` / `onWorldUnload` / `onChunkUnload`）。
 */
object WirelessNetwork {
  val dimensions = mutable.Map.empty[ResourceLocation, RTree[WirelessEndpoint]]

  @SubscribeEvent
  def onWorldUnload(e: LevelEvent.Unload): Unit = {
    if (!e.getLevel.isClientSide) {
      dimensions.remove(dimension(e.getLevel))
    }
  }

  @SubscribeEvent
  def onWorldLoad(e: LevelEvent.Load): Unit = {
    if (!e.getLevel.isClientSide) {
      dimensions.remove(dimension(e.getLevel))
    }
  }

  // Safety clean up, in case some tile entities didn't properly leave the net.
  @SubscribeEvent
  def onChunkUnload(e: ChunkEvent.Unload): Unit = {
    e.getChunk match {
      case chunk: LevelChunk => chunk.getBlockEntities.values.asScala.foreach {
        case endpoint: WirelessEndpoint => remove(endpoint)
        case _ =>
      }
      case _ => // 非 LevelChunk（例如空区块）不含方块实体。
    }
  }

  def add(endpoint: WirelessEndpoint): Unit = {
    dimensions.getOrElseUpdate(dimension(endpoint), new RTree[WirelessEndpoint](Settings.get.rTreeMaxEntries)((endpoint) => (endpoint.x + 0.5, endpoint.y + 0.5, endpoint.z + 0.5))).add(endpoint)
  }

  def update(endpoint: WirelessEndpoint): Unit = {
    dimensions.get(dimension(endpoint)) match {
      case Some(tree) =>
        tree(endpoint) match {
          case Some((x, y, z)) =>
            val dx = math.abs(endpoint.x + 0.5 - x)
            val dy = math.abs(endpoint.y + 0.5 - y)
            val dz = math.abs(endpoint.z + 0.5 - z)
            if (dx > 0.5 || dy > 0.5 || dz > 0.5) {
              tree.remove(endpoint)
              tree.add(endpoint)
            }
          case _ =>
        }
      case _ =>
    }
  }

  def remove(endpoint: WirelessEndpoint): Boolean = removeFrom(dimension(endpoint), endpoint)

  /**
   * 兼容 `api.detail.NetworkAPI#leaveWirelessNetwork(WirelessEndpoint, int)`。
   *
   * 1.21.1 的维度没有数字 id，这里用 `minecraft:overworld` / `the_nether` / `the_end` 三个
   * 已知 id 做一次回退映射，并额外提供 [[removeFrom]] 供内部按 [[ResourceLocation]] 精确删除。
   * 该重载是 api 层为兼容 1.7.10 保留的入口，一旦 api 移除即可一并删除。
   */
  def remove(endpoint: WirelessEndpoint, dimensionId: Int): Boolean = {
    val legacy = dimensionId match {
      case -1 => ResourceLocation.withDefaultNamespace("the_nether")
      case 1 => ResourceLocation.withDefaultNamespace("the_end")
      case _ => ResourceLocation.withDefaultNamespace("overworld")
    }
    removeFrom(legacy, endpoint)
  }

  private def removeFrom(dimension: ResourceLocation, endpoint: WirelessEndpoint): Boolean = {
    dimensions.get(dimension) match {
      case Some(set) => set.remove(endpoint)
      case _ => false
    }
  }

  def computeReachableFrom(endpoint: WirelessEndpoint, strength: Double) = {
    dimensions.get(dimension(endpoint)) match {
      case Some(tree) if strength > 0 =>
        val range = strength + 1
        tree.query(offset(endpoint, -range), offset(endpoint, range)).
          filter(_ != endpoint).
          map(zipWithSquaredDistance(endpoint)).
          filter(_._2 <= range * range).
          map {
          case (c, distance) => (c, Math.sqrt(distance))
        } filter isUnobstructed(endpoint, strength) map(_._1)
      case _ => Iterable.empty[WirelessEndpoint]
    }
  }

  private def dimension(endpoint: WirelessEndpoint): ResourceLocation = dimension(endpoint.world)

  private def dimension(world: net.minecraft.world.level.LevelAccessor): ResourceLocation =
    world match {
      case level: net.minecraft.world.level.Level => level.dimension().location()
      case _ => ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
    }

  private def offset(endpoint: WirelessEndpoint, value: Double) =
    (endpoint.x + 0.5 + value, endpoint.y + 0.5 + value, endpoint.z + 0.5 + value)

  private def zipWithSquaredDistance(reference: WirelessEndpoint)(endpoint: WirelessEndpoint) =
    (endpoint, {
      val dx = endpoint.x - reference.x
      val dy = endpoint.y - reference.y
      val dz = endpoint.z - reference.z
      dx * dx + dy * dy + dz * dz
    })

  private def isUnobstructed(reference: WirelessEndpoint, strength: Double)(info: (WirelessEndpoint, Double)): Boolean = {
    val (endpoint, distance) = info
    val gap = distance - 1
    if (gap > 0) {
      // If there's some space between the two wireless network cards we try to
      // figure out if the signal might have been obstructed. We do this by
      // taking a few samples (more the further they are apart) and check if we
      // hit a block. For each block hit we subtract its hardness from the
      // surplus strength left after crossing the distance between the two. If
      // we reach a point where the surplus strength does not suffice we block
      // the message.
      val world = endpoint.world

      val origin = new Vec3(reference.x, reference.y, reference.z)
      val target = new Vec3(endpoint.x, endpoint.y, endpoint.z)

      // Vector from reference endpoint (sender) to this one (receiver).
      val delta = target.subtract(origin)
      val v = delta.normalize()

      // Get the vectors that are orthogonal to the direction vector.
      val up = if (v.x == 0 && v.z == 0) {
        assert(v.y != 0)
        new Vec3(1, 0, 0)
      }
      else {
        new Vec3(0, 1, 0)
      }
      val side = v.cross(up)
      val top = v.cross(side)

      // Accumulated obstructions and number of samples.
      var hardness = 0.0
      val samples = math.max(1, math.sqrt(gap).toInt)

      for (i <- 0 until samples) {
        val rGap = world.random.nextDouble() * gap
        // Adding some jitter to avoid only tracking the perfect line between
        // two endpoints when they are diagonal to each other for example.
        val rSide = world.random.nextInt(3) - 1
        val rTop = world.random.nextInt(3) - 1
        val x = (origin.x + v.x * rGap + side.x * rSide + top.x * rTop).toInt
        val y = (origin.y + v.y * rGap + side.y * rSide + top.y * rTop).toInt
        val z = (origin.z + v.z * rGap + side.z * rSide + top.z * rTop).toInt
        val pos = new BlockPos(x, y, z)
        if (world.isLoaded(pos)) {
          hardness = hardness + world.getBlockState(pos).getDestroySpeed(world, pos)
        }
      }

      // Normalize and scale obstructions:
      hardness *= gap / samples

      // See if we have enough power to overcome the obstructions.
      strength - gap > hardness
    }
    else true
  }
}
