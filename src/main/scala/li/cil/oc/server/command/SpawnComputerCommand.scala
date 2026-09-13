package li.cil.oc.server.command

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.tileentity
import li.cil.oc.server.machine.luac.{LuaStateFactory, NativeLua53Architecture}
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld._
import li.cil.oc.util.InventoryUtils
import net.minecraft.commands.CommandSourceStack
import net.minecraft.command.WrongUsageException
import net.minecraft.world.entity.player.Player
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Direction

object SpawnComputerCommand extends SimpleCommand("oc_spawnComputer") {
  aliases += "oc_sc"

  final val MaxDistance = 16

  override def getCommandUsage(source: ICommandSender): String = name

  override def processCommand(source: ICommandSender, command: Array[String]): Unit = {
    source match {
      case player: Player =>
        val world = player.getEntityWorld
        val origin = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight, player.posZ)
        val direction = player.getLookVec
        val lookAt = origin.addVector(direction.xCoord * MaxDistance, direction.yCoord * MaxDistance, direction.zCoord * MaxDistance)
        world.rayTraceBlocks(origin, lookAt) match {
          case hit: HitResult if hit.typeOfHit == HitResult.MovingObjectType.BLOCK =>
            val hitPos = BlockPosition(hit.blockX, hit.blockY, hit.blockZ, world)
            val casePos = hitPos.offset(Direction.getOrientation(hit.sideHit))
            val screenPos = casePos.offset(Direction.UP)
            val keyboardPos = screenPos.offset(Direction.UP)

            if (!world.isAirBlock(casePos) || !world.isAirBlock(screenPos) || !world.isAirBlock(keyboardPos)) {
              player.addChatMessage(new ChatComponentText("Target position obstructed."))
              return
            }

            def rotateProperly(pos: BlockPosition):tileentity.traits.Rotatable = {
              world.getTileEntity(pos) match {
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

            world.setBlock(casePos, api.Items.get(Constants.BlockName.CaseCreative).block())
            rotateProperly(casePos)
            world.setBlock(screenPos, api.Items.get(Constants.BlockName.ScreenTier2).block())
            rotateProperly(screenPos) match {
              case rotatable: tileentity.traits.Rotatable => rotatable.pitch match {
                case Direction.UP | Direction.DOWN =>
                  rotatable.pitch = Direction.NORTH
                case _ => // nothing to do here, pitch is fine
              }
              case _ => // ???
            }
            world.setBlock(keyboardPos, api.Items.get(Constants.BlockName.Keyboard).block())
            world.getTileEntity(keyboardPos) match {
              case t: tileentity.traits.Rotatable =>
                t.setFromEntityPitchAndYaw(player)
                t.setFromFacing(Direction.UP)
              case _ => // ???
            }

            api.Network.joinOrCreateNetwork(world.getTileEntity(casePos))

            val apu = api.Items.get(Constants.ItemName.APUCreative).createItemStack(1)
            LuaStateFactory.setDefaultArch(apu)

            InventoryUtils.insertIntoInventoryAt(apu, casePos)
            InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.RAMTier6).createItemStack(2), casePos)
            InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.HDDTier3).createItemStack(1), casePos)
            InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.LuaBios).createItemStack(1), casePos)
            InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.OpenOS).createItemStack(1), casePos)
          case _ => player.addChatMessage(new ChatComponentText("You need to be looking at a nearby block."))
        }
      case _ => throw new WrongUsageException("Can only be used by players.")
    }
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.

  override def getRequiredPermissionLevel = 2
}
