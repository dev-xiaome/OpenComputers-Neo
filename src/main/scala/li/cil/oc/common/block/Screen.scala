package li.cil.oc.common.block

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.tileentity
import li.cil.oc.util.{Color, PackedColor, Rarity, Tooltip}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.{Entity, LivingEntity}
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.{BlockGetter, Level}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

/**
 * 屏幕（原 1.7.10 `Screen`，等级 1 / 2 / 3）——**重点降级文件**。
 *
 * 屏幕在 1.7.10 里是「一个方块 + metadata 表示等级」的**多方块**：相邻的屏幕会自动合并成一个大的
 * 显示区域，渲染时按自己在大屏幕里的位置挑选贴图（`Icons` 那一大堆名字），并且还能被键盘点击、
 * 被箭矢射击、被玩家踩踏。这些能力大部分依赖 `li.cil.oc.client`（模型 / TESR）与
 * `li.cil.oc.common.component.TextBuffer`（显示缓冲），两者都尚未移植，因此这里：
 *
 *  1. 保留 [[tier]]、朝向（由 [[tileentity.traits.Rotatable]] 提供）与邻接屏幕合并的入口方法
 *     （[[rightClick]]、以及 `onBlockPlacedBy` 里的 `delayUntilCheckForMultiBlock` 重置）；
 *  2. 交互逻辑（点击屏幕、键盘转发）按 1.21.1 的钩子映射保留；
 *  3. 渲染 / 贴图 / 客户端专用 GUI 部分占位 + `TODO`。
 *
 * 1.21.1 迁移要点：
 *  - 等级改为每级一个方块（`screen1` / `screen2` / `screen3`），[[tier]] 为 `val`：
 *    方块实体要从方块实例反查等级（见 `tileentity.Screen`）。
 *  - `ModColoredLights.setLightLevel(this, 5, 5, 5)` → 构造属性 `lightLevel(_ => 5)`，
 *    见 [[Screen.properties]]。TODO(integration.coloredlights): 彩色光源集成移植后补上彩色发光。
 *  - `getRenderColor(metadata)` → [[SimpleBlockHooks.tintColor]]（按等级着色，客户端由
 *    [[li.cil.oc.client.ColorHandlers]] 注册 `BlockColor` / `ItemColor` 后生效）。
 *  - `isSideSolid`（原「正面不算实心」）：1.21.1 由碰撞形状 + 面坚固判定取代，不再覆写。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]，内部转发到 [[rightClick]]
 *    （保留原签名，`common.block.Keyboard` 也用它触发屏幕点击）。
 *  - `getValidRotations(world, x, y, z)` → [[SimpleBlockHooks.validRotations]]；该钩子**没有位置参数**，
 *    无法像旧实现那样「排除正反面」，因此退化为允许全部朝向；
 *    原来的按位置判定保留在 [[validRotationsAt]] 里，供 [[rightClick]] 使用。
 *    TODO(方块): 等 `SimpleBlockHooks.validRotations` 增加 `(state, level, pos)` 版本后改回精确实现。
 *  - 原 `onEntityWalking`（踩在朝上的屏幕上）→ 保留为普通方法 [[onEntityWalking]]，
 *    但 1.21.1 没有对应钩子（需要 `SimpleBlock` 转发 `Block#stepOn`），因此目前不会被调用。
 *    TODO(方块): `Block#stepOn` 转发钩子。
 *  - 原 `onEntityCollidedWithBlock`（箭矢命中屏幕 = 点击）**未移植**：
 *    1.7.10 的 `EntityArrow` 在 1.21.1 是 `AbstractArrow`，而 `tileentity.Screen#shot` 的签名
 *    仍是旧类型，等方块实体层移植完再一起处理。
 *    TODO(方块 / 客户端): `Block#entityInside` 转发钩子 + `AbstractArrow` 版本。
 *  - 客户端专用 GUI（原 `player.openGui(OpenComputers, GuiType.Screen.id, ...)`，只在 `world.isRemote`
 *    下调用）与「只有本机玩家才点击」的判断（`Minecraft.getMinecraft.thePlayer`）都属客户端逻辑。
 *    TODO(GUI): 等 `common.container` 与 `li.cil.oc.client.gui` 移植后恢复为 `player.openMenu(...)`。
 *  - `getIcon` / `Icons`（多方块屏幕的贴图选择）/ `registerBlockIcons` 全部删除。
 *
 * 纹理说明（`tools/gen-block-assets.ps1` 负责生成，本类不再触碰任何资源文件）：
 * 屏幕**不是「六面各一张」**的方块：它按「在大屏幕里的位置」取贴图，面序沿用 1.7.10 的
 * `Array[Option[String]](DOWN, UP, NORTH, SOUTH, WEST, EAST)`，其中 SOUTH（正面）用 `f*` 系列、
 * NORTH（背面）用 `b*` 系列、UP/DOWN 与侧面用无后缀的通用贴图。命名规则：
 *  - 首字母：`f` = front（正面），`b` = back（背面）；
 *  - 次字母：`h` = 水平边，`v` = 垂直边，`t/m/b` = 上 / 中 / 下，`l/m/r` = 左 / 中 / 右；
 *  - 结尾：`t` = top、`m` = middle、`b` = bottom、`l/m/r` = left/middle/right（例如 `fht` = 正面-水平-上）；
 *  - 后缀 `2` 表示「单方块 / 无环境光遮蔽」的那一份（`f2`、`b2` 与 `fbl2` 之类）；
 *  - 单方块屏幕的六面映射（原 `getIcon(side, metadata)`）：南 = `f2`，下 / 上 = `b`，其它 = `b2`。
 *  完整贴图名（均在 `assets/opencomputers_neo/textures/block/screen/` 下，相对路径省略 `screen/` 前缀）：
 *  `b`, `b2`, `bbl`, `bbl2`, `bbm`, `bbm2`, `bbr`, `bbr2`, `bhb`, `bhb2`, `bhm`, `bhm2`, `bht`, `bht2`,
 *  `bml`, `bmm`, `bmr`, `btl`, `btm`, `btr`, `bvb`, `bvb2`, `bvm`, `bvt`,
 *  `f`, `f2`, `fbl`, `fbl2`, `fbm`, `fbm2`, `fbr`, `fbr2`, `fhb`, `fhb2`, `fhm`, `fhm2`, `fht`, `fht2`,
 *  `fml`, `fmm`, `fmr`, `ftl`, `ftm`, `ftr`, `fvb`, `fvb2`, `fvm`, `fvt`。
 *
 *  1.21.1 当前的静态模型（`models/block/screen1|2|3.json`，继承
 *  `opencomputers_neo:block/tinted_cube` 以获得 `tintindex`）只做**兜底外观**：
 *  面贴图按「放在地上、正面朝南」的单方块屏幕（`screen.pitch == DOWN` 分支）取值 ——
 *  正面（南）= `screen/f2`，背面（北 / 西 / 东）= `screen/b2`，上面（朝上）= `screen/b`。
 *  多方块拼接与朝向变化后的贴图选择（原 `Icons` + `getIcon`）仍待客户端实现，
 *  见下面的 TODO。
 *  TODO(客户端): 这些贴图的选择逻辑（原 `Icons` + `getIcon`）需要客户端在渲染时按方块实体状态
 *  （`width` / `height` / `localPosition` / 朝向）决定，等 TESR / 状态化模型移植后恢复。
 */
