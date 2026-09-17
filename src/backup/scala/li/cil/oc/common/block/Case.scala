package li.cil.oc.common.block

import java.util

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import li.cil.oc.util.{Rarity, Tooltip}
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

/**
 * 机箱（原 1.7.10 `Case`）：计算机的方块形态。
 *
 * 1.21.1 迁移要点：
 *  - 1.7.10 用一个方块 + metadata 表示等级，1.21.1 每个等级一个独立方块
 *    （`Constants.BlockName.CaseTier1/2/3` 与 `CaseCreative`，见规范 §1.2.1），
 *    因此 [[tier]] 是构造参数，并且**必须是 `val`**：方块实体要从方块实例反查等级
 *    （见 `tileentity.Case` 的 `state.getBlock match { case b: block.Case => b.tier }`）。
 *  - `createTileEntity(world, metadata)` → [[SimpleBlockHooks.createBlockEntity]]，
 *    构造统一为 `new tileentity.Case(pos, state)`（等级由方块决定）。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]；潜行时空手右键启动计算机。
 *  - `removedByPlayer`（返回 `false` 阻止破坏）→ [[SimpleBlockHooks.playerDestroyBlock]]。
 *    TODO(方块): 1.21.1 的 `playerDestroyBlock` 在**方块已经被破坏之后**才调用，
 *    无法像 1.7.10 的 `removedByPlayer` 那样返回 `false` 取消破坏；这里只能跳过
 *    内部物品的掉落处理（见 [[playerDestroyBlock]]）。要真正「禁止破坏」，需要
 *    `SimpleBlock` 额外把 `IBlockExtension#onDestroyedByPlayer` / `canEntityDestroy`
 *    转发成钩子（`SimpleBlock.scala` 由另一位 agent 负责，这里不改）。
 *  - `getRenderColor(metadata)` → [[SimpleBlockHooks.tintColor]]，客户端由
 *    [[li.cil.oc.client.ColorHandlers]] 注册 `BlockColor` / `ItemColor` 后生效：
 *    1-3 级与创造机箱的贴图是同一套灰阶贴图（`casetop` / `caseback` / `casefront` /
 *    `caseside`），颜色**全部**来自 `tintColor`（`Color.byTier`：1 级浅灰、2 级黄、
 *    3 级青、创造品红）；对应的模型 `models/block/case*.json` 必须继承
 *    `opencomputers_neo:block/tinted_cube` 才能让 `tintindex: 0`（即 `tintColor`
 *    的返回值）作用到各个面。
 *  - `getIcon` / `iconsOn`（运行时指示灯贴图）/ `registerBlockIcons` / `customTextures` 全部删除。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = `CaseTop`，北 = `CaseBack`，南 = `CaseFront`，西 / 东 = `CaseSide`。
 * 原运行时贴图：`CaseBackOn`（北，运行中）、`CaseSideOn`（西 / 东，运行中）
 * ——需要客户端按方块实体状态换模型。
 * TODO(客户端): `li.cil.oc.client` 移植后用状态化模型或 `BlockEntityRenderer` 恢复指示灯。
 */
class Case(val tier: Int, properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) with traits.PowerAcceptor with traits.StateAware with traits.GUI {

  override def rarity(stack: ItemStack) = Rarity.byTier(tier)

  // ----------------------------------------------------------------------- //

  override def tooltipBody(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    tooltip.addAll(Tooltip.get(getClass.getSimpleName, slots))
  }

  private def slots = tier match {
    case 0 => "2/1/1"
    case 1 => "2/2/2"
    case 2 | 3 => "3/2/3"
    case _ => "0/0/0"
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.caseRate(tier)

  override def guiType = GuiType.Case

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Case(pos, state)

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    if (player.isShiftKeyDown) {
      if (!level.isClientSide) level.getBlockEntity(pos) match {
        // TODO(server.machine): 机器层未完成时 `machine` 可能为 `null`，这里做空值保护。
        case computer: tileentity.Case if computer.machine != null && !computer.machine.isRunning && computer.isUseableByPlayer(player) =>
          computer.machine.start()
        case _ =>
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else super.useBlock(state, level, pos, player, hit)
  }

  // ----------------------------------------------------------------------- //
  // 破坏（原 `removedByPlayer` 的所有权检查）
  // ----------------------------------------------------------------------- //

  override def playerDestroyBlock(state: BlockState, level: Level, pos: BlockPos, player: Player,
                                  blockEntity: BlockEntity, tool: ItemStack): Unit = {
    // 原：`if (c.isCreative && (!player.capabilities.isCreativeMode || !c.canInteract(name))) false`
    //     `else c.canInteract(name) && super.removedByPlayer(...)`
    val allowed = blockEntity match {
      case c: tileentity.Case =>
        val name = player.getGameProfile.getName
        !(c.isCreative && (!player.isCreative || !c.canInteract(name))) && c.canInteract(name)
      case _ => true
    }
    if (allowed) {
      super.playerDestroyBlock(state, level, pos, player, blockEntity, tool)
    }
    else {
      // TODO(方块): 见类注释——1.21.1 无法在此取消破坏；这里只做最小可用处理
      // （不执行 `CustomDrops` 之外的任何额外动作，并让方块实体落盘以免状态丢失）。
      blockEntity match {
        case c: tileentity.Case => c.markDirty()
        case _ =>
      }
    }
  }
}
