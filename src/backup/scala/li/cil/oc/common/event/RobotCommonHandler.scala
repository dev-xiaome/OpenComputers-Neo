package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.api.event.RobotUsedToolEvent
import li.cil.oc.api.internal.Robot
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.UpgradeHover
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import net.minecraft.core.Direction
import net.neoforged.neoforge.common.NeoForge

/**
 * 机器人通用逻辑：工具损耗减免与飞行高度限制。
 *
 * 1.21.1 迁移要点：
 *  - `@SubscribeEvent` → 显式 `addListener`（见 [[initialize]]）。
 *  - `ItemStack#isItemStackDamageable` → `isDamageableItem`；
 *    `getItemDamage` / `setItemDamage` → `getDamageValue` / `setDamageValue`。
 *  - `EntityPlayer#getRNG` → `Entity#getRandom`。
 *  - `ForgeDirection.VALID_DIRECTIONS` → `Direction.values()`。
 *  - `robot.equipmentInventory` / `mainInventory` 现在是 `IItemHandler`：
 *    `getSizeInventory` → `getSlots`。
 */
object RobotCommonHandler {
  /** 注册监听器；由主类（或 [[EventHandlers]]）调用一次。 */
  def initialize(): Unit = {
    NeoForge.EVENT_BUS.addListener((e: RobotUsedToolEvent.ApplyDamageRate) => onRobotApplyDamageRate(e))
    NeoForge.EVENT_BUS.addListener((e: RobotMoveEvent.Pre) => onRobotMove(e))
  }

  def onRobotApplyDamageRate(e: RobotUsedToolEvent.ApplyDamageRate): Unit = {
    if (e.toolAfterUse.isDamageableItem) {
      val damage = e.toolAfterUse.getDamageValue - e.toolBeforeUse.getDamageValue
      if (damage > 0) {
        val actualDamage = damage * e.getDamageRate
        val player = e.agent.player
        val repairedDamage = if (player != null && player.getRandom.nextDouble() > 0.5)
          damage - math.floor(actualDamage).toInt
        else
          damage - math.ceil(actualDamage).toInt
        e.toolAfterUse.setDamageValue(e.toolAfterUse.getDamageValue - repairedDamage)
      }
    }
  }

  def onRobotMove(e: RobotMoveEvent.Pre): Unit = {
    if (Settings.get.limitFlightHeight >= 0) e.agent match {
      case robot: Robot =>
        val world = robot.world
        var maxFlyingHeight = Settings.get.limitFlightHeight

        (0 until robot.equipmentInventory.getSlots).
          map(robot.equipmentInventory.getStackInSlot).
          map(Delegator.subItem).
          collect { case Some(item: UpgradeHover) => maxFlyingHeight = math.max(maxFlyingHeight, Settings.get.upgradeFlightHeight(item.tier)) }

        (0 until robot.componentCount).
          map(_ + robot.mainInventory.getSlots + robot.equipmentInventory.getSlots).
          map(robot.getStackInSlot).
          map(Delegator.subItem).
          collect { case Some(item: UpgradeHover) => maxFlyingHeight = math.max(maxFlyingHeight, Settings.get.upgradeFlightHeight(item.tier)) }

        def isMovingDown = e.direction == Direction.DOWN
        // 1.21.1 的 `Level#getHeight()` 就是建筑高度上限（旧版为固定的 256）。
        def bypassesFlightLimit = maxFlyingHeight >= world.getHeight
        def hasAdjacentBlock(pos: BlockPosition) = Direction.values().exists(side => world.isSideSolid(pos.offset(side), side.getOpposite))
        def isWithinFlyingHeight(pos: BlockPosition) = (1 to maxFlyingHeight).exists(n => !world.isAirBlock(pos.offset(Direction.DOWN, n)))
        val startPos = BlockPosition(robot)
        val targetPos = startPos.offset(e.direction)
        // 自 1.5 起的新移动规则：
        // 1. 起点或终点合法即可移动（允许搭桥）。
        // 2. 机器人下方永远合法（总能向下移动）。
        // 3. 方块上方 <flightHeight> 格以内合法（有限的飞行能力）。
        // 4. 与某方块朝向本位置的实心面相邻的位置合法（机器人可以“攀爬”）。
        val validMove = isMovingDown ||
          bypassesFlightLimit ||
          hasAdjacentBlock(startPos) ||
          hasAdjacentBlock(targetPos) ||
          isWithinFlyingHeight(startPos)

        if (!validMove) {
          e.setCanceled(true)
        }
      case _ =>
    }
  }
}
