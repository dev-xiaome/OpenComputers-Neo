package li.cil.oc.common.item.traits

import java.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.util.ItemStackNBTExtensions._
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * 文件系统类物品（软盘、硬盘等，原 1.7.10 的 `FileSystemLike`）。
 *
 * 1.21.1 迁移要点：
 *  - `stack.hasTagCompound` / `getTagCompound` → 隐式扩展 `hasTag()` / `getTag()`
 *    （底层是自定义数据组件 `opencomputers_neo:nbt`）
 *  - `CompoundTag#hasKey` → `contains`
 *  - `onItemRightClick` 里打开 GUI 的 `player.openGui(OpenComputers, GuiType.Drive.id, ...)`
 *    在 1.21.1 已不存在，改为 `player.openMenu(MenuProvider)`；菜单类型与
 *    `MenuProvider` 属于 `common/container` + `common/GuiHandler`（尚未移植），
 *    这里先留 TODO 占位。
 */
trait FileSystemLike extends Delegate {

  override protected def tooltipName: Option[String] = None

  /** 容量（KB），由具体物品给出。 */
  def kiloBytes: Int

  override def tooltipLines(stack: ItemStack, player: Player, tooltip: util.List[String], advanced: Boolean): Unit = {
    if (stack.hasTag()) {
      val nbt = stack.getTag()
      if (nbt.contains(Settings.namespace + "data")) {
        val data = nbt.getCompound(Settings.namespace + "data")
        if (data.contains(Settings.namespace + "fs.label")) {
          tooltip.add(data.getString(Settings.namespace + "fs.label"))
        }
        if (advanced && data.contains("fs")) {
          val fsNbt = data.getCompound("fs")
          if (fsNbt.contains("capacity.used")) {
            val used = fsNbt.getLong("capacity.used")
            tooltip.add(Localization.Tooltip.DiskUsage(used, kiloBytes * 1024))
          }
        }
      }
      val data = new DriveData(stack)
      tooltip.add(Localization.Tooltip.DiskMode(data.isUnmanaged))
      tooltip.add(Localization.Tooltip.DiskLock(data.lockInfo))
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }

  override def use(world: Level, player: Player, hand: InteractionHand): InteractionResultHolder[ItemStack] = {
    val stack = player.getItemInHand(hand)
    if (!player.isShiftKeyDown && (!stack.hasTag() || !stack.getTag().contains(Settings.namespace + "lootFactory"))) {
      // TODO(菜单): 1.21.1 用 `player.openMenu(new SimpleMenuProvider(...))` 打开容器，
      // 需要 `li.cil.oc.common.GuiHandler` 与 `MenuType`（`common/container`、`common/init`）
      // 移植完成后接线；此处先不打开，避免静默失败。
      player.swing(hand)
    }
    InteractionResultHolder.sidedSuccess(stack, world.isClientSide)
  }
}
