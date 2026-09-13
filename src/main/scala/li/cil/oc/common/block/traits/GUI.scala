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
 * 1.21.1 的 GUI 打开方式是 `Player#openMenu(MenuProvider)` + 已注册的 `MenuType`，
 * 而 `li.cil.oc.common.container` 与 `li.cil.oc.client.gui` 尚未移植，因此这里保留
 * 「交互已被消费」的语义（返回 `InteractionResult.sidedSuccess`），暂不真正打开界面。
 */
trait GUI extends SimpleBlockHooks {
  // 注意：Scala 的自类型不会被继承，[[SimpleBlockHooks]] 的子 trait 必须重新声明 `self: Block`。
  self: net.minecraft.world.level.block.Block =>

  def guiType: GuiType.EnumVal

  override def useBlock(state: BlockState, level: Level, pos: BlockPos,
                        player: Player, hit: BlockHitResult): InteractionResult = {
    if (!player.isShiftKeyDown) {
      if (!level.isClientSide) {
        // TODO(GUI): 1.7.10 为 `player.openGui(OpenComputers, guiType.id, world, x, y, z)`。
        // 1.21.1 需要 `MenuProvider`（由 `common/container` 提供）与 `Registry.registerMenu`
        // 注册的 `MenuType`，并配合客户端 `AbstractContainerScreen`。待这两层移植完成后改为：
        //   player.openMenu(new SimpleMenuProvider((id, inv, p) => new XxxMenu(id, inv, ...), title))
      }
      InteractionResult.sidedSuccess(level.isClientSide)
    }
    else super.useBlock(state, level, pos, player, hit)
  }
}
