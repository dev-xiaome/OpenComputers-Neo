package li.cil.oc.server.command

import com.mojang.brigadier.CommandDispatcher
import li.cil.oc.{api, Constants}
import li.cil.oc.common.Loot
import li.cil.oc.common.blockentity.{Case => CaseBlockEntity}
import li.cil.oc.common.blockentity.traits.Rotatable
import li.cil.oc.common.init.OCBlocks
import li.cil.oc.server.machine.luac.LuaStateFactory
import net.minecraft.commands.{Commands, CommandSourceStack}
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.{BlockHitResult, HitResult}

object SpawnComputerCommand {
  final val MaxDistance = 16.0
  final val DefaultScreenTier = 2

  def register(dispatcher: CommandDispatcher[CommandSourceStack]): Unit = {
    def command(name: String) = Commands.literal(name)
      .requires(CommandHandler.canUse(_, 2))
      .executes(context => execute(context.getSource, DefaultScreenTier, tieredComponents = false))
      .then(Commands.literal("1").executes(context => execute(context.getSource, 1, tieredComponents = true)))
      .then(Commands.literal("2").executes(context => execute(context.getSource, 2, tieredComponents = true)))
      .then(Commands.literal("3").executes(context => execute(context.getSource, 3, tieredComponents = true)))
      .then(Commands.literal("4").executes(context => execute(context.getSource, 4, tieredComponents = true)))

    dispatcher.register(command("oc_spawnComputer"))
    dispatcher.register(command("oc_spawncomputer"))
    dispatcher.register(command("oc_sc"))
  }

  private def execute(source: CommandSourceStack, screenTier: Int, tieredComponents: Boolean): Int = {
    val player = source.getPlayerOrException
    val level = player.serverLevel()

    player.pick(MaxDistance, 0.0f, false) match {
      case hit: BlockHitResult if hit.getType == HitResult.Type.BLOCK =>
        val casePos = (hit.getBlockPos.relative(hit.getDirection): net.minecraft.core.BlockPos)
        val screenPos = (casePos.above(): net.minecraft.core.BlockPos)
        val keyboardPos = (screenPos.above(): net.minecraft.core.BlockPos)

        if (!level.isEmptyBlock(casePos) || !level.isEmptyBlock(screenPos) || !level.isEmptyBlock(keyboardPos)) {
          source.sendFailure(Component.literal("Target position obstructed."))
          return 0
        }

        val componentNames = if (!tieredComponents) {
          Seq(
            Constants.ItemName.APUCreative,
            Constants.ItemName.RAMTier6,
            Constants.ItemName.RAMTier6,
            Constants.ItemName.HDDTier3)
        } else screenTier match {
          case 1 => Seq(
            Constants.ItemName.CPUTier1,
            Constants.ItemName.GraphicsCardTier1,
            Constants.ItemName.RAMTier2,
            Constants.ItemName.RAMTier2,
            Constants.ItemName.HDDTier1)
          case 2 => Seq(
            Constants.ItemName.CPUTier2,
            Constants.ItemName.GraphicsCardTier2,
            Constants.ItemName.RAMTier4,
            Constants.ItemName.RAMTier4,
            Constants.ItemName.HDDTier2)
          case 3 => Seq(
            Constants.ItemName.CPUTier3,
            Constants.ItemName.GraphicsCardTier3,
            Constants.ItemName.RAMTier6,
            Constants.ItemName.RAMTier6,
            Constants.ItemName.HDDTier3)
          case 4 => Seq(
            Constants.ItemName.CPUTier4,
            Constants.ItemName.GraphicsCardTier4,
            Constants.ItemName.RAMTier8,
            Constants.ItemName.RAMTier8,
            Constants.ItemName.SSDTier3)
        }
        val components = (componentNames.map(name =>
          Option(api.Items.get(name)).map(_.createItemStack(1))) ++ Seq(
          Option(Loot.defaultEEPROM).filter(stack => !stack.isEmpty),
          Option(Loot.defaultOpenOS).filter(stack => !stack.isEmpty)
        )).flatten

        if (components.size != componentNames.size + 2 || components.exists(_.isEmpty)) {
          source.sendFailure(Component.literal("OpenComputers default EEPROM/OpenOS data is not loaded; reload the server resources and try again."))
          return 0
        }

        val apuStack = components.head

        def rotateProperly(pos: net.minecraft.core.BlockPos): Option[Rotatable] =
          level.getBlockEntity(pos) match {
            case rotatable: Rotatable =>
              rotatable.setFromEntityPitchAndYaw(player)
              if (!rotatable.validFacings.contains(rotatable.pitch)) {
                rotatable.pitch = rotatable.validFacings.headOption.getOrElse(Direction.NORTH)
              }
              rotatable.invertRotation()
              Some(rotatable)
            case _ => None
          }

        level.setBlockAndUpdate(casePos, OCBlocks.CaseCreative.get().defaultBlockState())
        rotateProperly(casePos)

        val screen = screenTier match {
          case 1 => OCBlocks.ScreenTier1
          case 2 => OCBlocks.ScreenTier2
          case 3 => OCBlocks.ScreenTier3
          case 4 => OCBlocks.ScreenTier4
        }
        level.setBlockAndUpdate(screenPos, screen.get().defaultBlockState())
        rotateProperly(screenPos).foreach { rotatable =>
          if (rotatable.pitch == Direction.UP || rotatable.pitch == Direction.DOWN) {
            rotatable.pitch = Direction.NORTH
          }
        }

        level.setBlockAndUpdate(keyboardPos, OCBlocks.Keyboard.get().defaultBlockState())
        level.getBlockEntity(keyboardPos) match {
          case rotatable: Rotatable =>
            rotatable.setFromEntityPitchAndYaw(player)
            rotatable.setFromFacing(Direction.UP)
          case _ =>
        }

        api.Network.joinOrCreateNetwork(level.getBlockEntity(casePos))

        LuaStateFactory.setDefaultArch(apuStack)
        level.getBlockEntity(casePos) match {
          case computer: CaseBlockEntity =>
            for (component <- components) {
              val slot = (0 until computer.getContainerSize)
                .find(i => computer.getItem(i).isEmpty && computer.canPlaceItem(i, component))
                .getOrElse(throw new IllegalStateException(s"No compatible case slot for ${component.getHoverName.getString}"))
              computer.setItem(slot, component)
            }
            computer.setChanged()
          case _ =>
            throw new IllegalStateException("Creative case block entity was not created.")
        }

        source.sendSuccess(() => Component.literal("Spawned a configured OpenComputers computer."), true)
        1
      case _ =>
        source.sendFailure(Component.literal("You need to be looking at a nearby block."))
        0
    }
  }
}
