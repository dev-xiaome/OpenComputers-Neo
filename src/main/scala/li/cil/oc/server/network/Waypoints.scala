package li.cil.oc.server.network

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.Waypoint
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.RTree
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.level.LevelEvent

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * 航点索引（按维度维护一棵 R 树）。
 *
 * ==1.21.1 移植要点==
 *  - `WorldEvent` 在 NeoForge 1.21.1 里叫 [[net.neoforged.neoforge.event.level.LevelEvent]]；
 *    `e.world.isRemote` → `e.getLevel.isClientSide`。
 *  - 1.21.1 没有数字维度 id，`world.provider.dimensionId` → `world.dimension().location()`，
 *    因此键的类型由 `Int` 改为 [[ResourceLocation]]。
 *  - `Chunk#chunkTileEntityMap` → `LevelChunk#getBlockEntities()`。
 *  - `tileEntity.isInvalid` → `BlockEntity#isRemoved`；`tileEntity.world` → `getLevel`
 *    （[[Waypoint]] 上由 [[li.cil.oc.common.tileentity.BlockEntityBase]] 暴露为 `world`）。
 *  - `AABB#expand` → `AABB#inflate`。
 *  - `BlockPosition#world` 是 `Option[Level]`，取维度 id 时要先取值。
 *
 * 事件注册：本对象上的 `@SubscribeEvent` 在 NeoForge 下对 Scala `object` 不可靠，
 * 因此统一由 `integration/opencomputers/ModOpenComputers` 用
 * `NeoForge.EVENT_BUS.addListener` 显式挂上（`onWorldLoad` / `onWorldUnload` / `onChunkUnload`）。
 */
object Waypoints {
  val dimensions = mutable.Map.empty[ResourceLocation, RTree[Waypoint]]

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
        case waypoint: Waypoint => remove(waypoint)
        case _ =>
      }
      case _ => // 非 LevelChunk（例如空区块）不含方块实体。
    }
  }

  def add(waypoint: Waypoint): Unit = if (!waypoint.isRemoved && waypoint.world != null && !waypoint.world.isClientSide) {
    val pos = waypoint.getBlockPos
    dimensions.getOrElseUpdate(dimension(waypoint.world), new RTree[Waypoint](Settings.get.rTreeMaxEntries)((_: Waypoint) => (pos.getX + 0.5, pos.getY + 0.5, pos.getZ + 0.5))).add(waypoint)
  }

  def remove(waypoint: Waypoint): Unit = if (waypoint.world != null && !waypoint.world.isClientSide) {
    dimensions.get(dimension(waypoint.world)) match {
      case Some(set) => set.remove(waypoint)
      case _ =>
    }
  }

  def findWaypoints(pos: BlockPosition, range: Double): Iterable[Waypoint] = {
    pos.world.flatMap(world => dimensions.get(dimension(world))).fold(Iterable.empty[Waypoint]) { set =>
      val bounds = pos.bounds.inflate(range * 0.5, range * 0.5, range * 0.5)
      set.query((bounds.minX, bounds.minY, bounds.minZ), (bounds.maxX, bounds.maxY, bounds.maxZ))
    }
  }

  private def dimension(waypoint: Waypoint): ResourceLocation = dimension(waypoint.world)

  private def dimension(world: net.minecraft.world.level.LevelAccessor): ResourceLocation =
    world match {
      case level: net.minecraft.world.level.Level => level.dimension().location()
      case _ => ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
    }
}
