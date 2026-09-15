package li.cil.oc.common.block

import li.cil.oc.common.tileentity.traits
import li.cil.oc.util.Color
import li.cil.oc.util.Tooltip
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.{InteractionHand, InteractionResult, ItemInteractionResult}
import net.minecraft.world.level.{BlockGetter, Level, LevelReader}
import net.minecraft.world.level.block.{Block, EntityBlock, RenderShape, SoundType}
import net.minecraft.world.level.block.entity.{BlockEntity, BlockEntityTicker, BlockEntityType}
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.{AABB, BlockHitResult}
import net.minecraft.world.phys.shapes.{CollisionContext, Shapes, VoxelShape}

/**
 * ==方块移植说明（1.7.10 → 1.21.1）==
 *
 * 1.7.10 的 `SimpleBlock` 是一个类，其它方块 trait 通过 `extends SimpleBlock` 混入；
 * 1.21.1 的 `BlockBehaviour.Properties` 是构造参数，Scala trait 无法把参数传给父类，
 * 因此这里同样拆成两层（与 [[li.cil.oc.common.tileentity.traits.TileEntity]] 一致）：
 *
 *  - [[SimpleBlockHooks]]：原 `SimpleBlock` 的 API 表面，全部是**可覆写钩子**
 *    （子 trait 用 `extends SimpleBlockHooks`，方块实现用 `extends SimpleBlock(props) with ...`）；
 *  - [[SimpleBlock]]：真正的 `Block` 子类，把 1.21.1 的回调转发到钩子。
 *
 * ==整套图标系统已删除==
 * `IIcon` / `registerBlockIcons` / `getIcon(side, metadata)` / `customTextures` /
 * `setBlockBoundsForItemRender` 在 1.21.1 不再存在：面纹理改由**烘焙模型**指定
 * （`assets/opencomputers_neo/blockstates/<name>.json` +
 * `models/block/<name>.json` + `models/item/<name>.json`）。
 * 原 `customTextures` 里「某几个面用哪张贴图」的语义现在写在模型 JSON 的 `textures` 段。
 *
 * ==其它映射==
 * {{{
 *  1.7.10                                     1.21.1
 *  setHardness/setResistance                  Properties#strength(hardness, resistance)
 *  getRenderType                              getRenderShape（默认 MODEL）
 *  setBlockBoundsBasedOnState                 getShape / getCollisionShape（VoxelShape）
 *  getCollisionBoundingBoxFromPool            getCollisionShape
 *  getSelectedBoundingBoxFromPool             getShape（原版自动用于选中框）
 *  collisionRayTrace                          不再需要（原版基于 getShape 自动处理）
 *  onBlockActivated                           useWithoutItem / useItemOn
 *  canConnectRedstone / isProvidingWeakPower  canConnectRedstoneTo / getSignal / getDirectSignal
 *  onBlockPreDestroy                          onRemove（方块被替换/破坏时）
 *  createTileEntity / hasTileEntity           EntityBlock#newBlockEntity
 *  colorMultiplier / getRenderColor           BlockColor（客户端注册，见 tintColor 的 TODO）
 * }}}
 *
 * 注意：与 NeoForge 回调同名的钩子都带了后缀（`canConnectRedstoneTo`、`useBlock`、
 * `blockShape` 等），避免 `SimpleBlock` 里的转发方法自我递归。
 */
trait SimpleBlockHooks { self: Block =>

  /**
   * 1.7.10 的注册名（取值同 [[li.cil.oc.Constants.BlockName]]，例如 `diskDrive`、`case1`），
   * 由注册层（[[li.cil.oc.common.init.Registry.Blocks]]）在方块构造后写入。
   *
   * 用途只有一个：拼出 `tile.oc.<name>.name` 这个翻译键（见 [[SimpleBlock#getDescriptionId]]）。
   * 不能拿类名代替 —— `RobotProxy` 的键是 `tile.oc.robot.name`、
   * `FakeEndstone` 是 `tile.oc.endstone.name`、分级方块是 `case1` / `hologram2` 这种带后缀的名字。
   */
  var ocBlockName: String = null

