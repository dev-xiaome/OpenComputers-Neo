package li.cil.oc.common.block

import java.util

import com.google.common.base.Strings
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity
import li.cil.oc.util.ExtendedAABB._
import li.cil.oc.util.{BlockPosition, ExtendedAABB, InventoryUtils}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.shapes.{CollisionContext, Shapes, VoxelShape}

import scala.reflect.ClassTag

/**
 * 3D 打印件方块（原 1.7.10 `Print`）。
 *
 * 1.21.1 迁移要点：
 *  - `setLightOpacity(0)` / `setHardness(1)` / `setCreativeTab(null)` / `setBlockTextureName(...)`
 *    全部由构造属性与注册层表达：当前默认 [[Print.properties]] 已经带
 *    `strength(1f, ...)` 与 `noOcclusion()`；**不是**创造模式物品（原
 *    `setCreativeTab(null)`、`NEI.hide(this)`）。
 *    TODO(common.init.Registry): 「不出现在创造模式物品栏、但仍是合法物品」由调度方在
 *    注册方块物品时处理；NEI 集成不再移植。
 *  - `getLightValue`（按方块实体 `data.lightLevel` 发光）→ 1.21.1 应在方块层覆写
 *    `getLightEmission`，但该回调不在 [[SimpleBlockHooks]] 的允许钩子集合里，
 *    因此这里退而使用**放置时已知的默认值 0**（新建 `PrintData` 的 `lightLevel`，
 *    即 [[Print.properties]]），并在同步标签里保留真实值。
 *    TODO(客户端): 打印件的动态发光需要在客户端按方块实体数据换模型/发光，
 *    等 `li.cil.oc.client` 移植后通过 `BlockEntityRenderer` 或方块状态属性恢复；
 *    届时可在方块层覆写 `getLightEmission`（读方块实体）。
 *  - `getLightOpacity`（`Settings.get.printsHaveOpacity` 时按 `data.opacity` 变暗）：
 *    1.21.1 的光照遮挡由形状推导，原版已不再接受逐方块覆写，整块删除。
 *    TODO(客户端): 需要时用 `noOcclusion` 之外的模型/渲染方案模拟。
 *  - `shouldSideBeRendered` → 同名钩子（`isSingleShape` 时按形状是否贴着该面决定），
 *    这里直接用 `blockShape` 的包围盒与 `isSideSolid` 判定；
 *  - `isBlockSolid` / `isSideSolid`：`isSideSolid` 的语义（打印件某面是否铺满整个方块面）
 *    仍保留为 [[Print.isSideSolid]]，`isBlockSolid` 在 1.21.1 已由「碰撞形状 + 面坚固
 *    判定」取代，不再覆写；
 *  - `addCollisionBoxesToList` / `intersect`（自定义逐形状的碰撞与选中框）→
 *    [[SimpleBlockHooks.blockCollisionShape]] / [[SimpleBlockHooks.blockShape]]：
 *    1.21.1 的 `VoxelShape` 只表达一个形状，这里返回所有打印形状的**并集包围盒**
 *    （比原实现粗糙，但保证类型正确且不会漏掉碰撞）；
 *    TODO(客户端/形状): `VoxelShape` 可以用 `Shapes.join(..., BooleanOp.OR)` 精确并起来，
 *    需要时在 `ExtendedAABB` 之外再加一个 `AABB -> VoxelShape` 的逐形状转换；
 *  - `doSetBlockBoundsBasedOnState` → `blockShape`（用 `data.boundsOn` / `boundsOff`）；
 *  - `setBlockBoundsForItemRender` → 物品形态用单位包围盒（模型 JSON 决定外观）；
 *  - `canCreatureSpawn`（恒 `true`）→ 1.21.1 的 `isValidSpawn` 不在允许钩子集合里，
 *    不再覆写（默认即允许）；
 *  - `tickRate` / `updateTick`（按钮模式：按下后延时还原）→ 方块实体自身的 tick 负责
 *    （`tileentity.Print#toggleState` 里已经改为直接调用 `toggleState()`），
 *    方块层不再需要随机 tick；
 *  - `isBeaconBase` → 1.21.1 的信标底座走**方块标签**（`minecraft:beacon_base_blocks`），
 *    逐方块回调已删除：TODO(data): 需要调度方把带 `isBeaconBase` 标记的打印件加入标签
 *    （数据驱动无法按方块实体区分，属于已知功能损失）；
 *  - `breakBlock` 里「发出红石信号的打印件被拆除时通知邻居」→
 *    [[SimpleBlockHooks.onBlockPreDestroy]]（1.21.1 已由 `getSignal` 的重新求值覆盖，
 *    这里仍显式通知一次，保持与原实现一致的即时性）；
 *  - `getPickBlock` → 1.21.1 的选取逻辑在客户端：TODO(客户端) 用 `data.createItemStack()`
 *    复刻（否则选取得到的是无数据的物品）；
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]（调用方块实体的 `activate()`）；
 *  - `colorMultiplier` / `textureOverride` / `getIcon`：整套图标与着色系统删除，
 *    逐形状着色改由模型/渲染层处理（TODO(客户端)）。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = 未指定（沿用 `GenericTop`），北 / 南 / 西 / 东 = `GenericSide`；
 * 打印件本身是模型渲染（`getRenderType` = TESR），面纹理只在物品栏/兜底时使用。
 */
