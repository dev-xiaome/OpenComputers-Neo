package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.tileentity
import li.cil.oc.util.{BlockPosition, InventoryUtils}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.{AABB, BlockHitResult}
import net.minecraft.world.phys.shapes.{CollisionContext, VoxelShape}

/**
 * 键盘（原 1.7.10 `Keyboard`，贴在方块某一面（通常是屏幕下方）的薄板）。
 *
 * 1.21.1 迁移要点：
 *  - `setLightOpacity(0)` → 构造属性 `noOcclusion()`（见 [[Keyboard.properties]]）；
 *  - `isSideSolid`（恒 `false`）与 `shouldSideBeRendered`（恒 `true`）→
 *    [[SimpleBlockHooks.shouldSideBeRendered]] 返回 `true`（永不剔除），前者在 1.21.1
 *    已由「碰撞形状 + 面坚固判定」取代，不再覆写；
 *  - `doSetBlockBoundsBasedOnState` → [[SimpleBlockHooks.blockShape]]：把原
 *    `setBlockBounds(pitch, yaw)` 算出的 0..1 相对包围盒转成 `VoxelShape`；
 *  - `canPlaceBlockOnSide` → 同名钩子（判断所贴的面是否坚固，且正面没有背对我们的屏幕）；
 *  - `onNeighborBlockChange` → 同名钩子：贴附面不再坚固时**自动掉落**（原
 *    `world.setBlockToAir` + `InventoryUtils.spawnStackInWorld`）；
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：转发给相邻屏幕的
 *    `rightClick(..., force = true)`；
 *  - `onBlockPlacedBy` → 同名钩子：按放置者朝向设置 `pitch` / `yaw`
 *    （[[SimpleBlockHooks.setRotationFromEntityPitchAndYaw]]，等价于原 `ItemBlock` 的放置逻辑）；
 *  - `updateTick` 里 `api.Network.joinOrCreateNetwork(keyboard)` 删除：1.21.1 的网络加入
 *    由方块实体注册与 `Registry`/网络层负责（键盘方块实体 `canUpdate = false`，
 *    原实现靠随机 tick 兜底，这里不再需要）；
 *  - `getValidRotations` 原返回 `null`（即键盘不允许扳手旋转）：1.21.1 的
 *    [[SimpleBlockHooks.validRotations]] 没有 `null` 语义，保留基类默认值
 *    （`UP` / `DOWN`）——扳手集成未移植，当前不会被触发；
 *  - `setBlockBoundsForItemRender` / `preItemRender`（`org.lwjgl` 的 GL 调用）删除：
 *    1.21.1 的物品形态由模型 JSON 决定。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 六面都是 `Keyboard`。
 *
 * TODO(客户端): 键盘的薄板外观需要独立的烘焙模型（当前 blockstate 指向立方体模型），
 * 等 `li.cil.oc.client` 与模型生成补齐。
 * TODO(integration): 原 `ImmibisMicroblocks_TransformableBlockMarker`（Immibis 微方块标记）
 * 随该集成删除。
 */
class Keyboard(properties: BlockBehaviour.Properties = Keyboard.properties())
  extends SimpleBlock(properties) with traits.SpecialBlock {

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Keyboard(pos, state)

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  override def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean = true

  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    level.getBlockEntity(pos) match {
      case keyboard: tileentity.Keyboard => shape(Keyboard.bounds(keyboard.pitch, keyboard.yaw))
      case _ => shape(Keyboard.bounds(Direction.NORTH, Direction.WEST))
    }

  // ----------------------------------------------------------------------- //
  // 放置
  // ----------------------------------------------------------------------- //

  override def onBlockPlacedBy(state: BlockState, level: Level, pos: BlockPos,
                               placer: LivingEntity, stack: ItemStack): Unit = {
    super.onBlockPlacedBy(state, level, pos, placer, stack)
    setRotationFromEntityPitchAndYaw(level, pos, placer)
  }

  override def canPlaceBlockOnSide(state: BlockState, level: Level, pos: BlockPos, side: Direction): Boolean = {
    val neighborPos = pos.relative(side)
    level.getBlockState(neighborPos).isFaceSturdy(level, neighborPos, side.getOpposite) &&
      (level.getBlockEntity(neighborPos) match {
        case screen: tileentity.Screen => screen.facing != side.getOpposite
        case _ => true
      })
  }

  // ----------------------------------------------------------------------- //
  // 邻居变化
  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit =
    level.getBlockEntity(pos) match {
      case keyboard: tileentity.Keyboard =>
        if (!canPlaceBlockOnSide(state, level, pos, keyboard.facing.getOpposite)) {
          // 原 `world.setBlockToAir` + 手动掉出键盘物品。
          level.destroyBlock(pos, false)
          InventoryUtils.spawnStackInWorld(
            BlockPosition(pos.getX, pos.getY, pos.getZ, level),
            api.Items.get(Constants.BlockName.Keyboard).createItemStack(1))
        }
      case _ =>
    }

  // ----------------------------------------------------------------------- //
  // 交互：转发给相邻屏幕
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult =
    adjacencyInfo(level, BlockPosition(pos.getX, pos.getY, pos.getZ, level)) match {
      case Some((_, screen, screenPos, facing)) =>
        if (screen.rightClick(level, screenPos.getX, screenPos.getY, screenPos.getZ, player, facing, 0, 0, 0, force = true))
          InteractionResult.sidedSuccess(level.isClientSide)
        else InteractionResult.PASS
      case _ => InteractionResult.PASS
    }

  /**
   * 找到与键盘相邻的屏幕（原 `adjacencyInfo`）。
   *
   * 三种情况依次判断：贴附面、键盘「前方」（俯仰为 UP/DOWN 时取 yaw，否则取 UP）、
   * 以及墙上键盘下方的屏幕。
   */
  def adjacencyInfo(level: Level, position: BlockPosition): Option[(tileentity.Keyboard, Screen, BlockPos, Direction)] =
    level.getBlockEntity(position.toChunkCoordinates) match {
      case keyboard: tileentity.Keyboard =>
        def screenBlockAt(target: BlockPos): Option[Screen] =
          level.getBlockState(target).getBlock match {
            case screen: Screen => Some(screen)
            case _ => None
          }
        def result(target: BlockPos, facing: Direction): Option[(tileentity.Keyboard, Screen, BlockPos, Direction)] =
          screenBlockAt(target).map(screen => (keyboard, screen, target, facing))

        val attached = position.offset(keyboard.facing.getOpposite).toChunkCoordinates
        result(attached, keyboard.facing.getOpposite) match {
          case some@Some(_) => some
          case _ =>
            // Special case #1: check for screen in front of the keyboard.
            val forward = keyboard.facing match {
              case Direction.UP | Direction.DOWN => keyboard.yaw
              case _ => Direction.UP
            }
            val front = position.offset(forward).toChunkCoordinates
            result(front, forward) match {
              case some@Some(_) => some
              case _ if keyboard.facing != Direction.UP && keyboard.facing != Direction.DOWN =>
                // Special case #2: check for screen below keyboards on walls.
                val below = position.offset(forward.getOpposite).toChunkCoordinates
                result(below, forward.getOpposite)
              case _ => None
            }
        }
      case _ => None
    }
}

