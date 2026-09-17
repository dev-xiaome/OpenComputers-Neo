package li.cil.oc.common.block.traits

import li.cil.oc.common.GuiType
import li.cil.oc.common.block.SimpleBlockHooks
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

/**
 * 右键打开 GUI 的方块（对应 1.7.10 的 `block.traits.GUI`）。
 *
 * 1.21.1 的 GUI 打开方式是 `Player#openMenu(MenuProvider)` + 已注册的 `MenuType`
 * （1.7.10 是 `player.openGui(OpenComputers, guiType.id, world, x, y, z)`）。
 * 分发逻辑集中在 [[li.cil.oc.common.GuiHandler.openGui]]：
 * 它会按 `guiType` 的 [[GuiType.Category]] 选载荷（方块坐标 / 实体 id / 主手物品），
 * 再用 [[li.cil.oc.common.container.MenuOpening]] 建出会写「宿主载荷」的 `MenuProvider`。
 *
 * 两点必须注意：
 *  - 只在**服务端**调用 `openMenu`：`Player#openMenu` 在客户端只是个空实现，
 *    真正的容器由服务端建立后通过 `ClientboundOpenScreenPacket` 通知客户端重建；
 *  - 交互结果在两侧都要返回 `sidedSuccess`，否则客户端的预测会把手里的动作回滚
 *    （表现为「右键没反应」）。
 *
 * `guiType` 是纯客户端界面的方块（[[GuiType.Screen]] / [[GuiType.Waypoint]]）由
 * [[li.cil.oc.common.GuiHandler.hasServerMenu]] 拦下，此处不做特殊处理。
 */
trait GUI extends SimpleBlockHooks {
  // 注意：Scala 的自类型不会被继承，[[SimpleBlockHooks]] 的子 trait 必须重新声明 `self: Block`。
  self: net.minecraft.world.level.block.Block =>

  def guiType: GuiType.EnumVal

  override def useBlock(state: BlockState, level: Level, pos: BlockPos,
                        player: Player, hit: BlockHitResult): InteractionResult = {
    if (!player.isShiftKeyDown) {
      if (!level.isClientSide) {
        li.cil.oc.server.GuiHandler.openGui(guiType.id, player, level, pos.getX, pos.getY, pos.getZ)
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else super.useBlock(state, level, pos, player, hit)
  }
}