class Print(protected implicit val tileTag: ClassTag[tileentity.Print])
  extends RedstoneAware(Print.properties()) with traits.SpecialBlock with traits.CustomDrops[tileentity.Print] {

  // 原 `colorMultiplierOverride` / `textureOverride`（`IIcon`）/ `isSingleShape`：
  // 前两者随图标与客户端着色系统删除；`isSingleShape` 保留（渲染层按它决定是否剔除侧面）。
  var isSingleShape = false

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override protected def tooltipBody(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipBody(stack, player, tooltip, advanced)
    val data = new PrintData(stack)
    data.tooltip.foreach(s => tooltip.addAll(s.linesIterator.toList.asJava))
  }

  override protected def tooltipTail(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(stack, player, tooltip, advanced)
    val data = new PrintData(stack)
    if (data.isBeaconBase) {
      tooltip.add(Localization.Tooltip.PrintBeaconBase)
    }
    if (data.emitRedstone) {
      tooltip.add(Localization.Tooltip.PrintRedstoneLevel(data.redstoneLevel))
    }
    if (data.emitLight) {
      tooltip.add(Localization.Tooltip.PrintLightValue(data.lightLevel))
    }
  }

  // ----------------------------------------------------------------------- //
  // 形状
  // ----------------------------------------------------------------------- //

  /**
   * 原 `shouldSideBeRendered`：只有「单形状」且该面贴着方块边界时才剔除相邻面。
   *
   * 1.21.1 的这个钩子拿不到世界与坐标（原实现读的是当前 `setBlockBounds` 的结果），
   * 而打印件的形状在方块实体里，因此这里只能保守地返回 `true`（不剔除任何侧面）。
   * `isSingleShape` 字段仍然保留，供客户端渲染层使用。
   * TODO(客户端): `li.cil.oc.client` 移植后，在渲染层按 `data.stateOn/Off` 决定面剔除。
   */
  override def shouldSideBeRendered(state: BlockState, adjacentState: BlockState, side: Direction): Boolean = true

  /**
   * 原 `doSetBlockBoundsBasedOnState`：用 `data.boundsOn` / `data.boundsOff` 作为选中/可视化形状。
   *
   * 1.7.10 的 `AABB` 只表达一个盒子（`intersect` 负责逐形状的精确选中），
   * `PrintData.Shape`
   * 带来的「逐形状选中」简化为并集包围盒。
   */
  override def blockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    shape(Print.boundsAt(level, pos))

  /**
   * 原 `addCollisionBoxesToList`：把打印件的各个形状加入碰撞列表。
   *
   * 1.21.1 返回单个 `VoxelShape`，因此与原 `intersect` 一样用**并集包围盒**近似；
   * `settings.ignorePower`（`noclipOn` / `noclipOff`）的无碰撞语义因此无法精确表达。
   * TODO(形状): 需要精确碰撞时把各形状并成 `VoxelShape`（`Shapes.join(..., BooleanOp.OR)`），
   * 并在这里按 `noclip` 返回空形状。
   */
  override def blockCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
    shape(Print.boundsAt(level, pos))

  /**
   * 原 `isSideSolid`：打印件的某个面是否铺满整个方块面（用于剔除相邻面）。
   *
   * 1.7.10 里这个方法既服务于渲染剔除，也服务于 `isBlockSolid`；1.21.1 保留它自身，
   * 供 [[shouldSideBeRendered]] 与客户端渲染使用。
   */
  def isSideSolid(level: BlockGetter, pos: BlockPos, side: Direction): Boolean =
    level.getBlockEntity(pos) match {
      case print: tileentity.Print =>
        val shapes = if (print.state) print.data.stateOn else print.data.stateOff
        shapes.exists(value => {
          if (Strings.isNullOrEmpty(value.texture)) false
          else {
            val bounds = value.bounds.rotateTowards(print.facing)
            val fullX = bounds.minX == 0 && bounds.maxX == 1
            val fullY = bounds.minY == 0 && bounds.maxY == 1
            val fullZ = bounds.minZ == 0 && bounds.maxZ == 1
            side match {
              case Direction.DOWN => bounds.minY == 0 && fullX && fullZ
              case Direction.UP => bounds.maxY == 1 && fullX && fullZ
              case Direction.NORTH => bounds.minZ == 0 && fullX && fullY
              case Direction.SOUTH => bounds.maxZ == 1 && fullX && fullY
              case Direction.WEST => bounds.minX == 0 && fullY && fullZ
              case Direction.EAST => bounds.maxX == 1 && fullY && fullZ
            }
          }
        })
      case _ => false
    }

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: net.minecraft.world.phys.BlockHitResult): InteractionResult =
    level.getBlockEntity(pos) match {
      case print: tileentity.Print =>
        if (print.activate()) InteractionResult.sidedSuccess(level.isClientSide)
        else InteractionResult.PASS
      case _ => InteractionResult.PASS
    }

  // ----------------------------------------------------------------------- //
  // 方块实体
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Print(pos, state)

  // ----------------------------------------------------------------------- //
  // 放置 / 掉落
  // ----------------------------------------------------------------------- //

  override protected def doCustomInit(tile: tileentity.Print, player: LivingEntity, stack: ItemStack): Unit = {
    super.doCustomInit(tile, player, stack)
    tile.data.load(stack)
    // 原实现还调用了 `tileEntity.updateBounds()`（随 1.7.10 的 `getLightValue` 一起使用）；
    // 移植后的 Print 方块实体不再暴露该方法，形状改由 [[Print.boundsAt]] 每次现算。
  }

  override protected def doCustomDrops(tile: tileentity.Print, player: Player, willHarvest: Boolean): Unit = {
    super.doCustomDrops(tile, player, willHarvest)
    if (!player.isCreative) {
      InventoryUtils.spawnStackInWorld(tile.position, tile.data.createItemStack())
    }
  }

  // ----------------------------------------------------------------------- //
  // 拆除
  // ----------------------------------------------------------------------- //

  override def onBlockPreDestroy(state: BlockState, level: Level, pos: BlockPos): Unit = {
    super.onBlockPreDestroy(state, level, pos)
    // 原 `breakBlock`：发出红石信号的打印件被拆除时要通知邻居（尤其是它自己那一格）。
    level.getBlockEntity(pos) match {
      case print: tileentity.Print if print.data.emitRedstone(print.state) =>
        level.notifyNeighborsOfStateChange(pos, state.getBlock, null)
        Direction.values().foreach(side => {
          val neighbor = pos.relative(side)
          val neighborState = level.getBlockState(neighbor)
          level.notifyNeighborsOfStateChange(neighbor, neighborState.getBlock, null)
        })
      case _ =>
    }
  }
}

