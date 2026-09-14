package li.cil.oc.common.block

import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity
import li.cil.oc.util.Rarity
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.{AABB, BlockHitResult, Vec3}
import net.minecraft.world.phys.shapes.{CollisionContext, VoxelShape}

/**
 * 机器人移动残影（原 1.7.10 `RobotAfterimage`）。
 *
 * 这是一个**纯技术方块**：机器人移动时在原位置留一格残影，让移动动画看起来是
 * 「滑过去」而不是瞬移。它不可见、不可选取、不参与碰撞，并在移动结束后自动消失。
 *
 * 1.21.1 迁移要点：
 *  - 无方块实体：覆写 [[SimpleBlockHooks.hasBlockEntity]] 返回 `false`，
 *    `createBlockEntity` 保持 `null`；
 *  - `setLightOpacity(0)` / `setCreativeTab(null)` → 构造属性 [[SimpleBlock.nonOccluding]]。
 *    「不出现在创造模式物品栏」由注册层处理：
 *    TODO(common.init.Registry): 调度方注册方块物品时把该方块排除在创造模式标签页之外
 *    （原 `NEI.hide(this)` 随 NEI 集成删除）；
 *  - `isAir`（恒 `true`）：1.21.1 的 `Block` 没有可覆写的 `isAir`，等价物是**不渲染 +
 *    自定义空形状 + 不可选取**（见下）；
 *  - `isBlockSolid` / `isSideSolid`（恒 `false`）：1.21.1 已由「碰撞形状 + 面坚固判定」
 *    取代，不再覆写；`shouldSideBeRendered` 仍可覆写，返回 `false` 表示残影不渲染自身；
 *  - `getPickBlock` → 1.21.1 的选取逻辑在客户端：
 *    TODO(客户端) 用 [[RobotAfterimage.findMovingRobot]] 取 `robot.info.createItemStack()`，
 *    否则会选取到残影方块本身；
 *  - `onBlockAdded` / `updateTick`（`world.scheduleBlockUpdate` 延时后 `setBlockToAir`）→
 *    1.21.1 需要方块/方块实体 tick 才能延时移除，见 [[RobotAfterimage.properties]] 的
 *    TODO(方块 tick)；
 *  - `removedByPlayer` → [[SimpleBlockHooks.playerDestroyBlock]]：如果机器人正移动到这一格，
 *    改为移除**机器人所在格**的方块；
 *  - `doSetBlockBoundsBasedOnState` → [[SimpleBlockHooks.blockShape]]：机器人形状 + 移动偏移；
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：转发给机器人所在格的交互。
 *
 * 纹理：原实现六面都用 `…:GenericTop`，但 `shouldSideBeRendered` 恒 `false`，因此**实际
 * 不渲染**；生成的 `blockstates/robotafterimage.json` 指向空模型（只带 `particle` 纹理），
 * 与此一致。
 */
