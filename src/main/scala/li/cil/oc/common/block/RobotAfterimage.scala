package li.cil.oc.common.block

import li.cil.oc.common.blockentity
import li.cil.oc.common.blockentity.BlockEntityTypes
import li.cil.oc.util.BlockPosHelper
import li.cil.oc.{Constants, Settings, api}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.server.level.{ServerLevel => ServerWorld}
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.{Player => PlayerEntity}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityType}
import net.minecraft.world.level.block.state.BlockBehaviour.Properties
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.{Block, Blocks}
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.{BlockGetter => IBlockReader, Level => World}
import net.minecraft.world.level.LevelReader
import net.minecraft.world.phys.shapes.{VoxelShape, CollisionContext => ISelectionContext}
import net.minecraft.world.phys.{BlockHitResult => BlockRayTraceResult, HitResult => RayTraceResult}
import net.minecraft.world.ticks.ScheduledTick
import net.minecraft.world.{InteractionHand, InteractionResult => ActionResultType, ItemInteractionResult}

import java.util.Random

class RobotAfterimage(props: Properties) extends SimpleBlock(props) with traits.Tickable {
  // 1.21.1：`Block#getCloneItemStack` 的签名换成了 `(LevelReader, BlockPos, BlockState)`，
  // 原来的「玩家 / 命中结果」参数已移除。
  override def getCloneItemStack(world: LevelReader, pos: BlockPos, state: BlockState): ItemStack =
    findMovingRobot(world, pos) match {
      case Some(robot) => robot.info.createItemStack()
      case _ => ItemStack.EMPTY
    }

  override def getShape(state: BlockState, world: IBlockReader, pos: BlockPos, ctx: ISelectionContext): VoxelShape = {
    findMovingRobot(world, pos) match {
      case Some(robot) =>
        // 1.21.1：`BlockBehaviour#getShape` 是 protected，不能对「另一个方块实例」调用；
        // 改用 public 的 `BlockState#getShape(BlockGetter, BlockPos, CollisionContext)`。
        val shape = robot.getBlockState.getShape(world, robot.getBlockPos, ctx)
        val delta = robot.moveFrom.fold(BlockPos.ZERO)(vec => {
          val blockPos = robot.getBlockPos
          new BlockPos(blockPos.getX - vec.getX, blockPos.getY - vec.getY, blockPos.getZ - vec.getZ)
        })
        shape.move(delta.getX, delta.getY, delta.getZ)
      case _ => super.getShape(state, world, pos, ctx)
    }
  }

  // ----------------------------------------------------------------------- //

  override def onPlace(
                        state: BlockState,
                        world: World,
                        pos: BlockPos,
                        prevState: BlockState,
                        moved: Boolean
                      ): Unit = {
    super.onPlace(state, world, pos, prevState, moved)

    if (!world.isClientSide) {
      val delay = Math.max((Settings.get.moveDelay * 20).toInt, 1) - 1
      val triggerTime = world.getGameTime + delay.toLong

      world.getBlockTicks.schedule(new ScheduledTick[Block](this, pos, triggerTime, world.nextSubTickCount))
    }
  }

  override def tick(state: BlockState, world: ServerWorld, pos: BlockPos, rand: RandomSource): Unit = {
    world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState)
  }

  override def onDestroyedByPlayer(
                                state: BlockState,
                                world: World,
                                pos: BlockPos,
                                player: PlayerEntity,
                                willHarvest: Boolean,
                                fluid: FluidState
                              ): Boolean = {
    findMovingRobot(world, pos) match {
      case Some(robot) if robot.isAnimatingMove && robot.moveFrom.contains(pos) =>
        robot.proxy.getBlockState.getBlock.onDestroyedByPlayer(state, world, pos, player, false, fluid)
      case _ =>
        super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid)
    }
  }

  // 1.21.1：右击方块的入口从 `Block#use(state, level, pos, player, hand, hit)` 变成
  // `BlockBehaviour#useItemOn(stack, state, level, pos, player, hand, hit)` —— 多了一个手持堆叠参数，
  // 返回值也从 `InteractionResult` 换成 `ItemInteractionResult`。委托给机器人本体方块的状态。
  override def useItemOn(stack: ItemStack, state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: InteractionHand, trace: BlockRayTraceResult): ItemInteractionResult = {
    findMovingRobot(world, pos) match {
      case Some(robot) =>
        // 1.21.1：public 的 `BlockState#useItemOn` 只有 5 个参数
        // （stack, level, player, hand, hit）—— 方块状态与坐标已经隐含在接收者里。
        world.getBlockState(robot.getBlockPos).useItemOn(stack, world, player, hand, trace)
      case _ =>
        if (world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState)) ItemInteractionResult.sidedSuccess(world.isClientSide)
        else ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }
  }

  def findMovingRobot(world: IBlockReader, pos: BlockPos): Option[blockentity.Robot] = {
    for (side <- Direction.values) {
      val tpos = BlockPosHelper.relative(pos, side)
      if (world match {
        case world: World => world.isLoaded(tpos)
        case _ => true
      }) world.getBlockEntity(tpos) match {
        case proxy: blockentity.RobotProxy if proxy.robot.moveFrom.contains(pos) => return Some(proxy.robot)
        case _ =>
      }
    }
    None
  }

  override def getBlockEntityType: BlockEntityType[_ <: BlockEntity] = BlockEntityTypes.ROBOT.get()
}
