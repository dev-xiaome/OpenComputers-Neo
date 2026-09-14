package li.cil.oc.common.block

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity
import li.cil.oc.util.ItemStackNBTExtensions._
import li.cil.oc.util.{Rarity, Tooltip, TooltipKeyBindings}
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
 * 机器人代理方块（原 1.7.10 `RobotProxy`）。
 *
 * 1.21.1 迁移要点：
 *  - 方块本身只负责**放置 / 破坏 / 分析**，真正的逻辑都在方块实体
 *    （`tileentity.RobotProxy` 持有 `Robot` 实例），因此这里不覆写任何渲染逻辑。
 *  - `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]；原 `moving`（ThreadLocal）
 *    用于「机器人移动时把已有 `Robot` 实例交给新代理」的 hack，1.21.1 的方块实体构造只有
 *    `(pos, state)` 且由代理自己创建 `Robot`，因此 `moving` 仅为 `server.agent` 移植后保留。
 *  - `doSetBlockBoundsBasedOnState` → [[SimpleBlockHooks.blockShape]]（0.1..0.9 的方块，
 *    移动动画期间按剩余插值平移）。注意 1.21.1 的 `AABB` 是**不可变**的，`offset` → `move`，
 *    必须接收返回值。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：不潜行 = 打开 GUI；
 *    潜行 + 空手 = 启动机器人。
 *  - `removedByPlayer` / `getDrops` → [[SimpleBlockHooks.playerDestroyBlock]]：
 *    掉出 `robot.info.createItemStack()`，并清掉原位置的残影方块。
 *    TODO(方块): 1.21.1 无法在此取消破坏（见 `block.Case` 的说明），创造级机器人的
 *    权限校验只能退化为「不额外处理」。
 *    TODO(integration): 原 `getDrops` 里针对 AE2 湮灭平面的白名单 hack 不再需要
 *    （1.21.1 的掉落走战利品表 + `playerDestroy`）。
 *  - `getExplosionResistance(entity)` → 构造属性 `explosionResistance(10f)`；
 *    `setLightOpacity(0)` 删除（1.21.1 的遮光由形状决定，本方块形状非整格）。
 *  - `getPickBlock`：TODO(方块): 1.21.1 对应 `IBlockExtension#getCloneItemStack`，
 *    `SimpleBlockHooks` 没有该钩子，暂未移植（中间键拾取会得到「空白」机器人）。
 *  - `KeyBindings` → [[TooltipKeyBindings]]（`client` 包未移植时的占位）；
 *    `agent.Player` / `PacketSender` / `NEI` 全部降级（见各处 TODO）。
 *  - `getIcon` / `registerBlockIcons` / `shouldSideBeRendered` 相关贴图逻辑删除，
 *    面纹理改由模型 JSON 指定。
 *
 * 纹理（原 1.7.10 只用一张 `GenericTop` 给所有面），1.21.1 由调度方按此生成模型。
 */
