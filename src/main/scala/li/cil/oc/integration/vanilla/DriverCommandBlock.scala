package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{Block, Blocks}
import net.minecraft.world.level.block.entity.CommandBlockEntity

/**
 * 命令方块（`CommandBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityCommandBlock` → `CommandBlockEntity`；`func_145993_a` → `getCommandBlock`（`BaseCommandBlock`）
 *  - `func_145753_i` → `BaseCommandBlock#getCommand`
 *  - `func_145752_a(v)` → `BaseCommandBlock#setCommand(v)`；
 *    `world.markBlockForUpdate(...)` → `BaseCommandBlock#onUpdated()`（内部就是 `sendBlockUpdated`）
 *  - `MinecraftServer.getServer.isCommandBlockEnabled` → `Level#getServer.isCommandBlockEnabled`
 *  - `func_145755_a(world)` → `BaseCommandBlock#performCommand(level)`
 *  - `func_145760_g` → `BaseCommandBlock#getSuccessCount`；
 *    `func_145749_h.getUnformattedText` → `BaseCommandBlock#getLastOutput.getString`
 *  - 1.7.10 用 metadata 区分三种命令方块，1.21.1 拆成了三个独立方块，
 *    因此此处三种命令方块统一由本驱动处理
 *  - TODO(port): `performCommand` 在 1.21.1 返回 `Boolean`（是否真正执行）而不报告失败原因，
 *    「命令方块被禁用」与「命令执行失败」在返回表里只能靠 successCount 区分。
 */
object DriverCommandBlock extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[CommandBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[CommandBlockEntity])

  final class Environment(entity: CommandBlockEntity) extends ManagedTileEntityEnvironment[CommandBlockEntity](entity, "command_block") with NamedBlock {
    override def preferredName = "command_block"

    override def priority = 0

    @Callback(direct = true, doc = "function():string -- Get the command currently set in this command block.")
    def getCommand(context: Context, args: Arguments): Array[AnyRef] = {
      result(tileEntity.getCommandBlock.getCommand)
    }

    @Callback(doc = "function(value:string) -- Set the specified command for the command block.")
    def setCommand(context: Context, args: Arguments): Array[AnyRef] = {
      val commandBlock = tileEntity.getCommandBlock
      commandBlock.setCommand(args.checkString(0))
      commandBlock.onUpdated()
      result(true)
    }

    @Callback(doc = "function():number -- Execute the currently set command. This has a slight delay to allow the command block to properly update.")
    def executeCommand(context: Context, args: Arguments): Array[AnyRef] = {
      context.pause(0.1)
      val level = tileEntity.getLevel
      val server = if (level == null) null else level.getServer
      if (server == null || !server.isCommandBlockEnabled) {
        result(null, "command blocks are disabled")
      } else {
        val commandBlock = tileEntity.getCommandBlock
        commandBlock.performCommand(level)
        result(commandBlock.getSuccessCount, commandBlock.getLastOutput.getString)
      }
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty) {
        val block = Block.byItem(stack.getItem)
        if (block == Blocks.COMMAND_BLOCK || block == Blocks.REPEATING_COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK)
          classOf[Environment]
        else null
      }
      else null
    }
  }

}
