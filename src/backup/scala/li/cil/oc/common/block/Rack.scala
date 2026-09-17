package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.api.component.RackMountable
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.shapes.{CollisionContext, Shapes, VoxelShape}
import net.minecraft.world.phys.{AABB, BlockHitResult, Vec3}

/**
 * 服务器机架（原 1.7.10 `Rack`）。
 *
 * 1.21.1 迁移要点：
 *  - `createTileEntity(world, metadata)` → [[SimpleBlockHooks.createBlockEntity]]。
 *  - 原 `intersect`（自定义射线追踪：跳过正面外框，让点击落在内部凹槽上）在 1.21.1 改为
 *    [[SimpleBlockHooks.blockShape]]：返回「除朝向面以外的外框 + 内部凹槽」的并集，
 *    原版据此做选中与交互的射线追踪，命中点与旧实现一致；
 *    而碰撞形状（`blockCollisionShape`）仍保持整方块，与 1.7.10 的默认碰撞一致。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：命中坐标由
 *    `BlockHitResult#getLocation` 减去方块坐标得到（0..1），与原参数语义一致。
 *  - `AxisAlignedBB.getBoundingBox` → `new AABB`，`Vec3.createVectorHelper` → `new Vec3`，
 *    `Vec3#addVector` → `Vec3#add(dx, dy, dz)`，`xCoord/yCoord/zCoord` → `x/y/z`，
 *    `side.offsetX` → `side.getStepX`。
 *  - `isBlockSolid` / `isSideSolid`（原「除正面外都是实心」）：1.21.1 由碰撞形状与面坚固判定
 *    取代（见 [[traits.SpecialBlock]] 的说明），不再覆写。
 *  - `getMixedBrightnessForBlock`（客户端亮度微调，让机架内部亮一点）与 `frontOverride`
 *    （正面换成所装服务器的贴图）都属客户端渲染，已删除。
 *    TODO(客户端): `li.cil.oc.client` 移植后用状态化模型 / `BlockEntityRenderer` 恢复。
 *  - `getIcon` / `registerBlockIcons` / `customTextures` / `Textures.Rack.*` 全部删除。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = 未指定（沿用 `GenericTop`），北 = `RackSide`，南 = `RackFront`，
 * 西 / 东 = `RackSide`。
 * 原运行时贴图：`DiskDriveMountable`（磁盘驱动器抽屉）、`ServerFront`（服务器正面）、
 * `TerminalServerFront`（终端服务器正面）。
 */
class Rack(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) with traits.SpecialBlock with traits.PowerAcceptor with traits.StateAware with traits.GUI {

  override def energyThroughput = Settings.get.serverRackRate

  override def guiType = GuiType.Rack

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Rack(pos, state)

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  /**
   * 机架的外框（不含正面那一块，正面朝向内部凹槽敞开）+ 内部凹槽。
   *
   * 面序与 1.7.10 的 `ForgeDirection.VALID_DIRECTIONS` 一致：
   * DOWN, UP, NORTH, SOUTH, WEST, EAST。
   */
  final val collisionBounds = Array(
    new AABB(0, 0, 0, 1, 1 / 16f, 1),
    new AABB(0, 15 / 16f, 0, 1, 1, 1),
    new AABB(0, 0, 0, 1, 1, 1 / 16f),
    new AABB(0, 0, 15 / 16f, 1, 1, 1),
    new AABB(0, 0, 0, 1 / 16f, 1, 1),
    new AABB(15 / 16f, 0, 0, 1, 1, 1),
    new AABB(1 / 16f, 1 / 16f, 1 / 16f, 15 / 16f, 15 / 16f, 15 / 16f)
  )

  private def facingOf(level: BlockGetter, pos: BlockPos): Direction =
    level.getBlockEntity(pos) match {
      case rack: tileentity.Rack => rack.facing
      case _ => Direction.NORTH
    }

  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = {
    val facing = facingOf(level, pos)
    val directions = Direction.values()
    val boxes = (0 until directions.length).
      collect { case i if directions(i) != facing => collisionBounds(i) } :+
      collisionBounds.last
    boxes.foldLeft(Shapes.empty())((acc, box) => Shapes.or(acc, shape(box)))
  }

  override def blockCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    Shapes.block()

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    val handled = level.getBlockEntity(pos) match {
      case rack: tileentity.Rack =>
        // 1.7.10 的 `hitX/hitY/hitZ` 是方块内的相对坐标（0..1）。
        val location = hit.getLocation
        val hitX = (location.x - pos.getX).toFloat
        val hitY = (location.y - pos.getY).toFloat
        val hitZ = (location.z - pos.getZ).toFloat
        val side = hit.getDirection
        rack.slotAt(side, hitX, hitY, hitZ) match {
          case Some(slot) =>
            // Snap to grid to get same behavior on client and server...
            val hitVec = new Vec3(((hitX * 16f).toInt / 16f).toDouble, ((hitY * 16f).toInt / 16f).toDouble, ((hitZ * 16f).toInt / 16f).toDouble)
            val rotation = side match {
              case Direction.WEST => Math.toRadians(90).toFloat
              case Direction.NORTH => Math.toRadians(180).toFloat
              case Direction.EAST => Math.toRadians(270).toFloat
              case _ => 0
            }
            // Rotate *centers* of pixels to keep association when reversing axis.
            val localHitVec = rotate(hitVec.add(-0.5 + 1 / 32f, -0.5 + 1 / 32f, -0.5 + 1 / 32f), rotation).
              add(0.5 - 1 / 32f, 0.5 - 1 / 32f, 0.5 - 1 / 32f)
            val globalX = (localHitVec.x * 16.05f).toInt // [0, 15], work around floating point inaccuracies
            val globalY = (localHitVec.y * 16.05f).toInt // [0, 15], work around floating point inaccuracies
            val localX = (if (side.getStepX != 0) 15 - globalX else globalX) - 1
            val localY = (15 - globalY) - 2 - 3 * slot
            if (localX >= 0 && localX < 14 && localY >= 0 && localY < 3) rack.getMountable(slot) match {
              // Activation handled by the mountable.
              case mountable: RackMountable if mountable.onActivate(player, localX / 14f, localY / 3f) => true
              case _ => false
            }
            else false
          case _ => false
        }
      case _ => false
    }
    if (handled) InteractionResult.sidedSuccess(level.isClientSide)
    else super.useBlock(state, level, pos, player, hit)
  }

  def rotate(v: Vec3, t: Float): Vec3 = {
    val cos = Math.cos(t)
    val sin = Math.sin(t)
    new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos)
  }
}
