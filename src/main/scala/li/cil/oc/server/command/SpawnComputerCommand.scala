package li.cil.oc.server.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.tileentity
import li.cil.oc.integration.opencomputers.DriverAPU
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.BlockHitResult

/**
 * `/oc_spawnComputer`（别名 `/oc_sc`）：在玩家视线所指的方块上直接搭出一台创造模式计算机
 * （机箱 + 二级屏幕 + 键盘，并塞满创造级 APU / 内存 / 硬盘 / BIOS / OpenOS）。
 *
 * ==1.21.1 迁移要点==
 *  - 命令从 `ICommandSender` + `processCommand(ICommandSender, Array[String])` 改为 Brigadier 的
 *    `LiteralArgumentBuilder` + `executes`（见 [[SimpleCommand]]）；
 *  - 视线检测：`world.rayTraceBlocks(origin, lookAt)` → `Player#pick(distance, 0f, false)`，
 *    命中方块用 [[BlockHitResult]] 判定，坐标与面从它身上取；
 *  - `world.getTileEntity(pos)` → `world.getBlockEntity(pos)`；`world.isAirBlock(pos)` → `world.isEmptyBlock(pos)`；
 *  - `world.setBlock(x, y, z, block)` → `world.setBlock(pos, block.defaultBlockState(), 3)`；
 *  - `player.addChatMessage(...)` → `player.displayClientMessage(component, false)`；
 *  - `WrongUsageException` → Brigadier 的 `SimpleCommandExceptionType`。
 *
 * ==降级说明==
 *  - 原 `LuaStateFactory.setDefaultArch(apu)`（把**原生 Lua** 架构写进 APU 的 NBT）随
 *    `server/machine/luac` 一起未移植，改为写入默认（LuaJ）架构，见 [[spawn]] 里的 TODO。
 */
object SpawnComputerCommand extends SimpleCommand("oc_spawnComputer") {
  aliases += "oc_sc"

  final val MaxDistance = 16

  private final val RequiredPermissionLevel = 2

  override def build(builder: LiteralArgumentBuilder[CommandSourceStack]): Unit = {
    builder.requires(source => hasOpLevel(source, RequiredPermissionLevel))
    builder.executes(context => {
      spawn(context.getSource)
      1
    })
  }

  private def spawn(source: CommandSourceStack): Unit = {
    val player = source.getPlayer
    if (player == null) {
      // 等价于 1.7.10 的 `throw new WrongUsageException("Can only be used by players.")`。
      throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
        Component.literal("Can only be used by players.")).create()
    }
    // `pick` 即 1.7.10 的 `rayTraceBlocks`：从眼睛位置沿视线追踪 MaxDistance 格。
    player.pick(MaxDistance.toDouble, 0f, false) match {
      case hit: BlockHitResult =>
        val world = player.level()
        val hitPos = BlockPosition(hit.getBlockPos.getX, hit.getBlockPos.getY, hit.getBlockPos.getZ, world)
        val casePos = hitPos.offset(hit.getDirection)
        val screenPos = casePos.offset(Direction.UP)
        val keyboardPos = screenPos.offset(Direction.UP)

        if (!world.isEmptyBlock(casePos.toChunkCoordinates) ||
          !world.isEmptyBlock(screenPos.toChunkCoordinates) ||
          !world.isEmptyBlock(keyboardPos.toChunkCoordinates)) {
          player.displayClientMessage(Component.literal("Target position obstructed."), false)
          return
        }

        def rotateProperly(pos: BlockPosition): tileentity.traits.Rotatable = {
          world.getBlockEntity(pos.toChunkCoordinates) match {
            case rotatable: tileentity.traits.Rotatable =>
              rotatable.setFromEntityPitchAndYaw(player)
              if (!rotatable.validFacings.contains(rotatable.pitch)) {
                rotatable.pitch = rotatable.validFacings.headOption.getOrElse(Direction.NORTH)
              }
              rotatable.invertRotation()
              rotatable
            case _ => null // not rotatable
          }
        }

        world.setBlock(casePos.toChunkCoordinates, api.Items.get(Constants.BlockName.CaseCreative).block().defaultBlockState(), 3)
        rotateProperly(casePos)
        world.setBlock(screenPos.toChunkCoordinates, api.Items.get(Constants.BlockName.ScreenTier2).block().defaultBlockState(), 3)
        rotateProperly(screenPos) match {
          case rotatable: tileentity.traits.Rotatable => rotatable.pitch match {
            case Direction.UP | Direction.DOWN =>
              rotatable.pitch = Direction.NORTH
            case _ => // nothing to do here, pitch is fine
          }
          case _ => // ???
        }
        world.setBlock(keyboardPos.toChunkCoordinates, api.Items.get(Constants.BlockName.Keyboard).block().defaultBlockState(), 3)
        world.getBlockEntity(keyboardPos.toChunkCoordinates) match {
          case t: tileentity.traits.Rotatable =>
            t.setFromEntityPitchAndYaw(player)
            t.setFromFacing(Direction.UP)
          case _ => // ???
        }

        api.Network.joinOrCreateNetwork(world.getBlockEntity(casePos.toChunkCoordinates))

        val apu = api.Items.get(Constants.ItemName.APUCreative).createItemStack(1)
        // TODO(port): 1.7.10 是 `LuaStateFactory.setDefaultArch(apu)`（把**原生 Lua** 架构
        // 写进 APU 的 NBT）。原生 Lua（`server/machine/luac`）未移植，改为写入默认架构
        // （LuaJ，由 `server.Proxy.preInit` 注册）。若架构还没注册（例如主类没有走服务端代理），
        // 就不给 APU 打架构标签，机器启动时会自行回退到 `api.Machine.LuaArchitecture`。
        Option(api.Machine.LuaArchitecture).foreach(arch => DriverAPU.setArchitecture(apu, arch))

        InventoryUtils.insertIntoInventoryAt(apu, casePos)
        InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.RAMTier6).createItemStack(2), casePos)
        InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.HDDTier3).createItemStack(1), casePos)
        InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.LuaBios).createItemStack(1), casePos)
        InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.OpenOS).createItemStack(1), casePos)
      case _ =>
        player.displayClientMessage(Component.literal("You need to be looking at a nearby block."), false)
    }
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.
}
