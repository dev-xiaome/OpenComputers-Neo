package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * 软盘驱动器界面（原 1.7.10 的 `li.cil.oc.client.gui.DiskDrive`）。
 *
 * 1.7.10 里宿主是 `IInventory`；1.21.1 里它有两种形态：
 *  - 方块形态（`tileentity.DiskDrive`）；
 *  - 机架 / 物品形态（`DiskDriveMountableInventory` 幽灵物品栏）。
 * 两种都通过 `menu.otherInventory` 暴露，且都必须实现 `getInventoryName`，
 * 因此屏幕不需要区分宿主，直接用 [[li.cil.oc.common.container.Player#otherInventory]] 读名字。
 */
class DiskDrive(menu: container.DiskDrive, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.DiskDrive](menu, playerInventory, title) {

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    // 1.7.10 是 `drive.getInventoryName`（宿主实现 `IInventory`）；
    // 1.21.1 里宿主统一实现 `common.inventory.Inventory`，名字仍然从它上面取，
    // 因此屏幕不需要区分「方块形态」与「机架/物品形态」。
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.driveName),
      8, 6, 0x404040, false)
  }
}
