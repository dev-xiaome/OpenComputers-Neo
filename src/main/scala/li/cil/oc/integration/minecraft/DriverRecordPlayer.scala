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
import net.minecraft.core.Holder
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.{ItemStack, JukeboxSong}
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.JukeboxBlockEntity

object DriverRecordPlayer extends DriverSidedBlockEntity {
  override def getBlockEntityClass: Class[_] = classOf[JukeboxBlockEntity]

  override def createEnvironment(world: Level, pos: BlockPos, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(pos).asInstanceOf[JukeboxBlockEntity])

  final class Environment(tileEntity: JukeboxBlockEntity) extends ManagedBlockEntityEnvironment[JukeboxBlockEntity](tileEntity, "jukebox") with NamedBlock {
    override def preferredName = "jukebox"

    override def priority = 0

    @Callback(doc = "function():string -- Get the title of the record currently in the jukebox.")
    def getRecord(context: Context, args: Arguments): Array[AnyRef] = recordSong match {
      case Some(song) => result(song.value().description().getString)
      case _ => null
    }

    @Callback(doc = "function() -- Start playing the record currently in the jukebox.")
    def play(context: Context, args: Arguments): Array[AnyRef] = recordSong match {
      case Some(song) =>
        // 1.21.1：唱片不再是 RecordItem，播放改由 JukeboxSongPlayer 负责，
        // 它会写入歌曲状态并派发 1010 音效事件。
        tileEntity.getSongPlayer.play(tileEntity.getLevel, song)
        result(true)
      case _ => null
    }

    @Callback(doc = "function() -- Stop playing the record currently in the jukebox.")
    def stop(context: Context, args: Arguments): Array[AnyRef] = {
      tileEntity.getSongPlayer.stop(tileEntity.getLevel, tileEntity.getBlockState)
      null
    }

    // 1.21.1 的唱片已改为携带 JukeboxPlayable 组件的普通物品，
    // 曲目信息需要用 JukeboxSong.fromStack 从动态注册表 JukeboxSong 中解析。
    private def recordSong: Option[Holder[JukeboxSong]] = {
      val level = tileEntity.getLevel
      val record = tileEntity.getTheItem
      if (level == null || record.isEmpty) None
      else {
        val song = JukeboxSong.fromStack(level.registryAccess(), record)
        if (song.isPresent) Some(song.get) else None
      }
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
