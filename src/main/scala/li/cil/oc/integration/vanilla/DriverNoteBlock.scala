package li.cil.oc.integration.vanilla

import li.cil.oc.api
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock, SidedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.{ManagedEnvironment, Visibility}
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.{Block, Blocks, NoteBlock}

/**
 * 音符盒（`NoteBlock`）驱动。
 *
 * 1.21.1 迁移要点：
 *  - `TileEntityNote` 已整体删除：音符盒的「音高」改由方块状态属性
 *    `NoteBlock.NOTE`（0..24）承载，方块本身也没有方块实体了，
 *    因此这里不再继承 `DriverSidedTileEntity`，而是直接实现 [[SidedBlock]] 并把
 *    `Level` + `BlockPos` 交给环境。
 *  - `TileEntityNote#triggerNote(world, x, y, z)` → `Level#blockEvent(pos, block, 0, 0)`
 *    （`NoteBlock#triggerEvent` 会真正发出音效）。
 *  - `tileEntity.markDirty()` → `Level#setBlock`（属性写入本身就会标记区块保存）。
 *
 * TODO(port): 1.7.10 用 `world.getBlock(x, y + 1, z).getMaterial eq Material.air` 判断
 * 「上方是空气、因此可以发声」；1.21.1 的 `NoteBlock#playNote` 还会额外考虑
 * 乐器类型（`INSTRUMENT`），这里退化为只检查上方是否为空气。
 */
object DriverNoteBlock extends SidedBlock {
  override def worksWith(world: Level, x: Int, y: Int, z: Int, side: Direction): Boolean =
    world != null && world.getBlockState(new BlockPos(x, y, z)).getBlock.isInstanceOf[NoteBlock]

  override def createEnvironment(world: Level, x: Int, y: Int, z: Int, side: Direction): ManagedEnvironment =
    if (worksWith(world, x, y, z, side)) new Environment(world, new BlockPos(x, y, z)) else null

  final class Environment(val level: Level, val position: BlockPos) extends li.cil.oc.api.prefab.ManagedEnvironment with NamedBlock {
    // 1.7.10 里 `Network.newNode` 来自 `li.cil.oc.api.Network`（工厂类），
    // 这里必须写全限定名 —— `li.cil.oc.api.network.Network`（接口）会遮蔽它。
    setNode(api.Network.newNode(this, Visibility.Network).withComponent("note_block").create())

    override def preferredName = "note_block"

    override def priority = 0

    @Callback(direct = true, doc = "function():number -- Get the currently set pitch on this note block.")
    def getPitch(context: Context, args: Arguments): Array[AnyRef] = {
      result(note + 1)
    }

    @Callback(doc = "function(value:number) -- Set the pitch for this note block. Must be in the interval [1, 25].")
    def setPitch(context: Context, args: Arguments): Array[AnyRef] = {
      setPitch(args.checkInteger(0))
      result(true)
    }

    @Callback(doc = "function([pitch:number]):boolean -- Triggers the note block if possible. Allows setting the pitch for to save a tick.")
    def trigger(context: Context, args: Arguments): Array[AnyRef] = {
      if (args.count > 0 && args.checkAny(0) != null) {
        setPitch(args.checkInteger(0))
      }
      val state = blockState
      val canTrigger = level.getBlockState(position.above()).isAir
      level.blockEvent(position, state.getBlock, 0, 0)
      result(canTrigger)
    }

    private def blockState = level.getBlockState(position)

    private def note: Int = {
      val state = blockState
      if (state.getBlock.isInstanceOf[NoteBlock]) state.getValue(NoteBlock.NOTE).intValue else 0
    }

    private def setPitch(value: Int): Unit = {
      if (value < 1 || value > 25) {
        throw new IllegalArgumentException("invalid pitch")
      }
      val state = blockState
      if (!state.getBlock.isInstanceOf[NoteBlock]) {
        throw new IllegalArgumentException("no longer a note block")
      }
      level.setBlock(position, state.setValue(NoteBlock.NOTE, Int.box(value - 1)), Block.UPDATE_ALL)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] = {
      if (stack != null && !stack.isEmpty && Block.byItem(stack.getItem) == Blocks.NOTE_BLOCK)
        classOf[Environment]
      else null
    }
  }

}