object Print {
  /**
   * 默认属性：`setHardness(1)` + `setLightOpacity(0)`（非完整方块）+ 默认光照等级。
   *
   * 光照等级取「新建打印件」的 `lightLevel`（默认 0）；动态发光见类注释里的 TODO。
   */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.properties().strength(1f, 5f).noOcclusion().
      lightLevel(_ => new PrintData().lightLevel)

  /** 打印件在 0..1 相对坐标下的包围盒（由各个形状并集得到）。 */
  def boundsFrom(data: PrintData, stateOn: Boolean, facing: Direction): AABB = {
    val shapes = if (stateOn) data.stateOn else data.stateOff
    val bounds = shapes.foldLeft(ExtendedAABB.unitBounds)((acc, shape) => union(acc, shape.bounds.rotateTowards(facing)))
    if (bounds.volume == 0) ExtendedAABB.unitBounds else bounds
  }

  /** 某坐标打印件的实际形状包围盒（无方块实体时退化为单位盒）。 */
  def boundsAt(level: BlockGetter, pos: BlockPos): AABB =
    level.getBlockEntity(pos) match {
      case print: tileentity.Print => boundsFrom(print.data, print.state, print.facing)
      case _ => ExtendedAABB.unitBounds
    }

  /** `AABB#minmax` 的替代（1.21.1 不再提供 `func_111270_a`）。 */
  private def union(a: AABB, b: AABB): AABB =
    new AABB(
      math.min(a.minX, b.minX), math.min(a.minY, b.minY), math.min(a.minZ, b.minZ),
      math.max(a.maxX, b.maxX), math.max(a.maxY, b.maxY), math.max(a.maxZ, b.maxZ))
}
