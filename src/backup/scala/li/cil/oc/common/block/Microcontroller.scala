package li.cil.oc.common.block

import java.util

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.common.tileentity
import li.cil.oc.util.{InventoryUtils, Rarity, Tooltip, TooltipKeyBindings}
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}
import net.minecraft.world.phys.BlockHitResult

import scala.reflect.{ClassTag, classTag}

/**
 * 单片机（原 1.7.10 `Microcontroller`）。
 *
 * 1.21.1 迁移要点：
 *  - 原构造参数 `(implicit val tileTag: ClassTag[tileentity.Microcontroller])` 由 OC 自己的
 *    注册层传入；1.21.1 的注册层写 `new block.Microcontroller()`（无参），因此 ClassTag
 *    改为由 [[tileTag]] 直接物化（`classTag[...]`），方块类不再需要构造参数。
 *  - `onBlockActivated` → [[SimpleBlockHooks.useBlock]]：
 *    潜行时空手 / 非 EEPROM → 什么也不做；空手右键 = 启动 / 停止；潜行 + EEPROM = 热插拔 EEPROM。
 *  - `getPickBlock`：TODO(方块): 1.21.1 对应 `IBlockExtension#getCloneItemStack`，
 *    但 `SimpleBlockHooks` 没有对应钩子，暂未移植（中间键拾取会得到「空白」单片机）；
 *    等 `SimpleBlock` 增加该钩子后改为返回 `mcu.info.copyItemStack()`。
 *  - `setCreativeTab(null)` / `NEI.hide(this)` 删除：1.21.1 的创造模式标签页与 NEI 隐藏由
 *    注册层负责（`Registry.hideBlockItemInCreativeTab`）。
 *    TODO(integration.util.NEI): NEI 集成未移植。
 *  - `player.inventory.decrStackSize(currentItem, 1)` → `player.getMainHandItem.split(1)`。
 *  - `getIcon` / `customTextures` / `registerBlockIcons` 删除，面纹理改由模型 JSON 指定。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = `MicrocontrollerTop`，北 = `MicrocontrollerSide`，南 = `MicrocontrollerFront`，
 * 西 / 东 = `MicrocontrollerSide`。
 */
class Microcontroller(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) with traits.PowerAcceptor with traits.StateAware
    with traits.CustomDrops[tileentity.Microcontroller] {

  override def tileTag: ClassTag[tileentity.Microcontroller] = classTag[tileentity.Microcontroller]

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override def tooltipTail(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(stack, player, tooltip, advanced)
    // 原：`if (KeyBindings.showExtendedTooltips)`（`li.cil.oc.client` 未移植，见 util.TooltipKeyBindings）
    if (TooltipKeyBindings.showExtendedTooltips) {
      val info = new MicrocontrollerData(stack)
      for (component <- info.components if component != null && !component.isEmpty) {
        // 1.21.1：`getDisplayName` → `getHoverName.getString`
        tooltip.add("- " + component.getHoverName.getString)
      }
    }
  }

  override def rarity(stack: ItemStack) = {
    val data = new MicrocontrollerData(stack)
    Rarity.byTier(data.tier)
  }

  // ----------------------------------------------------------------------- //

  override def energyThroughput = Settings.get.caseRate(Tier.One)

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Microcontroller(pos, state)

  // ----------------------------------------------------------------------- //
  // 交互
  // ----------------------------------------------------------------------- //

  override def useBlock(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult = {
    // 原：`Wrench.holdsApplicableWrench(player, BlockPosition(x, y, z))`
    val holdsWrench = false // TODO(integration.util.Wrench): 扳手集成移植后恢复判断
    // 注意：物品未注册时 `api.Items.get` 返回 `null`，必须先判空再比较，否则 `null == null` 会误判。
    val eeprom = api.Items.get(Constants.ItemName.EEPROM)
    val isHoldingEeprom = eeprom != null && api.Items.get(player.getMainHandItem) == eeprom
    if (holdsWrench) {
      InteractionResult.PASS
    }
    else if (!player.isShiftKeyDown) {
      if (!level.isClientSide) {
        level.getBlockEntity(pos) match {
          // TODO(server.machine): 机器层未完成时 `machine` 可能为 `null`，这里做空值保护。
          case mcu: tileentity.Microcontroller if mcu.machine != null =>
            if (mcu.machine.isRunning) mcu.machine.stop()
            else mcu.machine.start()
          case _ =>
        }
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else if (isHoldingEeprom) {
      if (!level.isClientSide) {
        level.getBlockEntity(pos) match {
          case mcu: tileentity.Microcontroller =>
            // 原：`player.inventory.decrStackSize(player.inventory.currentItem, 1)`
            val newEeprom = player.getMainHandItem.split(1)
            mcu.changeEEPROM(newEeprom) match {
              case Some(oldEeprom) => InventoryUtils.addToPlayerInventory(oldEeprom, player)
              case _ =>
            }
          case _ =>
        }
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else InteractionResult.PASS
  }

  // ----------------------------------------------------------------------- //
  // 放置 / 掉落
  // ----------------------------------------------------------------------- //

  override def doCustomInit(tileEntity: tileentity.Microcontroller, player: LivingEntity, stack: ItemStack): Unit = {
    super.doCustomInit(tileEntity, player, stack)
    if (tileEntity.isServer) {
      tileEntity.info.load(stack)
      tileEntity.snooperNode.changeBuffer(tileEntity.info.storedEnergy - tileEntity.snooperNode.localBuffer)
    }
  }

  override def doCustomDrops(tileEntity: tileentity.Microcontroller, player: Player, willHarvest: Boolean): Unit = {
    super.doCustomDrops(tileEntity, player, willHarvest)
    tileEntity.saveComponents()
    tileEntity.info.storedEnergy = tileEntity.snooperNode.localBuffer.toInt
    // 原：`dropBlockAsItem(world, x, y, z, ...)` → 1.21.1 的 `Block.popResource`。
    net.minecraft.world.level.block.Block.popResource(tileEntity.world, tileEntity.blockPos, tileEntity.info.createItemStack())
  }
}
