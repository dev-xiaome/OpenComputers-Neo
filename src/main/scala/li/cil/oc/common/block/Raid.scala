package li.cil.oc.common.block

import java.util

import li.cil.oc.common.GuiType
import li.cil.oc.common.item.data.RaidData
import li.cil.oc.common.tileentity
import li.cil.oc.util.TooltipKeyBindings
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

import scala.reflect.{ClassTag, classTag}

/**
 * RAID 阵列（原 1.7.10 `Raid`）。
 *
 * 1.21.1 迁移要点：
 *  - 原构造参数 `(implicit val tileTag: ClassTag[tileentity.Raid])` 由 OC 注册层传入；
 *    1.21.1 的注册层写 `new block.Raid()`，因此 ClassTag 改为在 [[tileTag]] 里直接物化。
 *  - `hasComparatorInputOverride` / `getComparatorInputOverride` →
 *    [[SimpleBlockHooks.providesAnalogOutput]] / [[SimpleBlockHooks.analogOutputSignal]]。
 *  - `hasTileEntity` / `createTileEntity` → [[SimpleBlockHooks.createBlockEntity]]
 *    （`hasBlockEntity` 默认 `true`）。
 *  - 提示尾部原本在按住扩展提示键时列出内部磁盘，
 *    `client.KeyBindings` 未移植 → 改用 [[TooltipKeyBindings]]（占位恒为 `false`）。
 *  - `disk.getDisplayName` → `disk.getHoverName.getString`（1.21.1 的组件式名称）。
 *  - `NBT#hasNoTags` → `CompoundTag#isEmpty`、`getCompoundTag` → `getCompound`。
 *  - `dropBlockAsItem(world, x, y, z, stack)` → `Block.popResource`。
 *  - `setInventorySlotContents` 保留：它的「先移除、再添加」通知顺序是
 *    `onItemAdded` → `tryCreateRaid` 的关键（见 `common.inventory.Inventory`）。
 *  - `getIcon` / `customTextures` / `registerBlockIcons` 删除，面纹理改由模型 JSON 指定。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 / 上 = 未指定（沿用 `GenericTop`），北 = `RaidSide`，南 = `RaidFront`，
 * 西 / 东 = `RaidSide`。
 */
class Raid(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends SimpleBlock(properties) with traits.GUI with traits.CustomDrops[tileentity.Raid] {

  override def tileTag: ClassTag[tileentity.Raid] = classTag[tileentity.Raid]

  // ----------------------------------------------------------------------- //
  // 提示
  // ----------------------------------------------------------------------- //

  override def tooltipTail(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    super.tooltipTail(stack, player, tooltip, advanced)
    // 原：`if (KeyBindings.showExtendedTooltips)`
    if (TooltipKeyBindings.showExtendedTooltips) {
      val data = new RaidData(stack)
      for (disk <- data.disks if disk != null && !disk.isEmpty) {
        tooltip.add("- " + disk.getHoverName.getString)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override def guiType = GuiType.Raid

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Raid(pos, state)

  // ----------------------------------------------------------------------- //
  // 比较器
  // ----------------------------------------------------------------------- //

  override def providesAnalogOutput = true

  override def analogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int =
    level.getBlockEntity(pos) match {
      case raid: tileentity.Raid if raid.presence.forall(ok => ok) => 15
      case _ => 0
    }

  // ----------------------------------------------------------------------- //
  // 放置 / 掉落
  // ----------------------------------------------------------------------- //

  override def doCustomInit(tileEntity: tileentity.Raid, player: LivingEntity, stack: ItemStack): Unit = {
    super.doCustomInit(tileEntity, player, stack)
    if (tileEntity.isServer) {
      val data = new RaidData(stack)
      for (i <- 0 until math.min(data.disks.length, tileEntity.getSlots)) {
        tileEntity.setInventorySlotContents(i, data.disks(i))
      }
      data.label.foreach(tileEntity.label.setLabel)
      if (!data.filesystem.isEmpty) {
        tileEntity.tryCreateRaid(data.filesystem.getCompound("node").getString("address"))
        // 原：`tileEntity.filesystem.foreach(_.load(data.filesystem))`。
        // TODO(server.component.FileSystem): 原实现加载的是 `server.component.FileSystem`
        // 托管环境（同时恢复节点状态）；该组件未移植，这里退化为加载底层 `api.fs.FileSystem`
        // 的内容（节点的恢复由 `tryCreateRaid` 用保存的地址完成）。
        tileEntity.fileSystem.foreach(fileSystem => fileSystem.load(data.filesystem))
      }
    }
  }

  override def doCustomDrops(tileEntity: tileentity.Raid, player: Player, willHarvest: Boolean): Unit = {
    super.doCustomDrops(tileEntity, player, willHarvest)
    val stack = createItemStack()
    if (tileEntity.items.exists(_.isDefined)) {
      val data = new RaidData()
      data.disks = tileEntity.items.map(_.orNull)
      // TODO(server.component.FileSystem): 同上，原来保存的是托管环境（含节点状态）。
      tileEntity.fileSystem.foreach(fileSystem => fileSystem.save(data.filesystem))
      data.label = Option(tileEntity.label.getLabel)
      data.save(stack)
    }
    // 原：`dropBlockAsItem(world, x, y, z, stack)`
    Block.popResource(tileEntity.world, tileEntity.blockPos, stack)
  }
}
