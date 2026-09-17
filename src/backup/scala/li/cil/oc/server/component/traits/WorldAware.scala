package li.cil.oc.server.component.traits

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedBlock._
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.phys.{AABB, BlockHitResult}
import net.minecraft.world.phys.shapes.CollisionContext
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.BlockEvent

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.reflect.classTag

/**
 * 需要访问世界的组件的公共父 trait（对应 1.7.10 的 `traits.WorldAware`）。
 *
 * 1.21.1 迁移要点：
 *  - `FakePlayerFactory` 仍在 `net.neoforged.neoforge.common.util` 下，只是包名变了。
 *  - `Entity#posX/posY/posZ` → `setPos(x, y, z)`
 *  - `Level#getEntitiesWithinAABB` → `Level#getEntitiesOfClass`（返回 `java.util.List`，需要 `.asScala`）
 *  - `Level#findNearestEntityWithinAABB` 已移除，改用 `getEntitiesOfClass` 后取首个
 *  - `EntityMinecart` → `AbstractMinecart`
 *  - `ForgeEventFactory.onPlayerInteract` 已移除，见 [[mayInteract]] 的 TODO
 *  - `BlockEvent.BreakEvent` 构造签名变为 `(Level, BlockPos, BlockState, Player)`，
 *    metadata 参数已随方块的 metadata 概念一起消失
 *  - `FluidRegistry.lookupFluidForBlock` 已移除，改用 `BlockState#getFluidState`
 *  - `Block#getCollisionBoundingBoxFromPool` → `BlockState#getCollisionShape(...)`
 */
trait WorldAware {
  def position: BlockPosition

  def world = position.world.get

  def fakePlayer: Player = {
    val player = FakePlayerFactory.get(world.asInstanceOf[ServerLevel], Settings.get.fakePlayerProfile)
    player.setPos(position.x + 0.5, position.y + 0.5, position.z + 0.5)
    player
  }

  /**
   * 设备是否可以操作指定面相邻的方块。
   *
   * TODO(integration): 1.7.10 用 `ForgeEventFactory.onPlayerInteract` 询问其它模组是否允许交互；
   * 1.21.1 没有该工厂方法，这里退化为投递 `PlayerInteractEvent.RightClickBlock` 并检查是否被取消。
   * 被领地/保护类模组取消时视为不可交互，语义与旧版一致。
   */
  def mayInteract(blockPos: BlockPosition, face: Direction): Boolean = {
    try {
      // 1.21.1 的 RightClickBlock 只接受 BlockHitResult，不再接受裸的 Direction；
      // 这里用方块中心 + 指定面构造一个等价的命中结果。
      val event = new PlayerInteractEvent.RightClickBlock(
        fakePlayer,
        net.minecraft.world.InteractionHand.MAIN_HAND,
        blockPos.toChunkCoordinates,
        new BlockHitResult(blockPos.toVec3, face, blockPos.toChunkCoordinates, false))
      !NeoForge.EVENT_BUS.post(event).isCanceled
    }
    catch {
      case t: Throwable =>
        OpenComputers.log.warn("Some event handler threw up while checking for permission to access a block.", t)
        true
    }
  }

  def entitiesInBounds[Type <: Entity : ClassTag](bounds: AABB): Iterable[Type] =
    world.getEntitiesOfClass(classTag[Type].runtimeClass.asInstanceOf[Class[Type]], bounds).asScala

  def entitiesInBlock[Type <: Entity : ClassTag](blockPos: BlockPosition): Iterable[Type] =
    entitiesInBounds[Type](blockPos.bounds)

  def entitiesOnSide[Type <: Entity : ClassTag](side: Direction): Iterable[Type] =
    entitiesInBlock[Type](position.offset(side))

  def closestEntity[Type <: Entity : ClassTag](side: Direction): Option[Type] = {
    val blockPos = position.offset(side)
    world.getEntitiesOfClass(classTag[Type].runtimeClass.asInstanceOf[Class[Type]], blockPos.bounds).asScala.headOption
  }

  def blockContent(side: Direction): (Boolean, String) = {
    closestEntity[Entity](side) match {
      case Some(_@(_: net.minecraft.world.entity.LivingEntity | _: AbstractMinecart)) =>
        (true, "entity")
      case _ =>
        val blockPos = position.offset(side)
        val blockState = world.getBlockState(blockPos)
        val fluidState = blockState.getFluidState
        if (blockState.isAir) {
          (false, "air")
        }
        else if (fluidState != null && !fluidState.isEmpty) {
          val event = new BlockEvent.BreakEvent(world, blockPos.toChunkCoordinates, blockState, fakePlayer)
          NeoForge.EVENT_BUS.post(event)
          (event.isCanceled, "liquid")
        }
        else if (blockState.canBeReplaced) {
          val event = new BlockEvent.BreakEvent(world, blockPos.toChunkCoordinates, blockState, fakePlayer)
          NeoForge.EVENT_BUS.post(event)
          (event.isCanceled, "replaceable")
        }
        else if (blockState.getCollisionShape(world, blockPos.toChunkCoordinates, CollisionContext.empty()).isEmpty) {
          (true, "passable")
        }
        else {
          (true, "solid")
        }
    }
  }
}