class RobotProxy(properties: BlockBehaviour.Properties = RobotProxy.properties())
  extends RedstoneAware(properties) with traits.SpecialBlock with traits.StateAware {

  /** 原 `override val getUnlocalizedName = "Robot"`；1.21.1 的显示名走 `getDescriptionId`，这里仅用于提示键。 */
  def unlocalizedName: String = "Robot"

  /**
   * 机器人移动时暂存要复用的 `Robot` 实例（原 `moving`）。
   *
   * TODO(server.agent): `server.agent` 移植后由 `Robot#move` 设置，供
   * `createBlockEntity` 复用实例；目前 `createBlockEntity` 不再读取它。
   */
  var moving = new ThreadLocal[Option[tileentity.Robot]] {
    override protected def initialValue = None
  }

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  override def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean = false

  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = {
    level.getBlockEntity(pos) match {
      case proxy: tileentity.RobotProxy =>
        val robot = proxy.robot
        val bounds = new AABB(0.1, 0.1, 0.1, 0.9, 0.9, 0.9)
        val bounds_ =
          if (robot != null && robot.isAnimatingMove) {
            val remaining = robot.animationTicksLeft.toDouble / robot.animationTicksTotal.toDouble
            val dx = robot.moveFromX - robot.x
            val dy = robot.moveFromY - robot.y
            val dz = robot.moveFromZ - robot.z
            // 1.21.1 的 `AABB` 不可变：`offset` → `move`（返回新实例）。
            bounds.move(dx * remaining, dy * remaining, dz * remaining)
          }
          else bounds
        shape(bounds_)
      case _ => super.blockShape(state, level, pos, context)
    }
  }

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override def rarity(stack: ItemStack) = {
    val data = new RobotData(stack)
    Rarity.byTier(data.tier)
  }

  override protected def tooltipHead(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipHead(stack, player, tooltip, advanced)
    addLines(stack, tooltip)
  }

  override protected def tooltipBody(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get("Robot"))
  }

  override protected def tooltipTail(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(stack, player, tooltip, advanced)
    // 原：`if (KeyBindings.showExtendedTooltips)`
    if (TooltipKeyBindings.showExtendedTooltips) {
      val info = new RobotData(stack)
      val components = info.containers ++ info.components
      if (components.length > 0) {
        tooltip.addAll(Tooltip.get("Server.Components"))
        for (component <- components if component != null && !component.isEmpty) {
          tooltip.add("- " + component.getHoverName.getString)
        }
      }
    }
  }

  private def addLines(stack: ItemStack, tooltip: util.List[String]): Unit = {
    val tag = stack.getTag
    if (tag != null) {
      if (tag.contains(Settings.namespace + "xp")) {
        val xp = tag.getDouble(Settings.namespace + "xp")
        val level = math.min((Math.pow(xp - Settings.get.baseXpToLevel, 1 / Settings.get.exponentialXpGrowth) / Settings.get.constantXpGrowth).toInt, 30)
        if (level > 0) {
          tooltip.addAll(Tooltip.get(unlocalizedName + "_Level", level))
        }
      }
      if (tag.contains(Settings.namespace + "storedEnergy")) {
        val energy = tag.getInt(Settings.namespace + "storedEnergy")
        if (energy > 0) {
          tooltip.addAll(Tooltip.get(unlocalizedName + "_StoredEnergy", energy))
        }
      }
    }
  }

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = {
    // 原：`moving.get match { case Some(robot) => new tileentity.RobotProxy(robot); case _ => new tileentity.RobotProxy() }`
    // 1.21.1 的方块实体只接受 `(pos, state)`，`Robot` 实例由代理自己创建
    // （见 `tileentity.RobotProxy.robot`），因此这里无需分支。
    new tileentity.RobotProxy(pos, state)
  }

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    if (!player.isShiftKeyDown) {
      if (!level.isClientSide) {
        // We only send slot changes to nearby players, so if there was no slot
        // change since this player got into range he might have the wrong one,
        // so we send him the current one just in case.
        level.getBlockEntity(pos) match {
          case proxy: tileentity.RobotProxy if proxy.robot != null && proxy.robot.node != null && proxy.robot.node.network != null =>
            // 原：`PacketSender.sendRobotSelectedSlotChange(proxy.robot)`
            // TODO(server.PacketSender): 网络层移植后改为发送选中槽位同步包。
            // 原：`player.openGui(OpenComputers, GuiType.Robot.id, world, x, y, z)`
            // TODO(GUI): 见 `traits.GUI` 的说明——等 `common.container` 与客户端 GUI 移植后
            // 改为 `player.openMenu(new SimpleMenuProvider(...))`。
          case _ =>
        }
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else if (player.getMainHandItem.isEmpty) {
      if (!level.isClientSide) {
        level.getBlockEntity(pos) match {
          // TODO(server.machine): 机器层未完成时 `machine` 可能为 `null`，这里做空值保护。
          case proxy: tileentity.RobotProxy if proxy.machine != null && !proxy.machine.isRunning && proxy.isUseableByPlayer(player) =>
            proxy.machine.start()
          case _ =>
        }
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else InteractionResult.PASS
  }

  // ----------------------------------------------------------------------- //
  // 放置 / 破坏
  // ----------------------------------------------------------------------- //

  override def onBlockPlacedBy(state: BlockState, level: Level, pos: BlockPos, placer: LivingEntity, stack: ItemStack): Unit = {
    super.onBlockPlacedBy(state, level, pos, placer, stack)
    if (!level.isClientSide) (placer, level.getBlockEntity(pos)) match {
      // 原：`case (player: agent.Player, proxy: tileentity.RobotProxy) => Some((proxy.robot, player.agent.ownerName, player.agent.ownerUUID))`
      // TODO(server.agent): 机器人假玩家层未移植，这里只处理普通玩家放置的分支。
      case (player: Player, proxy: tileentity.RobotProxy) if proxy.robot != null =>
        val robot = proxy.robot
        // 原 `Item#placeBlockAt`：创造模式下先复制物品栈，保证不同机器人的组件地址互不相同
        // （否则它们的屏幕等组件会互相干扰）。
        val stackToUse = if (player.isCreative) new RobotData(stack).copyItemStack() else stack
        robot.ownerName = player.getGameProfile.getName
        robot.ownerUUID = player.getGameProfile.getId
        robot.info.load(stackToUse)
        // TODO(server.component): `bot` 目前是占位实现（`node` 恒为 `null`），因此这里做空值保护。
        if (robot.bot.node != null) {
          robot.bot.node.changeBuffer(robot.info.robotEnergy - robot.bot.node.localBuffer)
        }
        robot.updateInventorySize()
      case _ =>
    }
  }

  override def playerDestroyBlock(state: BlockState, level: Level, pos: BlockPos, player: Player,
                                  blockEntity: BlockEntity, tool: ItemStack): Unit = {
    blockEntity match {
      case proxy: tileentity.RobotProxy =>
        val robot = proxy.robot
        if (robot != null) {
          // Only allow breaking creative tier robots by allowed users.
          // Unlike normal robots, griefing isn't really a valid concern
          // here, because to get a creative robot you need creative
          // mode in the first place.
          // TODO(方块): 1.21.1 无法在这里取消破坏（见 `block.Case` 的说明）。
          val allowed = !(robot.isCreative && (!player.isCreative || !robot.canInteract(player.getGameProfile.getName)))
          if (allowed && robot.player != player && !level.isClientSide) {
            if (robot.node != null) {
              robot.node.remove()
            }
            robot.saveComponents()
            // 原：`dropBlockAsItem(world, x, y, z, robot.info.createItemStack())`
            Block.popResource(level, pos, robot.info.createItemStack())
          }
          // 清掉机器人移动时留在原位置的残影方块。
          // 注意：`moveFrom*` 在没有移动过时是 `Int.MaxValue`（见 `tileentity.Robot`），
          // 直接拿去查方块会得到越界坐标，因此这里先确认「确实移动过」。
          if (robot.moveFromX != Int.MaxValue && robot.moveFromY != Int.MaxValue && robot.moveFromZ != Int.MaxValue) {
            val fromPos = new BlockPos(robot.moveFromX, robot.moveFromY, robot.moveFromZ)
            val afterimage = Option(api.Items.get(Constants.BlockName.RobotAfterimage)).map(_.block).orNull
            if (afterimage != null && level.getBlockState(fromPos).getBlock == afterimage) {
              level.removeBlock(fromPos, false)
            }
          }
        }
      case _ =>
    }
    super.playerDestroyBlock(state, level, pos, player, blockEntity, tool)
  }

  override def onBlockPreDestroy(state: BlockState, level: Level, pos: BlockPos): Unit = {
    if (moving.get.isEmpty) {
      super.onBlockPreDestroy(state, level, pos)
    }
  }
}

object RobotProxy {
  /**
   * 机器人代理方块属性。
   *
   *  - 原 `setLightOpacity(0)` / `setCreativeTab(null)`：1.21.1 的遮光由形状决定，
   *    创造模式标签页由注册层负责（`Registry.hideBlockItemInCreativeTab`）。
   *  - 原 `getExplosionResistance(entity) = 10f` → [[BlockBehaviour.Properties#explosionResistance]]。
   *  - TODO(integration.util.NEI): 原 `NEI.hide(this)`，NEI 集成未移植。
   */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.nonOccluding().explosionResistance(10f)
}
