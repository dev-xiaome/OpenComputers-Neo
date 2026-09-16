package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedBlockEntity
import li.cil.oc.integration.ManagedBlockEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.{Item, ItemStack, JukeboxPlayable, JukeboxSong}
import net.minecraft.core.{BlockPos, Direction, Holder}
import net.minecraft.core.component.DataComponents
import net.minecraft.locale.Language
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.JukeboxBlockEntity
import net.neoforged.neoforge.server.ServerLifecycleHooks
import org.jspecify.annotations.Nullable

import java.util.function.{BiConsumer, BiFunction}

object DriverRecordPlayer extends DriverSidedBlockEntity {
  override def getBlockEntityClass: Class[_] = classOf[JukeboxBlockEntity]

  override def createEnvironment(world: Level, pos: BlockPos, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(pos).asInstanceOf[JukeboxBlockEntity])

  final class Environment(tileEntity: JukeboxBlockEntity) extends ManagedBlockEntityEnvironment[JukeboxBlockEntity](tileEntity, "jukebox") with NamedBlock {
    override def preferredName = "jukebox"

    override def priority = 0

    @Nullable
    private def getRecordComponent[T](fn: BiFunction[Holder[JukeboxSong], JukeboxPlayable, T]): Option[T] = {
      val stack = tileEntity.getTheItem
      stack.get(DataComponents.JUKEBOX_PLAYABLE) match {
        case playable: JukeboxPlayable => {
          val song = playable.song().unwrap(ServerLifecycleHooks.getCurrentServer.registryAccess())

          if (song.isPresent) {
            return Some(fn.apply(song.get(), playable))
          }
        }
      }

      null
    }

    @Callback(doc = "function():string -- Get the title of the record currently in the jukebox.")
    def getRecord(context: Context, args: Arguments): Array[AnyRef] = {
      getRecordComponent((song, _) => {
        result(song.value().description().getString)
      }).orNull
    }

    @Callback(doc = "function() -- Start playing the record currently in the jukebox.")
    def play(context: Context, args: Arguments): Array[AnyRef] = {
      getRecordComponent((song, _) => {
        tileEntity.getSongPlayer().play(tileEntity.getLevel, song)
        result(true)
      }).orNull
    }

    @Callback(doc = "function() -- Stop playing the record currently in the jukebox.")
    def stop(context: Context, args: Arguments): Array[AnyRef] = {
      tileEntity.getSongPlayer.stop(tileEntity.getLevel, tileEntity.getBlockState)
      null
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack.getItem == Blocks.JUKEBOX.asItem)
        classOf[Environment]
      else null
    }
  }

}
