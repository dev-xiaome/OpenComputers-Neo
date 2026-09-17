package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.{BlockBehaviour, BlockState}

/**
 * 航点（原 1.7.10 `Waypoint`，导航升级用的命名坐标点）。
 *
 * 1.21.1 迁移要点：
 *  - 继承 [[RedstoneAware]] 并混入 [[traits.GUI]]：原实现是「非潜行时打开 GUI、潜行时
 *    走默认交互」，这正是 [[traits.GUI]] 的语义（`guiType` = `GuiType.Waypoint`）。
 *    TODO(GUI): 原 `player.openGui(OpenComputers, GuiType.Waypoint.id, ...)` 是**纯客户端**
 *    调用；1.21.1 需要 `MenuProvider` + `Registry.registerMenu`，等菜单层移植后补齐。
 *  - 原 `getValidRotations`（排除当前 `facing` 及其反向）→ [[SimpleBlockHooks.validRotations]]：
 *    1.21.1 的该钩子拿不到世界与坐标（扳手集成未移植），因此这里返回除当前朝向与其反向
 *    之外的所有方向——它是 `getValidRotations` 的超集，不会**少**允许任何旋转；
 *    精确按朝向过滤等原 `Rotatable#rotate` 的上下文接口移植后恢复。
 *  - `createTileEntity` → `createBlockEntity`，构造为 `new tileentity.Waypoint(pos, state)`。
 *
 * 纹理（原 `customTextures` 面序 DOWN, UP, NORTH, SOUTH, WEST, EAST）：
 * 下 = 未指定（沿用 `GenericTop`），上 = `WaypointTop`，北 = `WaypointBack`，
 * 南 = `WaypointFront`，西 / 东 = `WaypointSide`。
 */
class Waypoint(properties: BlockBehaviour.Properties = SimpleBlock.properties())
  extends RedstoneAware(properties) with traits.GUI {

  override def guiType = GuiType.Waypoint

  override def createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
    new tileentity.Waypoint(pos, state)

  // ----------------------------------------------------------------------- //

  override def validRotations: Array[Direction] = Direction.values()
}