  /** 是否在物品列表中展示；1.21.1 里由创造模式标签页决定，保留字段兼容原代码。 */
  var showInItemList = true

  /** 原 `createItemStack`。 */
  def createItemStack(amount: Int = 1): ItemStack = new ItemStack(this, amount)

  /** 原 `Block#getValidRotations`：允许扳手旋转到的朝向集合。 */
  protected def validRotations_ : Array[Direction] = Array(Direction.UP, Direction.DOWN)

  def validRotations: Array[Direction] = validRotations_

  // ----------------------------------------------------------------------- //
  // 渲染
  // ----------------------------------------------------------------------- //

  /**
   * TODO(客户端): 原 `getRenderType` 由 OC 自己的渲染类型编号控制（`Settings.blockRenderId`）。
   * 1.21.1 改用 `RenderShape`；需要 TESR 的方块（屏幕、机器人、全息投影等）在客户端
   * 注册 `BlockEntityRenderer` 后，可覆写为 `RenderShape.ENTITYBLOCK_ANIMATED`。
   */
  def renderShape: RenderShape = RenderShape.MODEL

  /**
   * 原 `colorMultiplier` / `getRenderColor`。
   *
   * TODO(客户端): 1.21.1 的染色要在客户端通过 `RegisterColorHandlersEvent.Block` 注册
   * `BlockColor`（`BlockColor#getColor(state, level, pos, tintIndex)`），而 `li.cil.oc.client`
   * 尚未移植，模型也还没有 `tintindex`，因此这里只保留钩子。
   */
  def tintColor(state: BlockState, level: BlockGetter, pos: BlockPos, tintIndex: Int): Int = 0xFFFFFF

  /** 原 `shouldSideBeRendered`：相邻面的剔除（线缆等非完整方块用）。 */
  def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean = true

  /** 物品形态的预渲染（原 `preItemRender`）；1.21.1 由模型 JSON 处理，保留空钩子。 */
  def preItemRender(): Unit = {}

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  /** 原 `doSetBlockBoundsBasedOnState`：返回选中/可视化形状。 */
  def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    Shapes.block()

  /** 原 `getCollisionBoundingBoxFromPool`：返回碰撞形状，默认与 [[blockShape]] 相同。 */
  def blockCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    blockShape(state, level, pos, context)

  /** 把 1.7.10 的 `setBlockBounds(AABB)` 语义（0..1 的相对包围盒）转成 `VoxelShape`。 */
  protected def shape(bounds: AABB): VoxelShape = Shapes.create(bounds)

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  /**
   * 原 `onBlockActivated(world, x, y, z, player, side, hitX, hitY, hitZ)` 中「空手/非物品交互」的部分。
   *
   * 1.21.1 拆成两个回调：`useWithoutItem`（返回 [[InteractionResult]]）与
   * `useItemOn`（返回 [[ItemInteractionResult]]）。命中位置信息在 [[BlockHitResult]] 里。
   */
  def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult =
    InteractionResult.PASS

