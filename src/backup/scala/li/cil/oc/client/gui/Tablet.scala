package li.cil.oc.client.gui

import li.cil.oc.Localization
import li.cil.oc.common.container
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
/**
 * 平板电脑界面（原 1.7.10 的 `li.cil.oc.client.gui.Tablet`）。
 *
 * 界面里只有一个「容器槽位」（放扩展卡 / 升级），代表平板当前暴露给玩家的那一格；
 * 真正的平板物品栏在于物品本身的 NBT 上，因此这个槽位是**幽灵槽位**，
 * 必须混入 [[traits.LockedHotbar]] 把「代表平板本体」的那一格锁住，
 * 否则玩家能把自己手里的平板拖进自己的界面里。
 *
 * ==1.21.1 迁移要点==
 *  - 屏幕构造器固定为 `(menu, playerInventory, title)`；宿主（平板物品栏）由
 *    `MenuTypes` 的客户端工厂从载荷里的主手物品快照重建。
 *  - `lockedStack` 从原来的 `tablet.stack` 改成 [[li.cil.oc.common.container.Tablet#lockedStack]]
 *    ——「哪一份堆叠是平板本体」由容器统一回答（悬浮靴子等其它宿主也能复用这个约定）。
 *  - `drawSecondaryForegroundLayer(mouseX, mouseY)` 增加 `GuiGraphics` 参数。
 *
 * TODO(common.inventory + server.component.Tablet): 连接收载荷、重建平板物品栏的
 *   `MenuTypes.Tablet` 工厂目前仍返回 `null`（`TabletWrapper` / `TabletCaseInventory`
 *   都还没移植），因此本界面在客户端**暂时打不开**。等平板的物品栏层补齐后，
 *   这里不需要再改（只是 `MenuType` 工厂会开始返回真实容器）。
 */
class Tablet(menu: container.Tablet, playerInventory: Inventory, title: Component)
  extends DynamicGuiContainer[container.Tablet](menu, playerInventory, title) with traits.LockedHotbar {

  override def lockedStack = menu.lockedStack

  override protected def drawSecondaryForegroundLayer(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int): Unit = {
    super.drawSecondaryForegroundLayer(guiGraphics, mouseX, mouseY)
    guiGraphics.drawString(font,
      Localization.localizeImmediately(menu.tabletName),
      8, 6, 0x404040, false)
  }
}
