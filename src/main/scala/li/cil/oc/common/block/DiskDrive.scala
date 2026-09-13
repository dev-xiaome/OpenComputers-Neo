package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

/**
 * 软驱（原 1.7.10 `DiskDrive`）。
 *
 * 1.21.1 迁移要点：
 *  - `hasComparatorInputOverride` / `getComparatorInputOverride` 钩子改写为
 *    [[SimpleBlockHooks.providesAnalogOutput]] / [[SimpleBlockHooks.analogOutputSignal]]；
 *    原判空 `getStackInSlot(0) != null` 改为 `!isEmpty`（移植后的 `IItemHandler` 返回
 *    `ItemStack.EMPTY` 而不是 `null`）。
 *  - `isItemValidForSlot` → `IItemHandler#isItemValid`。
 *  - `player.inventory.decrStackSize(currentItem, 1)` → `player.getMainHandItem.split(1)`
 *    （1.21.1 的 `Inventory` 不再提供按「当前选中槽」取放的方法）。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：潜行时插/取磁盘，否则交给 GUI trait。
 *  - 提示尾部原本在装有 ComputerCraft 时追加 `.CC` 一行，
 *    TODO(integration): CC 集成不再移植，相关分支删除。
 *  - `getIcon` / `customTextures` 删除，面纹理改由模型 JSON 指定。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = 未指定（沿用 `GenericTop`），北 = `DiskDriveSide`，南 = `DiskDriveFront`，
 * 西 / 东 = `DiskDriveSide`。
 */
class DiskDrive(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.GUI {

  override def guiType = GuiType.DiskDrive

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.DiskDrive(pos, state)

  // ----------------------------------------------------------------------- //
  // 比较器
  // ----------------------------------------------------------------------- //

  override def providesAnalogOutput = true

  override def analogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
    level.getBlockEntity(pos) match {
      case drive: tileentity.DiskDrive if !drive.getStackInSlot(0).isEmpty => 15
      case _ => 0
    }

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  /**
   * 行为：潜行 → 插入（已有磁盘时先弹出），不潜行 → 打开 GUI。
   */
  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    if (player.isShiftKeyDown) level.getBlockEntity(pos) match {
      case drive: tileentity.DiskDrive =>
        val isDiskInDrive = !drive.getStackInSlot(0).isEmpty
        val isHoldingDisk = drive.isItemValid(0, player.getMainHandItem)
        if (isDiskInDrive) {
          if (!level.isClientSide) {
            drive.dropSlot(0, 1, Option(drive.facing))
          }
        }
        if (isHoldingDisk) {
          // Insert the disk.
          drive.setInventorySlotContents(0, player.getMainHandItem.split(1))
        }
        if (isDiskInDrive || isHoldingDisk) InteractionResult.sidedSuccess(level.isClientSide)
        else InteractionResult.PASS
      case _ => InteractionResult.PASS
    }
    else super.useBlock(state, level, pos, player, hit)
  }
}
