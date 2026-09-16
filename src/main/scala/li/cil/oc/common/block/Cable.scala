package li.cil.oc.common.block

import java.util

import li.cil.oc.api.network.{Environment, SidedComponent, SidedEnvironment}
import li.cil.oc.common.tileentity
import li.cil.oc.util.{Color, InventoryUtils}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.entity.{Entity, LivingEntity}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.AABB

import scala.reflect.ClassTag

/**
 * 网络线缆（原 1.7.10 `Cable`）。
 *
 * 1.21.1 迁移要点：
 *  - 形状：原 `doSetBlockBoundsBasedOnState` / `addCollisionBoxesToList` 用一组 12/16 见方的
 *    「中心 + 六条接线臂」包围盒；1.21.1 统一走
 *    [[SimpleBlockHooks.blockShape]] / [[SimpleBlockHooks.blockCollisionShape]]，这里把
 *    [[Cable.bounds]]（各臂的并集）转成 `VoxelShape`；`setLightOpacity(0)` 由
 *    构造属性 [[SimpleBlock.nonOccluding]] 的 `noOcclusion()` 表达；
 *  - `shouldSideBeRendered` 恒为 `true`（线缆面永不剔除）→ 覆写同名钩子的**取反语义**
 *    [[SimpleBlockHooks.shouldSideBeRendered]] 返回 `true`；
 *  - `isSideSolid` 恒为 `false`：1.21.1 由「碰撞形状 + 面坚固判定」取代，不再覆写；
 *  - 掉落：`CustomDrops` 保证按方块实体的颜色生成带颜色的物品（原 `dropBlockAsItem` →
 *    [[InventoryUtils.spawnStackInWorld]]）；
 *  - `getPickBlock`（潜行选取方块时取带颜色的物品）→ 1.21.1 的选取逻辑在客户端，
 *    TODO(客户端): 原实现从方块实体 `createItemStack()` 取带颜色物品，1.21.1 需要在
 *    客户端 `BlockEntityRenderer` / 交互层复刻，或改用物品数据组件保存颜色。
 *  - `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]，构造为
 *    `new tileentity.Cable(pos, state)`；
 *  - 整套图标系统删除（原 `Textures.Cable.iconCap` 是线缆端帽贴图）。
 *
 *  纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 *  六面都是 `CablePart`（端帽贴图为 `CableCap`，需要线缆专用的烘焙模型/渲染器才能还原，
 *  TODO(客户端): `li.cil.oc.client` 移植后补上线缆模型；FMP / Immibis 微方块版本的面剔除
 *  也依赖它）。
 *
 *  ==染色==
 *  线缆的贴图（`cablepart`）是灰阶的，颜色来自**方块实体的 `traits.Colored#color`**：
 *  客户端 [[li.cil.oc.client.ColorHandlers]] 注册的 `BlockColor` 会在这里取
 *  [[tileentity.Cable]] 的颜色，取不到（未被网络同步等）时退回 [[Color.LightGray]]。
 *  因此 `models/block/cable.json` 也必须继承 `opencomputers_neo:block/tinted_cube`（`tintindex`）。
 *  物品形态的颜色存在堆叠的 `ItemColorizer` 颜色里（见 `block.Item` / `tileentity.Cable`）。
 *
 * ==FMP（ForgeMultipart）集成整块删除==
 * 1.7.10 的 `Cable` 通过 `integration.fmp.CablePart` 支持「把线缆变成 FMP 微方块」，
 * 因此 `Cable` 对象里带有 `canConnectFromSideFMP` / `hasMultiPartNode` / `cableColorFMP`
 * 等分支，并实现 `JNormalOcclusion` / `TFacePart` / `TileMultipart` 的遮挡判定。
 * `codechicken.multipart.*` 在 1.21.1 不存在（FMP 也从未移植到该版本），
 * TODO(integration.fmp): 上述分支与方法整块删除；连接判定只看原版方块实体。
 * 同理，原 `ImmibisMicroblocks_TransformableBlockMarker`（Immibis 微方块标记）与
 * `canConnectFromSideIM`（`traits.ImmibisMicroblock` 的 `ImmibisMicroblocks_isSideOpen`）
 * 也一并删除。
 */