class Screen(val tier: Int, properties: BlockBehaviour.Properties = Screen.properties())
  extends RedstoneAware(properties) {

  // ----------------------------------------------------------------------- //
  // 染色 / 提示
  // ----------------------------------------------------------------------- //

  override def tintColor(state: BlockState, level: BlockGetter, pos: BlockPos, tintIndex: Int): Int = Color.byTier(tier)

  override def rarity(stack: ItemStack) = Rarity.byTier(tier)

  override def tooltipBody(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    val (w, h) = Settings.screenResolutionsByTier(tier)
    val depth = PackedColor.Depth.bits(Settings.screenDepthsByTier(tier))
    tooltip.addAll(Tooltip.get(getClass.getSimpleName, w, h, depth))
  }

  // ----------------------------------------------------------------------- //
  // 方块实体 / 放置
  // ----------------------------------------------------------------------- //

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Screen(pos, state)

  override def onBlockPlacedBy(state: BlockState, level: Level, pos: BlockPos, placer: LivingEntity, stack: ItemStack): Unit = {
    super.onBlockPlacedBy(state, level, pos, placer, stack)
    // 放置后立即检查一次多方块合并（原 `delayUntilCheckForMultiBlock = 0`）。
    level.getBlockEntity(pos) match {
      case screen: tileentity.Screen => screen.delayUntilCheckForMultiBlock = 0
      case _ =>
    }
  }

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    // 1.7.10 的 `hitX/hitY/hitZ` 是方块内的相对坐标（0..1）。
    val location = hit.getLocation
    val hitX = (location.x - pos.getX).toFloat
    val hitY = (location.y - pos.getY).toFloat
    val hitZ = (location.z - pos.getZ).toFloat
    if (rightClick(level, pos.getX, pos.getY, pos.getZ, player, hit.getDirection, hitX, hitY, hitZ, force = false)) {
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else InteractionResult.PASS
  }

  /**
   * 屏幕的右键入口（原 `rightClick`）。
   *
   * 保留 1.7.10 的参数形状（方块坐标 + 相对命中坐标 + `force`），因为
   * [[li.cil.oc.common.block.Keyboard]] 也通过它把键盘点击转发给相邻屏幕。
   *
   * @param force 由键盘转发时为 `true`，表示忽略「扳手旋转」与「潜行切换触控模式」的判断。
   */
  def rightClick(level: Level, x: Int, y: Int, z: Int, player: Player, side: Direction,
                 hitX: Float, hitY: Float, hitZ: Float, force: Boolean): Boolean = {
    val pos = new BlockPos(x, y, z)
    // 原：`Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))`
    val holdsWrench = false // TODO(integration.util.Wrench): 扳手集成移植后恢复判断
    // 注意：物品未注册时 `api.Items.get` 返回 `null`，必须先判空再比较，否则 `null == null` 会误判。
    val analyzer = api.Items.get(Constants.ItemName.Analyzer)
    if (holdsWrench && validRotationsAt(level, pos).contains(side) && !force) false
    else if (analyzer != null && api.Items.get(player.getMainHandItem) == analyzer) false
    else level.getBlockEntity(pos) match {
      case screen: tileentity.Screen if screen.hasKeyboard && (force || player.isShiftKeyDown == screen.origin.invertTouchMode) =>
        // Yep, this GUI is actually purely client side. We could skip this
        // if, but it is clearer this way (to trigger it from the server we
        // would have to give screens a "container", which we do not want).
        // TODO(GUI): 原为 `if (world.isRemote) player.openGui(OpenComputers, GuiType.Screen.id, world, x, y, z)`；
        // 1.21.1 需要 `MenuProvider` + 客户端 `AbstractContainerScreen`（两层都未移植）。
        true
      case screen: tileentity.Screen if screen.tier > 0 && side == screen.facing =>
        if (level.isClientSide) {
          // 原：`if (world.isRemote && player == Minecraft.getMinecraft.thePlayer) screen.click(hitX, hitY, hitZ)`
          // TODO(客户端): 待 `li.cil.oc.client` 移植后补上「只有本机玩家才点击」的判断。
          screen.click(hitX.toDouble, hitY.toDouble, hitZ.toDouble)
        }
        else true
      case _ => false
    }
  }

  /**
   * 踩到屏幕（仅朝上的屏幕有效）。
   *
   * TODO(方块): 1.21.1 对应 `Block#stepOn`，`SimpleBlockHooks` 没有对应钩子，
   * 因此本方法目前不会被调用；等 `SimpleBlock` 转发 `stepOn` 后即可生效。
   */
  def onEntityWalking(level: Level, pos: BlockPos, entity: Entity): Unit =
    if (!level.isClientSide) level.getBlockEntity(pos) match {
      case screen: tileentity.Screen if screen.tier > 0 && screen.facing == Direction.UP => screen.walk(entity)
      case _ =>
    }

  // ----------------------------------------------------------------------- //
  // 旋转
  // ----------------------------------------------------------------------- //

  /**
   * 原 `getValidRotations(world, x, y, z)`：俯仰为 UP / DOWN 时允许任意朝向，
   * 否则排除当前朝向及其反向，避免把屏幕转到「看不见」的方向。
   */
  private def validRotationsAt(level: BlockGetter, pos: BlockPos): Array[Direction] =
    level.getBlockEntity(pos) match {
      case screen: tileentity.Screen =>
        if (screen.facing == Direction.UP || screen.facing == Direction.DOWN) Direction.values()
        else Direction.values().filter(d => d != screen.facing && d != screen.facing.getOpposite)
      case _ => Array(Direction.UP, Direction.DOWN)
    }

  /**
   * TODO(方块): [[SimpleBlockHooks.validRotations]] 没有位置参数，无法按当前朝向过滤，
   * 因此这里退化为「允许全部朝向」（等价于旧实现里俯仰为 UP / DOWN 的分支）。
   * 精确判定见 [[validRotationsAt]]。
   */
  override def validRotations: Array[Direction] = Direction.values()
}

object Screen {
  /**
   * 屏幕方块属性。
   *
   * 原 `ModColoredLights.setLightLevel(this, 5, 5, 5)`：没有彩色光源集成时，
   * 等价于把原版光照等级设为 5。
   */
  def properties(): BlockBehaviour.Properties =
    SimpleBlock.properties().lightLevel((_: BlockState) => 5)
}
