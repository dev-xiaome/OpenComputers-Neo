package li.cil.oc.common.item

import java.util

import li.cil.oc.Settings
import li.cil.oc.common.item.data.DroneData
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.Rarity
import li.cil.oc.util.TooltipKeyBindings
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * 「无人机（物品形态）」（原 `li.cil.oc.common.item.Drone`）。
 *
 * 降级说明（依赖未移植内容）：
 *  - `li.cil.oc.common.entity.Drone`（无人机实体）与 `li.cil.oc.server.agent.Player`
 *    （机器人/无人机的假玩家）都尚未移植，因此 [[onItemUse]] 目前**不会放置无人机**，
 *    只保留提示信息与品质显示；等实体层移植完成后按原实现恢复放置逻辑。
 *  - `li.cil.oc.integration.util.NEI.hide(this)` 属于第三方模组集成，整体移除。
 *  - `client.KeyBindings.showExtendedTooltips` → 已移植的
 *    [[li.cil.oc.util.TooltipKeyBindings]]（`client` 包未移植时的占位）。
 *  - `registerIcons` 已删除（1.21.1 走模型 JSON）。
 */
class Drone(props: Item.Properties) extends Item(props) with traits.Delegate {

  showInItemList = false

  override protected def tooltipExtended(stack: ItemStack, tooltip: util.List[String]): Unit = {
    if (TooltipKeyBindings.showExtendedTooltips) {
      val info = new DroneData(stack)
      for (component <- info.components if component != null && !component.isEmpty) {
        tooltip.add("- " + component.getHoverName.getString)
      }
    }
  }

  override def onItemUse(stack: ItemStack, player: Player, position: BlockPosition,
                         side: Int, hitX: Float, hitY: Float, hitZ: Float): Boolean = {
    // TODO(实体): 需要 `common/entity/Drone`（无人机实体）与
    // `server/agent/Player`（假玩家）移植完成后恢复放置逻辑：
    //   1. `new entity.Drone(world)` 并设置 ownerName / ownerUUID
    //   2. `drone.initializeAfterPlacement(stack, player, position.offset(...))`
    //   3. `world.addFreshEntity(drone)` 并 `stack.shrink(1)`
    false
  }
}

object Drone {
  /** 品质按堆叠里存的实际等级显示（原 `rarity(stack)` 的实现）。 */
  def rarityOf(stack: ItemStack): net.minecraft.world.item.Rarity =
    Rarity.byTier(new DroneData(stack).tier)

  /**
   * 创造模式标签页里的预配置无人机（原 `Items.createConfiguredDrone`）。
   *
   * 变异说明：
   *  - 原版会用 `LuaStateFactory.setDefaultArch(...)` 给 CPU 写入默认架构，
   *    但 `server/machine/luac` 未移植，这里退化为直接使用 CPU 堆叠（等价于默认架构）。
   *  - `safeGetStack` 语义：物品未注册时得到 `null`，这里统一转成 `ItemStack.EMPTY`。
   */
  def createConfiguredDrone(): ItemStack = {
    import li.cil.oc.Constants.ItemName
    import li.cil.oc.common.Tier

    def safeGetStack(name: String): ItemStack = {
      val info = li.cil.oc.api.Items.get(name)
      if (info == null) ItemStack.EMPTY else info.createItemStack(1)
    }

    val data = new DroneData()
    data.name = "Crecopter"
    data.tier = Tier.Four
    data.storedEnergy = Settings.get.bufferDrone.toInt
    data.components = Array(
      safeGetStack(ItemName.InventoryUpgrade),
      safeGetStack(ItemName.InventoryUpgrade),
      safeGetStack(ItemName.InventoryControllerUpgrade),
      safeGetStack(ItemName.TankUpgrade),
      safeGetStack(ItemName.TankControllerUpgrade),
      safeGetStack(ItemName.LeashUpgrade),

      safeGetStack(ItemName.WirelessNetworkCardTier2),

      // TODO(架构): 恢复 `LuaStateFactory.setDefaultArch(safeGetStack(ItemName.CPUTier3))`
      safeGetStack(ItemName.CPUTier3),
      safeGetStack(ItemName.RAMTier6),
      safeGetStack(ItemName.RAMTier6)
    )

    data.createItemStack()
  }
}