class Cable(protected implicit val tileTag: ClassTag[tileentity.Cable])
  extends SimpleBlock(Cable.properties()) with traits.SpecialBlock with traits.CustomDrops[tileentity.Cable] {

  // 注：原 `colorMultiplierOverride`（FMP 部件染色用）与
  // `ImmibisMicroblocks_TransformableBlockMarker` 随对应集成一起删除。

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  override def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean = true

  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: net.minecraft.world.phys.shapes.CollisionContext): net.minecraft.world.phys.shapes.VoxelShape =
    shape(Cable.bounds(level, pos))

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Cable(pos, state)

  // ----------------------------------------------------------------------- //
  // 邻居变化
  // ----------------------------------------------------------------------- //

  override def onNeighborBlockChange(state: BlockState, level: Level, pos: BlockPos, neighborBlock: Block): Unit = {
    // 原实现只做一次方块更新，让客户端重算线缆形状。
    level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
    super.onNeighborBlockChange(state, level, pos, neighborBlock)
  }

  // ----------------------------------------------------------------------- //
  // 放置 / 掉落
  // ----------------------------------------------------------------------- //

  override def doCustomInit(tile: tileentity.Cable, player: LivingEntity, stack: ItemStack): Unit = {
    super.doCustomInit(tile, player, stack)
    if (!tile.world.isClientSide) {
      tile.fromItemStack(stack)
    }
  }

  override def doCustomDrops(tile: tileentity.Cable, player: Player, willHarvest: Boolean): Unit = {
    super.doCustomDrops(tile, player, willHarvest)
    if (!player.isCreative) {
      InventoryUtils.spawnStackInWorld(tile.position, tile.createItemStack())
    }
  }
}

object Cable {
  /** 线缆是非完整方块，需要 `noOcclusion()`（等价于原 `setLightOpacity(0)`）。 */
  def properties(): BlockBehaviour.Properties = SimpleBlock.nonOccluding()

  private final val MIN = 0.375
  private final val MAX = 1 - MIN

  /** 中心立方体（12/16 见方）。 */
  val center: AABB = new AABB(MIN, MIN, MIN, MAX, MAX, MAX)

  /** 六条接线臂，下标与 `Direction#ordinal` 一致（DOWN, UP, NORTH, SOUTH, WEST, EAST）。 */
  val cachedParts: Array[AABB] = Array(
    new AABB(MIN, 0, MIN, MAX, MIN, MAX), // Down
    new AABB(MIN, MAX, MIN, MAX, 1, MAX), // Up
    new AABB(MIN, MIN, 0, MAX, MAX, MIN), // North
    new AABB(MIN, MIN, MAX, MAX, MAX, 1), // South
    new AABB(0, MIN, MIN, MIN, MAX, MAX), // West
    new AABB(MAX, MIN, MIN, 1, MAX, MAX)) // East

  /** 六个方向共 2^6 种组合，预先并成包围盒。 */
  val cachedBounds: Array[AABB] = {
    // 6 directions = 6 bits = 11111111b >> 2 = 0xFF >> 2
    (0 to 0xFF >> 2).map(mask => {
      Direction.values().foldLeft(center)((bound, side) =>
        if ((1 << side.ordinal & mask) != 0) union(bound, cachedParts(side.ordinal))
        else bound)
    }).toArray
  }

  /** `AABB#minmax` 的替代：把两个包围盒并起来（1.21.1 不再提供 `func_111270_a`）。 */
  private def union(a: AABB, b: AABB): AABB =
    new AABB(
      math.min(a.minX, b.minX), math.min(a.minY, b.minY), math.min(a.minZ, b.minZ),
      math.max(a.maxX, b.maxX), math.max(a.maxY, b.maxY), math.max(a.maxZ, b.maxZ))

