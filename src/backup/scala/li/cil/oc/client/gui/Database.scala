package li.cil.oc.client.gui

import li.cil.oc.client.Textures
import li.cil.oc.common.Tier
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * 数据库升级界面（原 1.7.10 的 `li.cil.oc.client.gui.Database`）。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`；数据库物品栏由
 *    `MenuTypes` 的客户端工厂从载荷里的**主手物品快照**重建（幽灵物品栏）。
 *  - `ySize = 256` → `imageHeight = 256`（[[CustomGuiContainer.ySize]] 是它的旧名字包装）。
 *  - 底图由三张贴图叠出来：`guiDatabase` 是底，等级 ≥ 2 再叠 `guiDatabase1`，
 *    等级 ≥ 3 再叠 `guiDatabase2`（与原实现一致）。
 *  - `drawGuiContainerBackgroundLayer` → [[CustomGuiContainer.drawSecondaryBackgroundLayer]]。
 */
class Database(menu: container.Database, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Database](menu, playerInventory, title) with traits.LockedHotbar {

  // 1.7.10 的 `ySize = 256`：数据库界面比标准界面高（幽灵槽位 + 玩家物品栏）。
  imageHeight = 256

  override def lockedStack = menu.databaseInventory.container

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    // 数据库界面刻意**不**画任何前景（与原实现一致）。
  }

  override protected def drawSecondaryBackgroundLayer(guiGraphics: GuiGraphics): Unit = {
    guiGraphics.blit(Textures.guiDatabase, leftPos, topPos, 0, 0, imageWidth, imageHeight)

    if (menu.databaseInventory.tier > Tier.One) {
      guiGraphics.blit(Textures.guiDatabase1, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    }

    if (menu.databaseInventory.tier > Tier.Two) {
      guiGraphics.blit(Textures.guiDatabase2, leftPos, topPos, 0, 0, imageWidth, imageHeight)
    }
  }
}
