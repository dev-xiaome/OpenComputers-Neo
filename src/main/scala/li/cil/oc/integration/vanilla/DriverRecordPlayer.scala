package li.cil.oc.integration.vanilla

import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction, Holder}
import net.minecraft.world.item.{ItemStack, JukeboxSong}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{Block, Blocks}
import net.minecraft.world.level.block.entity.JukeboxBlockEntity

/**
 * 唱片机（`JukeboxBlockEntity`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `BlockJukebox.TileEntityJukebox` → `JukeboxBlockEntity`
 *  - `func_145856_a()`（取出唱片）→ `getTheItem`
 *  - `ItemRecord` 已删除，唱片内容改为数据驱动的 `JukeboxSong`：
 *    `JukeboxSong.fromStack(registries, stack)` 得到歌曲，标题取 `description`
 *  - 播放 / 停止：`world.playAuxSFX(1005, ...)` / `playRecord(...)` →
 *    `JukeboxSongPlayer#play(level, holder)` / `#stop(level, state)`
 *
 * TODO(port): 1.7.10 的 `play` 用底噪辅助事件（aux SFX）单独触发播放；
 * 1.21.1 走 `JukeboxSongPlayer`，它同时会更新「正在播放」状态并通知邻居，
 * 语义上更接近「装片即播」，因此 `play` 在唱片已装入时可能只是重新开始播放。
 */
object DriverRecordPlayer extends DriverSidedTileEntity {
  override def getTileEntityClass: Class[_] = classOf[JukeboxBlockEntity]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    new Environment(world.getBlockEntity(new BlockPos(x, y, z)).asInstanceOf[JukeboxBlockEntity])

  final class Environment(entity: JukeboxBlockEntity) extends ManagedTileEntityEnvironment[JukeboxBlockEntity](entity, "jukebox") with NamedBlock {
    override def preferredName = "jukebox"

    override def priority = 0

    @Callback(doc = "function():string -- Get the title of the record currently in the jukebox.")
    def getRecord(context: Context, args: Arguments): Array[AnyRef] = {
      val record = tileEntity.getTheItem
      if (record == null || record.isEmpty) null
      else {
        val song = songOf(record)
        if (song == null) result(record.getHoverName.getString)
        else result(song.value().description.getString)
      }
    }

    @Callback(doc = "function() -- Start playing the record currently in the jukebox.")
    def play(context: Context, args: Arguments): Array[AnyRef] = {
      val song = songOf(tileEntity.getTheItem)
      if (song == null) null
      else {
        tileEntity.getSongPlayer.play(level, song)
        result(true)
      }
    }

    @Callback(doc = "function() -- Stop playing the record currently in the jukebox.")
    def stop(context: Context, args: Arguments): Array[AnyRef] = {
      tileEntity.getSongPlayer.stop(level, tileEntity.getBlockState)
      null
    }

    private def level: Level = tileEntity.getLevel

    private def songOf(stack: ItemStack): Holder[JukeboxSong] = {
      val currentLevel = level
      if (stack == null || stack.isEmpty || currentLevel == null) null
      else JukeboxSong.fromStack(currentLevel.registryAccess(), stack).orElse(null)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty && Block.byItem(stack.getItem) == Blocks.JUKEBOX)
        classOf[Environment]
      else null
    }
  }

}