  /**
   * 计算某坐标的线缆连接掩码（原 `neighbors(world, x, y, z)`）。
   *
   * 1.21.1 里 `ForgeDirection#flag` 等价于 `1 << ordinal`，方向跳过空气与未加载的区块。
   * FMP / Immibis 相关的判定已删除（见类注释）。
   */
  def neighbors(level: BlockGetter, pos: BlockPos): Int = {
    var result = 0
    val tileEntity = level.getBlockEntity(pos)
    for (side <- Direction.values()) {
      val neighborPos = pos.relative(side)
      if (isLoaded(level, neighborPos)) {
        val neighborTileEntity = level.getBlockEntity(neighborPos)
        val neighborHasNode = hasNetworkNode(neighborTileEntity, side.getOpposite)
        val canConnectColor = canConnectBasedOnColor(tileEntity, neighborTileEntity)
        if (neighborHasNode && canConnectColor) {
          result |= 1 << side.ordinal
        }
      }
    }
    result
  }

  /**
   * 原「`world.blockExists(...)` / `!world.isAirBlock(...)`」的等价实现。
   *
   * 1.21.1 里 `Level#isLoaded(BlockPos)` / `LevelReader#hasChunk(...)` 都不是
   * [[BlockGetter]] 的成员，而本方法的调用点（形状/碰撞/渲染包围盒查询）都发生在**已加载**
   * 的方块上，邻居坐标也必然在已加载的区块内，因此这里直接按「不是空气」判断。
   * TODO(渲染): 若将来在未加载区块上调用（例如跨区块渲染包围盒），请改用
   * `level match { case l: Level => l.isLoaded(pos); case _ => ... }`。
   */
  private def isLoaded(level: BlockGetter, pos: BlockPos): Boolean = !level.getBlockState(pos).isAir

  /** 某坐标的线缆包围盒（原 `bounds`）。 */
  def bounds(level: BlockGetter, pos: BlockPos): AABB =
    cachedBounds(neighbors(level, pos))

  /** 某坐标的线缆组成部件（原 `parts`，供 `BlockEntity#getRenderBoundingBox` 等使用）。 */
  def parts(level: BlockGetter, pos: BlockPos, entityBox: AABB, boxes: util.List[AABB]): Unit = {
    val centerBox = center.move(pos.getX, pos.getY, pos.getZ)
    if (entityBox.intersects(centerBox)) boxes.add(centerBox)

    val flag = neighbors(level, pos)
    for (side <- Direction.values()) {
      if ((1 << side.ordinal & flag) != 0) {
        val part = cachedParts(side.ordinal).move(pos.getX, pos.getY, pos.getZ)
        if (entityBox.intersects(part)) boxes.add(part)
      }
    }
  }

  /**
   * 该方块实体是否提供网络节点（原 `hasNetworkNode`）。
   *
   * TODO(integration.fmp): 原末尾还有 `case host if Mods.ForgeMultipart.isAvailable =>
   * hasMultiPartNode(tileEntity)` 分支，随 FMP 集成整块删除。
   */
  private def hasNetworkNode(tileEntity: BlockEntity, side: Direction): Boolean =
    tileEntity match {
      // 机器人代理本身不当作线缆节点（避免机器人移动时误连）。
      case _: tileentity.RobotProxy => false
      case host: SidedEnvironment =>
        if (host.getLevel.isClientSide) host.canConnect(side)
        else host.sidedNode(side) != null
      case host: Environment with SidedComponent =>
        host.canConnectNode(side)
      case _: Environment => true
      case _ => false
    }

  /** 线缆颜色（原 `cableColor`，FMP 部件分支已删除）。 */
  private def cableColor(tileEntity: BlockEntity): Int =
    tileEntity match {
      case cable: tileentity.Cable => cable.color
      case _ => Color.LightGray
    }

  /** 只有颜色相同、或其中一方是未染色（浅灰）时才能相连。 */
  private def canConnectBasedOnColor(te1: BlockEntity, te2: BlockEntity): Boolean = {
    val (c1, c2) = (cableColor(te1), cableColor(te2))
    c1 == c2 || c1 == Color.LightGray || c2 == Color.LightGray
  }
}