class RobotAfterimage(properties: BlockBehaviour.Properties = RobotAfterimage.properties())
  extends SimpleBlock(properties) with traits.SpecialBlock {

  /** 技术方块：没有方块实体（原 `hasTileEntity` 返回 `false`）。 */
  override def hasBlockEntity: Boolean = false

  // ----------------------------------------------------------------------- //
  // 渲染 / 形状
  // ----------------------------------------------------------------------- //

  /** 残影永不渲染自身（原 `shouldSideBeRendered` 恒 `false`）。 */
  override def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean = false

  /** 原 `doSetBlockBoundsBasedOnState`：跟随机器人的形状与移动偏移。 */
  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    shape(RobotAfterimage.bounds(level, pos))

  // ----------------------------------------------------------------------- //
  // 物品
  // ----------------------------------------------------------------------- //

  override def rarity(stack: ItemStack): net.minecraft.world.item.Rarity = {
    val data = new RobotData(stack)
    Rarity.byTier(data.tier)
  }

  // ----------------------------------------------------------------------- //
  // 交互 / 破坏
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): net.minecraft.world.InteractionResult =
    RobotAfterimage.findMovingRobot(level, pos) match {
      case Some(robot) =>
        // 原实现转发给机器人方块所在格的 `onBlockActivated`。
        val robotPos = new BlockPos(robot.x, robot.y, robot.z)
        val robotState = level.getBlockState(robotPos)
        robotState.getBlock match {
          case robotBlock: SimpleBlockHooks =>
            robotBlock.useBlock(robotState, level, robotPos, player,
              new BlockHitResult(hit.getLocation, hit.getDirection, robotPos, hit.isInside))
          case _ => net.minecraft.world.InteractionResult.PASS
        }
      case _ =>
        // 没有对应机器人：残影直接消失（原实现也是 `world.setBlockToAir`）。
        if (!level.isClientSide) level.destroyBlock(pos, false)
        net.minecraft.world.InteractionResult.PASS
    }

  override def playerDestroyBlock(state: BlockState, level: Level, pos: BlockPos, player: Player,
                                  blockEntity: BlockEntity, tool: ItemStack): Unit =
    RobotAfterimage.findMovingRobot(level, pos) match {
      case Some(robot) if robot.isAnimatingMove &&
        robot.moveFromX == pos.getX &&
        robot.moveFromY == pos.getY &&
        robot.moveFromZ == pos.getZ =>
        // 残影代表的机器人正在移出这一格：改为移除机器人当前所在格的方块。
        level.destroyBlock(new BlockPos(robot.x, robot.y, robot.z), false)
      case _ => // 可能已被它所代表的机器人撞掉。
    }
}

object RobotAfterimage {
  /**
   * 默认属性：非完整方块（原 `setLightOpacity(0)`）。
   *
   * TODO(方块 tick): 原 `onBlockAdded` 用 `world.scheduleBlockUpdate(..., max((moveDelay * 20).toInt, 1) - 1)`
   * 在移动动画结束后自动移除残影。1.21.1 里延时移除需要方块或方块实体 tick，
   * 而本方块**没有方块实体**；等 `common/EventHandler` / 方块 tick 注册层移植后，
   * 请在这里改为注册 `randomTick` 支持的方块（或给残影加一个极简方块实体）来清除残影。
   */
  def properties(): BlockBehaviour.Properties = SimpleBlock.nonOccluding()

  /** 残影的包围盒：机器人所在格的形状 + (当前格 - 出发格) 的偏移。 */
  def bounds(level: BlockGetter, pos: BlockPos): AABB =
    findMovingRobot(level, pos) match {
      case Some(robot) =>
        val robotPos = new BlockPos(robot.x, robot.y, robot.z)
        val robotBounds = robotShape(level, robotPos)
        val dx = robot.x - robot.moveFromX
        val dy = robot.y - robot.moveFromY
        val dz = robot.z - robot.moveFromZ
        robotBounds.move(dx, dy, dz)
      case _ =>
        // 找不到对应机器人时不给选中框（原实现会抛异常，这里返回空盒避免崩溃）。
        new AABB(Vec3.ZERO, Vec3.ZERO)
    }

  /** 机器人方块的形状（没有对应方块时退化为该格单位盒）。 */
  private def robotShape(level: BlockGetter, pos: BlockPos): AABB = {
    val state = level.getBlockState(pos)
    state.getBlock match {
      case hooks: SimpleBlockHooks =>
        val robotShape = hooks.blockShape(state, level, pos, CollisionContext.empty())
        if (robotShape.isEmpty) new AABB(pos) else robotShape.bounds()
      case _ => new AABB(pos)
    }
  }

  /**
   * 找到残影所代表的机器人（原 `findMovingRobot`）。
   *
   * 遍历六个方向上的邻居，找 `RobotProxy` 且其机器人的**出发坐标**正是本残影坐标的那个。
   */
  def findMovingRobot(level: BlockGetter, pos: BlockPos): Option[tileentity.Robot] =
    Direction.values().iterator.map(side => level.getBlockEntity(pos.relative(side))).collectFirst {
      case proxy: tileentity.RobotProxy
        if proxy.robot.moveFromX == pos.getX &&
          proxy.robot.moveFromY == pos.getY &&
          proxy.robot.moveFromZ == pos.getZ => proxy.robot
    }
}