  /** 手持物品右键；默认放行给原版默认逻辑（放置方块等）。 */
  def useItemOnBlock(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player,
                     hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult =
    ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION

  /** 原 `canPlaceBlockOnSide`：从指定面放置是否合法（自定义朝向的方块用）。 */
  def canPlaceBlockOnSide(state: BlockState, level: Level, pos: BlockPos, side: Direction): Boolean = true

  // ----------------------------------------------------------------------- //
  // 破坏 / 放置 / 掉落
  // ----------------------------------------------------------------------- //

  /**
   * 原 `onBlockPreDestroy`：方块被替换/破坏前的回调（OC 用它把内部物品栏掉出来）。
   *
   * 1.21.1 里由 `onRemove` 转发；只有当新状态不再是同一个方块时才会调用。
   */
  def onBlockPreDestroy(state: BlockState, level: Level, pos: BlockPos): Unit = {}

  /** 原 `onBlockPlacedBy`：由实体放置时设置朝向等。 */
  def onBlockPlacedBy(state: BlockState, level: Level, pos: BlockPos,
                      placer: net.minecraft.world.entity.LivingEntity, stack: ItemStack): Unit = {}

  /**
   * 原 `removedByPlayer` / `Block#getDrops` 的替代：玩家挖掉方块时的回调。
   *
   * 1.21.1 的掉落走战利品表，OC 需要把方块实体里的物品取出来（[[CustomDrops]]），
   * 因此这里转发 `playerDestroy`。
   */
  def playerDestroyBlock(state: BlockState, level: Level, pos: BlockPos, player: Player,
                         blockEntity: BlockEntity, tool: ItemStack): Unit = {}

  // ----------------------------------------------------------------------- //
  // 比较器
  // ----------------------------------------------------------------------- //

  /** 原 `hasComparatorInputOverride`。 */
  def providesAnalogOutput: Boolean = false

  /** 原 `getComparatorInputOverride`。 */
  def analogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int = 0

  // ----------------------------------------------------------------------- //
  // 红石
  // ----------------------------------------------------------------------- //

  /** 本方块是否输出红石信号（原 `canProvidePower`）。 */
  def providesRedstoneSignal: Boolean = false

  /** 原 `canConnectRedstone`；`side` 可能为 `null`。 */
  def canConnectRedstoneTo(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction): Boolean = false

  /** 原 `isProvidingWeakPower`。 */
  def isProvidingWeakPower(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction): Int = 0

  /** 原 `isProvidingStrongPower`，默认与弱信号一致。 */
  def isProvidingStrongPower(state: BlockState, level: BlockGetter, pos: BlockPos, side: Direction): Int =
    isProvidingWeakPower(state, level, pos, side)

  // ----------------------------------------------------------------------- //
  // 邻居变化 / 旋转 / 染色
  // ----------------------------------------------------------------------- //

  /** 原 `onNeighborBlockChange`。 */
  def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit = {}

  /** 原 `onNeighborChange`（间接邻居变化，能拿到邻居坐标）。 */
  def onNeighborChanged(state: BlockState, level: LevelReader, pos: BlockPos, neighborPos: BlockPos): Unit = {}

  /**
   * 原 `rotateBlock`（扳手旋转）。
   *
   * TODO(integration): 1.7.10 通过 Forge 的 `IForgeBlock#rotateBlock` 暴露给扳手，
   * 1.21.1 的工具交互改为 `getToolModifiedState`。这里保留普通方法，等 `integration` 移植后接入。
   */
  def rotateBlock(level: Level, pos: BlockPos, side: Direction): Boolean =
    level.getBlockEntity(pos) match {
      case rotatable: traits.Rotatable if rotatable.rotate(side) =>
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), Block.UPDATE_CLIENTS)
        true
      case _ => false
    }

  /**
   * 用染料给方块上色（原 `SimpleBlock.onBlockActivated` 里处理染料的逻辑）。
   *
   * TODO(integration): NeoForge 1.21.1 已移除 `recolourBlock`，染色改由物品交互实现；
   * 等扳手/染料集成移植后再从 `useItemOnBlock` 里调用本方法。
   */
  def tryApplyDye(state: BlockState, level: Level, pos: BlockPos, player: Player): Boolean =
    level.getBlockEntity(pos) match {
      case colored: traits.Colored if player != null && Color.isDye(player.getMainHandItem) =>
        colored.color = Color.dyeColor(player.getMainHandItem)
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
        if (colored.consumesDye && !level.isClientSide) {
          player.getMainHandItem.shrink(1)
        }
        true
      case _ => false
    }

  // ----------------------------------------------------------------------- //
  // 朝向查询（原 SimpleBlock 里基于 Rotatable 方块实体的帮助方法）
  // ----------------------------------------------------------------------- //

  def getFacing(level: BlockGetter, pos: BlockPos): Direction =
    level.getBlockEntity(pos) match {
      case rotatable: traits.Rotatable => rotatable.facing
      case _ => Direction.NORTH
    }

  def setFacing(level: Level, pos: BlockPos, value: Direction): Boolean =
    level.getBlockEntity(pos) match {
      case rotatable: traits.Rotatable => rotatable.setFromFacing(value); true
      case _ => false
    }

  def setRotationFromEntityPitchAndYaw(level: Level, pos: BlockPos, value: Entity): Boolean =
    level.getBlockEntity(pos) match {
      case rotatable: traits.Rotatable => rotatable.setFromEntityPitchAndYaw(value); true
      case _ => false
    }

  def toLocal(level: BlockGetter, pos: BlockPos, value: Direction): Direction =
    level.getBlockEntity(pos) match {
      case rotatable: traits.Rotatable => rotatable.toLocal(value)
      case _ => value
    }

  def toGlobal(level: BlockGetter, pos: BlockPos, value: Direction): Direction =
    level.getBlockEntity(pos) match {
      case rotatable: traits.Rotatable => rotatable.toGlobal(value)
      case _ => value
    }

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  /** 原 `createTileEntity`；返回 `null` 表示该方块没有方块实体。 */
  def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = null

  /** 原 `hasTileEntity(metadata)`。 */
  def hasBlockEntity: Boolean = true

  // ----------------------------------------------------------------------- //
  // 物品提示
  // ----------------------------------------------------------------------- //

  /** 原 `rarity(stack)`。 */
  def rarity(stack: ItemStack): net.minecraft.world.item.Rarity = net.minecraft.world.item.Rarity.COMMON

  /**
   * 原 `addInformation`。
   *
   * 1.21.1 的物品提示挂在 **Item** 上（`Item#appendHoverText`），而 OC 的方块物品由注册层
   * 统一创建成普通 `BlockItem`，因此这里通过 `ItemTooltipEvent`（见 [[BlockTooltipHandler]]）
   * 把提示补回方块物品。
   */
  def addInformation(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    tooltipHead(stack, player, tooltip, advanced)
    tooltipBody(stack, player, tooltip, advanced)
    tooltipTail(stack, player, tooltip, advanced)
  }

  protected def tooltipHead(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {}

  protected def tooltipBody(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName))
  }

  protected def tooltipTail(stack: ItemStack, player: Player, tooltip: java.util.List[String], advanced: Boolean): Unit = {}
}