object Keyboard {
  /** 键盘是薄板方块，`setLightOpacity(0)` 的等价物。 */
  def properties(): BlockBehaviour.Properties = SimpleBlock.nonOccluding()

  /**
   * 原 `setBlockBounds(pitch, yaw)`：返回键盘的 0..1 相对包围盒。
   *
   * 尺寸沿用原值：贴附方向厚度 7/16，两个横向尺寸分别是 4/16 与 7/16，
   * 沿贴附方向从方块中心向外 0.5。
   */
  def bounds(pitch: Direction, yaw: Direction): AABB = {
    val (forward, up) = pitch match {
      case side@(Direction.DOWN | Direction.UP) => (side, yaw)
      case _ => (yaw, Direction.UP)
    }
    val side = rotation(forward, up)
    val sizes = Array(7f / 16f, 4f / 16f, 7f / 16f)
    def x(offset: Direction) = offset.getStepX
    def y(offset: Direction) = offset.getStepY
    def z(offset: Direction) = offset.getStepZ
    val x0 = -x(up) * sizes(1) - x(side) * sizes(2) - x(forward) * sizes(0)
    val x1 = x(up) * sizes(1) + x(side) * sizes(2) - x(forward) * 0.5f
    val y0 = -y(up) * sizes(1) - y(side) * sizes(2) - y(forward) * sizes(0)
    val y1 = y(up) * sizes(1) + y(side) * sizes(2) - y(forward) * 0.5f
    val z0 = -z(up) * sizes(1) - z(side) * sizes(2) - z(forward) * sizes(0)
    val z1 = z(up) * sizes(1) + z(side) * sizes(2) - z(forward) * 0.5f
    new AABB(
      math.min(x0, x1) + 0.5f, math.min(y0, y1) + 0.5f, math.min(z0, z1) + 0.5f,
      math.max(x0, x1) + 0.5f, math.max(y0, y1) + 0.5f, math.max(z0, z1) + 0.5f)
  }

  /**
   * 原 `ForgeDirection#getRotation(axis)` 的替代：把 `up` 与 `forward` 张成的第三个正交轴求出来。
   *
   * 需求只是「与 `forward` / `up` 都垂直」，两个候选方向里取哪个只影响包围盒在
   * 横向上的对称位置，因此这里用叉积的可行实现并留 TODO：
   * TODO(integration): 1.21.1 的原版 `Direction` 没有该 API；等扳手/客户端模型层移植后，
   * 用与 1.7.10 完全一致的旋转约定核对一遍（当前实现与原实现的两个候选轴之一等价）。
   */
  private def rotation(forward: Direction, up: Direction): Direction = {
    val axis = forward.getAxis
    axis match {
      case Direction.Axis.X => up.getAxis match {
        case Direction.Axis.Y => forward.getClockWise // +X 与 +Y 张成 Z 轴
        case _ => Direction.UP
      }
      case Direction.Axis.Z => up.getAxis match {
        case Direction.Axis.Y => forward.getCounterClockWise // +Z 与 +Y 张成 X 轴
        case _ => Direction.UP
      }
      case _ => // forward 是竖直方向（不可能出现在这里，兜底）
        Direction.NORTH
    }
  }
}