/**
 * OpenComputers 方块基类。
 *
 * 具体方块的构造形态：
 * {{{
 *   class Adapter(properties: BlockBehaviour.Properties = SimpleBlock.properties())
 *     extends SimpleBlock(properties) with traits.GUI
 * }}}
 */
class SimpleBlock(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends Block(properties) with EntityBlock with SimpleBlockHooks {

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = createBlockEntity(pos, state)

  /**
   * 方块（及其 `BlockItem`）的翻译键。
   *
   * 1.21.1 的 `BlockItem#getDescriptionId` 就是 `getBlock().getDescriptionId()`，
   * 而 `Block#getDescriptionId` 的返回值会被当作**完整**的翻译键使用；
   * 1.7.10 的键形如 `tile.oc.<注册名>.name`，语言文件从旧版机械转换而来、键名保持不变，
   * 因此这里要原样拼出这个键（`ocBlockName` 为 `null` 时退回默认键）。
   */
  override def getDescriptionId: String =
    if (ocBlockName == null) super.getDescriptionId else "tile.oc." + ocBlockName + ".name"

  /**
   * 方块实体 tick 驱动（原 `TileEntity#updateEntity`）。
   *
   * 没有方块实体的方块（`createBlockEntity` 返回 `null`）原版不会注册 ticker，
   * 因此这里只需在 `hasBlockEntity` 为真时返回。
   */
  override def getTicker[T <: BlockEntity](level: Level, state: BlockState, blockEntityType: BlockEntityType[T]): BlockEntityTicker[T] =
    if (hasBlockEntity) SimpleBlock.ticker.asInstanceOf[BlockEntityTicker[T]] else null

  // ----------------------------------------------------------------------- //
  // 渲染 / 形状
  // ----------------------------------------------------------------------- //

  override def getRenderShape(state: BlockState): RenderShape = renderShape

  override def getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    blockShape(state, level, pos, context)

  override def getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    blockCollisionShape(state, level, pos, context)

  override def skipRendering(state: BlockState, adjacentState: BlockState, side: Direction): Boolean =
    !shouldSideBeRendered(state, adjacentState, side)

  // ----------------------------------------------------------------------- //
  // 红石
  // ----------------------------------------------------------------------- //

  override def isSignalSource(state: BlockState): Boolean = providesRedstoneSignal

  override def getSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
    isProvidingWeakPower(state, level, pos, direction)

  override def getDirectSignal(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Int =
    isProvidingStrongPower(state, level, pos, direction)

  override def canConnectRedstone(state: BlockState, level: BlockGetter, pos: BlockPos, direction: Direction): Boolean =
    canConnectRedstoneTo(state, level, pos, direction)

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult =
    useBlock(state, level, pos, player, hit)

  override def useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos, player: Player,
                                   hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult =
    useItemOnBlock(stack, state, level, pos, player, hand, hit)

  // ----------------------------------------------------------------------- //
  // 邻居变化 / 放置 / 破坏
  // ----------------------------------------------------------------------- //

  override def neighborChanged(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block,
                                         neighborPos: BlockPos, movedByPiston: Boolean): Unit =
    onNeighborBlockChange(state, level, pos, neighborBlock)

  override def onNeighborChange(state: BlockState, level: LevelReader, pos: BlockPos, neighborPos: BlockPos): Unit =
    onNeighborChanged(state, level, pos, neighborPos)

  override def onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean): Unit = {
    // 只有「换成了别的方块」才算被拆除；同一方块的 BlockState 变化（例如旋转）不算。
    if (state.getBlock ne newState.getBlock) {
      onBlockPreDestroy(state, level, pos)
    }
    super.onRemove(state, level, pos, newState, movedByPiston)
  }

  override def setPlacedBy(level: Level, pos: BlockPos, state: BlockState,
                           placer: net.minecraft.world.entity.LivingEntity, stack: ItemStack): Unit = {
    super.setPlacedBy(level, pos, state, placer, stack)
    onBlockPlacedBy(state, level, pos, placer, stack)
  }

  override def playerDestroy(level: Level, player: Player, pos: BlockPos, state: BlockState,
                                       blockEntity: BlockEntity, tool: ItemStack): Unit = {
    playerDestroyBlock(state, level, pos, player, blockEntity, tool)
    super.playerDestroy(level, player, pos, state, blockEntity, tool)
  }

  // ----------------------------------------------------------------------- //
  // 比较器
  // ----------------------------------------------------------------------- //

  override def hasAnalogOutputSignal(state: BlockState): Boolean = providesAnalogOutput

  override def getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
    analogOutputSignal(state, level, pos)

  /** `BlockPlaceContext` 版的替换判定，直接沿用原版语义（保留覆写点给子类）。 */
  override def canBeReplaced(state: BlockState, useContext: BlockPlaceContext): Boolean =
    super.canBeReplaced(state, useContext)
}

object SimpleBlock {

  /** OC 方块通用属性（等价于 1.7.10 `SimpleBlock` 构造里的 `setHardness(2f)` / `setResistance(5)`）。 */
  def properties(): BlockBehaviour.Properties =
    BlockBehaviour.Properties.of().
      strength(2f, 5f).
      sound(SoundType.METAL).
      dynamicShape()

  /** 非完整方块（线缆、屏幕、键盘等）：不遮挡相邻面。 */
  def nonOccluding(): BlockBehaviour.Properties =
    properties().noOcclusion()

  /** 通用 ticker：把 1.21.1 的方块实体 tick 转发到 [[li.cil.oc.common.tileentity.traits.TileEntity#tick]]。 */
  val ticker: BlockEntityTicker[BlockEntity] = new BlockEntityTicker[BlockEntity] {
    override def tick(level: Level, pos: BlockPos, state: BlockState, blockEntity: BlockEntity): Unit =
      blockEntity match {
        case te: traits.TileEntity =>
          te.tick()
          if (level.isClientSide) {
            te.tickClient()
          }
        case _ => // 不是 OC 方块实体，忽略。
      }
  }
}
